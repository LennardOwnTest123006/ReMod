package dev.remod.installer.install;

import dev.remod.common.io.IOUtil;
import dev.remod.common.json.Json;
import dev.remod.common.json.JsonException;
import dev.remod.common.json.JsonObject;
import dev.remod.common.log.ReModLog;
import dev.remod.common.log.ReModLogger;
import dev.remod.common.net.DownloadException;
import dev.remod.common.net.HttpFetcher;
import dev.remod.common.net.ProgressListener;
import dev.remod.installer.manifest.MinecraftVersionEntry;
import dev.remod.loader.ReModPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Downloads the vanilla Minecraft files a ReMod installation inherits from.
 *
 * <p>ReMod's generated version JSON uses {@code inheritsFrom}, so the official
 * launcher will fetch anything missing on first launch anyway. Doing it here
 * instead means the install finishes in a state where pressing Play starts the
 * game immediately, rather than sitting on a progress bar -- which is what
 * people expect an installer to have done.</p>
 *
 * <p><b>What is downloaded, and from where.</b> Only the two files ReMod's
 * profile depends on: the version's own JSON and its client jar, both from the
 * URLs and with the SHA-1 checksums that Mojang's official version manifest
 * publishes. They land in {@code versions/&lt;id&gt;/}, exactly where the
 * launcher puts them and where it will find them.</p>
 *
 * <p><b>What is not.</b> Libraries and assets. There are hundreds of them, they
 * are per-platform and rule-filtered, and the launcher already resolves and
 * downloads them from the same version JSON. Duplicating that would be a large
 * amount of code to arrive at the same files the launcher fetches anyway.</p>
 *
 * <p>Nothing is redistributed: these are the user's own downloads, from
 * Mojang's own servers, to their own machine, over the same public endpoints
 * the official launcher uses.</p>
 */
public final class MinecraftDownloader {

    private static final ReModLogger LOG = ReModLog.get("ReMod/Download");

    /** A version's own JSON never changes once published, so cache it for a long time. */
    private static final Duration VERSION_JSON_FRESHNESS = Duration.ofDays(30);

    private final HttpFetcher fetcher;

    public MinecraftDownloader(HttpFetcher fetcher) {
        this.fetcher = fetcher;
    }

    /** What a download did, for the install summary. */
    public static final class Result {

        private final boolean versionJsonWritten;
        private final boolean clientJarWritten;
        private final boolean alreadyPresent;
        private final long clientJarBytes;

        Result(boolean versionJsonWritten, boolean clientJarWritten, boolean alreadyPresent,
               long clientJarBytes) {
            this.versionJsonWritten = versionJsonWritten;
            this.clientJarWritten = clientJarWritten;
            this.alreadyPresent = alreadyPresent;
            this.clientJarBytes = clientJarBytes;
        }

        public boolean versionJsonWritten() {
            return versionJsonWritten;
        }

        public boolean clientJarWritten() {
            return clientJarWritten;
        }

        /** True when everything was already on disk and nothing was fetched. */
        public boolean wasAlreadyPresent() {
            return alreadyPresent;
        }

        public long clientJarBytes() {
            return clientJarBytes;
        }

        public String summary() {
            if (alreadyPresent) {
                return "already downloaded";
            }
            return "downloaded " + IOUtil.humanBytes(clientJarBytes);
        }
    }

    /**
     * Ensures {@code entry}'s version JSON and client jar are present.
     *
     * @param entry the version, as listed in Mojang's manifest
     * @throws DownloadException when a file cannot be fetched or fails its checksum
     */
    public Result download(ReModPaths paths, MinecraftVersionEntry entry,
                           ProgressListener progress) throws DownloadException {
        ProgressListener listener = progress == null ? ProgressListener.NONE : progress;
        String id = entry.id();
        Path directory = paths.versionsDirectory().resolve(id);
        Path versionJson = directory.resolve(id + ".json");
        Path clientJar = directory.resolve(id + ".jar");

        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new DownloadException("Could not create " + directory + ": " + e.getMessage(),
                    "Check the folder is writable and try again.", e);
        }

        boolean hadJson = Files.isRegularFile(versionJson);
        boolean hadJar = Files.isRegularFile(clientJar);

        String json = obtainVersionJson(entry, versionJson, listener);
        JsonObject client = clientDownload(json, id);
        String url = client.optString("url", null);
        String sha1 = client.optString("sha1", null);
        long size = client.optLong("size", -1);

        if (url == null) {
            throw new DownloadException(
                    "Minecraft " + id + "'s version JSON lists no client download.",
                    "This version may not be playable on its own. Choose a different"
                            + " Minecraft version.");
        }

        listener.step("Downloading Minecraft " + id + " client"
                + (size > 0 ? " (" + IOUtil.humanBytes(size) + ")" : ""));
        fetcher.downloadFile(url, clientJar, sha1, listener);

        downloadMappings(paths, id, json, listener);

        boolean nothingFetched = hadJson && hadJar;
        LOG.info("Minecraft " + id + (nothingFetched
                ? " was already downloaded"
                : " downloaded to " + directory));
        return new Result(!hadJson, !hadJar, nothingFetched, sizeOf(clientJar));
    }

    /**
     * Downloads Mojang's official mappings for this version.
     *
     * <p>This is what lets ReMod reach the game's own fields on an obfuscated
     * install: without it, a class like {@code Abilities} is called something
     * unguessable and features that need it are unavailable. Mojang publishes
     * the file per version and names it in the version JSON, so ReMod is using
     * the documented mechanism rather than shipping anything of Mojang's.</p>
     *
     * <p>A failure here is a warning, not an error. The game still runs and
     * mods still load; only the features that need the game's internals are
     * lost, and they report that themselves.</p>
     */
    private void downloadMappings(ReModPaths paths, String id, String versionJson,
                                  ProgressListener listener) {
        JsonObject mappings;
        try {
            mappings = Json.parseObject(versionJson).optObject("downloads")
                    .optObject("client_mappings");
        } catch (JsonException e) {
            return;
        }
        String url = mappings.optString("url", null);
        if (url == null) {
            LOG.warn("Minecraft " + id + " publishes no official mappings, so ReMod cannot"
                    + " reach the game's own fields on this version.");
            return;
        }
        Path target = paths.remodDirectory().resolve("mappings").resolve(id + ".txt");
        try {
            listener.step("Downloading Mojang mappings for " + id);
            fetcher.downloadFile(url, target, mappings.optString("sha1", null), listener);
            LOG.info("Mojang mappings for " + id + " installed to " + target);
        } catch (DownloadException e) {
            LOG.warn("Could not download the Mojang mappings for " + id + " (" + e.getMessage()
                    + "). Mods will load, but features that reach into the game will not"
                    + " work until they are available.");
        }
    }

    /**
     * Fetches the version's own JSON, writing it where the launcher expects it.
     *
     * <p>The manifest publishes a SHA-1 for this file, so a corrupted or
     * intercepted copy is caught rather than written.</p>
     */
    private String obtainVersionJson(MinecraftVersionEntry entry, Path target,
                                     ProgressListener listener) throws DownloadException {
        if (entry.url() == null || entry.url().isEmpty()) {
            throw new DownloadException(
                    "Minecraft " + entry.id() + " has no version JSON URL in the manifest.",
                    "Choose a different Minecraft version.");
        }
        listener.step("Fetching the Minecraft " + entry.id() + " version file");
        String json = fetcher.fetchText(entry.url(), "mc-version-" + entry.id(),
                VERSION_JSON_FRESHNESS);
        try {
            // Written verbatim: the launcher verifies this file against the
            // same checksum, so reformatting it would break that check.
            IOUtil.writeAtomically(target, json);
        } catch (IOException e) {
            throw new DownloadException("Could not write " + target + ": " + e.getMessage(),
                    "Check there is free disk space and that the launcher is closed.", e);
        }
        return json;
    }

    private static JsonObject clientDownload(String json, String id) throws DownloadException {
        try {
            return Json.parseObject(json).optObject("downloads").optObject("client");
        } catch (JsonException e) {
            throw new DownloadException(
                    "Minecraft " + id + "'s version file could not be read: " + e.getMessage(),
                    "The download may have been corrupted or intercepted by a proxy."
                            + " Try again.", e);
        }
    }

    private static long sizeOf(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.size(file) : 0;
        } catch (IOException e) {
            return 0;
        }
    }

    /** True when this version's files are already on disk. */
    public static boolean isDownloaded(ReModPaths paths, String versionId) {
        Path directory = paths.versionsDirectory().resolve(versionId);
        return Files.isRegularFile(directory.resolve(versionId + ".json"))
                && Files.isRegularFile(directory.resolve(versionId + ".jar"));
    }
}
