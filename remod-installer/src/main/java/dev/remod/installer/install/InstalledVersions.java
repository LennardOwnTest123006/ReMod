package dev.remod.installer.install;

import dev.remod.common.json.Json;
import dev.remod.common.json.JsonException;
import dev.remod.common.json.JsonObject;
import dev.remod.common.log.ReModLog;
import dev.remod.common.log.ReModLogger;
import dev.remod.loader.ReModPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Finds the ReMod installations already present in a Minecraft directory.
 *
 * <p>Identification is by content, not by name: a directory only counts as a
 * ReMod installation if its version JSON carries ReMod's own marker block. That
 * is what stops the uninstaller from ever touching a vanilla or third-party
 * version directory that happens to be named similarly.</p>
 */
public final class InstalledVersions {

    private static final ReModLogger LOG = ReModLog.get("ReMod/Install");

    private InstalledVersions() {
    }

    /** Every ReMod installation found, newest version directory first. */
    public static List<Installed> scan(ReModPaths paths) {
        List<Installed> found = new ArrayList<>();
        Path versions = paths.versionsDirectory();
        if (!Files.isDirectory(versions)) {
            return found;
        }
        List<Path> directories = new ArrayList<>();
        try (java.util.stream.Stream<Path> stream = Files.list(versions)) {
            stream.filter(Files::isDirectory).sorted().forEach(directories::add);
        } catch (IOException e) {
            LOG.warn("Could not list " + versions + ": " + e.getMessage());
            return found;
        }
        for (Path directory : directories) {
            String id = directory.getFileName().toString();
            Path json = directory.resolve(id + ".json");
            if (!Files.isRegularFile(json)) {
                continue;
            }
            try {
                JsonObject root = Json.parseObject(Files.readString(json, StandardCharsets.UTF_8));
                if (!VersionJsonGenerator.isReModVersion(root)) {
                    continue;
                }
                found.add(new Installed(
                        id,
                        VersionJsonGenerator.minecraftVersionOf(root),
                        root.optObject("remod").optString("loaderVersion", "unknown"),
                        root.optObject("remod").optString("apiVersion", null),
                        directory));
            } catch (IOException | JsonException e) {
                LOG.debug(() -> "Skipping unreadable version JSON " + json + ": " + e.getMessage());
            }
        }
        Collections.reverse(found);
        return found;
    }

    /** True when ReMod is installed for a Minecraft version. */
    public static boolean isInstalled(ReModPaths paths, String minecraftVersion) {
        for (Installed installed : scan(paths)) {
            if (minecraftVersion.equals(installed.minecraftVersion())) {
                return true;
            }
        }
        return false;
    }

    /** One ReMod installation. */
    public static final class Installed {

        private final String versionId;
        private final String minecraftVersion;
        private final String loaderVersion;
        private final String apiVersion;
        private final Path directory;

        public Installed(String versionId, String minecraftVersion, String loaderVersion,
                         String apiVersion, Path directory) {
            this.versionId = versionId;
            this.minecraftVersion = minecraftVersion;
            this.loaderVersion = loaderVersion;
            this.apiVersion = apiVersion;
            this.directory = directory;
        }

        /** The launcher version id, e.g. {@code ReMod-1.21.4}. */
        public String versionId() {
            return versionId;
        }

        public String minecraftVersion() {
            return minecraftVersion;
        }

        public String loaderVersion() {
            return loaderVersion;
        }

        /** The ReMod API version, or {@code null} on an older installation. */
        public String apiVersion() {
            return apiVersion;
        }

        public Path directory() {
            return directory;
        }

        @Override
        public String toString() {
            return versionId + " (ReMod " + loaderVersion + " for Minecraft "
                    + minecraftVersion + ")";
        }
    }
}
