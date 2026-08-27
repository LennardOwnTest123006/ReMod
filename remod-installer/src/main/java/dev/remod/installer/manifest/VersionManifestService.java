package dev.remod.installer.manifest;

import dev.remod.common.io.Platform;
import dev.remod.common.log.ReModLog;
import dev.remod.common.log.ReModLogger;
import dev.remod.common.net.DownloadException;
import dev.remod.common.net.HttpFetcher;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Supplies the Minecraft version manifest, with caching.
 *
 * <p>The manifest is fetched at most once per session and cached on disk under
 * ReMod's home directory with its ETag. Subsequent sessions revalidate rather
 * than re-download, and an offline session serves the cached copy regardless of
 * age. This is what keeps ReMod from making a network request every time the
 * installer window opens.</p>
 */
public final class VersionManifestService {

    private static final ReModLogger LOG = ReModLog.get("ReMod/Manifest");

    /** How long a cached manifest is served without revalidating. */
    private static final Duration FRESHNESS = Duration.ofHours(6);

    private static final String CACHE_KEY = "minecraft-version-manifest-v2";

    private final HttpFetcher fetcher;
    private volatile MinecraftVersionManifest cached;

    public VersionManifestService(HttpFetcher fetcher) {
        this.fetcher = fetcher;
    }

    /** Uses ReMod's standard cache directory. */
    public static VersionManifestService standard() {
        return new VersionManifestService(
                new HttpFetcher(Platform.remodHome().resolve("cache")));
    }

    /** Uses an explicit cache directory. Used by tests. */
    public static VersionManifestService withCacheDirectory(Path cacheDirectory) {
        return new VersionManifestService(new HttpFetcher(cacheDirectory));
    }

    public void setOffline(boolean offline) {
        fetcher.setOffline(offline);
    }

    public boolean isOffline() {
        return fetcher.isOffline();
    }

    /**
     * Returns the manifest, using the in-memory copy when one exists.
     *
     * @throws ManifestException when it cannot be obtained or parsed
     */
    public MinecraftVersionManifest get() {
        MinecraftVersionManifest local = cached;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cached != null) {
                return cached;
            }
            cached = load();
            return cached;
        }
    }

    /** Discards the in-memory copy so the next {@link #get()} revalidates. */
    public synchronized void refresh() {
        cached = null;
    }

    private MinecraftVersionManifest load() {
        try {
            String json = fetcher.fetchText(MinecraftVersionManifest.MANIFEST_URL,
                    CACHE_KEY, FRESHNESS);
            MinecraftVersionManifest manifest = MinecraftVersionManifest.parse(json);
            LOG.info("Minecraft version manifest loaded: " + manifest.size() + " versions"
                    + manifest.latestRelease().map(r -> ", latest release " + r).orElse(""));
            return manifest;
        } catch (DownloadException e) {
            throw new ManifestException(
                    "Could not download the Minecraft version list. " + e.getMessage(),
                    e.suggestion(), e);
        }
    }

    /** The cache directory the manifest is stored in. */
    public Path cacheDirectory() {
        return fetcher.cacheDirectory();
    }
}
