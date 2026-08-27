package dev.remod.compat.bridge;

import dev.remod.compat.LoaderBridge;
import dev.remod.compat.LoaderPlatform;
import dev.remod.loader.ReModPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Shared detection for the client/server mod loaders.
 *
 * <p>All four -- Fabric, Quilt, Forge and NeoForge -- install themselves the
 * same way: a directory under {@code versions/} whose name contains the loader
 * name, plus a mods folder. Detecting them therefore comes down to one shared
 * scan with a different name to match on.</p>
 */
abstract class AbstractModLoaderBridge implements LoaderBridge {

    private final LoaderPlatform platform;
    private final String[] versionNameMarkers;
    private final String modsFolderName;

    AbstractModLoaderBridge(LoaderPlatform platform, String modsFolderName,
                            String... versionNameMarkers) {
        this.platform = platform;
        this.modsFolderName = modsFolderName;
        this.versionNameMarkers = versionNameMarkers;
    }

    @Override
    public LoaderPlatform platform() {
        return platform;
    }

    @Override
    public Optional<Detection> detect(ReModPaths paths) {
        List<String> profiles = new ArrayList<>();
        Path versions = paths.versionsDirectory();
        if (Files.isDirectory(versions)) {
            try (java.util.stream.Stream<Path> stream = Files.list(versions)) {
                stream.filter(Files::isDirectory).sorted().forEach(directory -> {
                    String name = directory.getFileName().toString().toLowerCase(Locale.ROOT);
                    for (String marker : versionNameMarkers) {
                        if (name.contains(marker)) {
                            profiles.add(directory.getFileName().toString());
                            break;
                        }
                    }
                });
            } catch (IOException e) {
                // A missing or unreadable versions folder simply means "not found".
                return Optional.empty();
            }
        }
        Path modsFolder = paths.gameDirectory().resolve(modsFolderName);
        boolean hasModsFolder = Files.isDirectory(modsFolder);

        if (profiles.isEmpty() && !hasModsFolder) {
            return Optional.empty();
        }
        Path evidence = profiles.isEmpty() ? modsFolder : versions.resolve(profiles.get(0));
        return Optional.of(new Detection(platform, versionFrom(profiles), evidence, profiles));
    }

    /** Pulls a loader version out of a launcher profile name where one is present. */
    private String versionFrom(List<String> profiles) {
        for (String profile : profiles) {
            String[] parts = profile.split("-");
            for (int i = parts.length - 1; i >= 0; i--) {
                if (!parts[i].isEmpty() && Character.isDigit(parts[i].charAt(0))) {
                    return parts[i];
                }
            }
        }
        return null;
    }

    /** The folder this loader reads its mods from, relative to the game directory. */
    String modsFolderName() {
        return modsFolderName;
    }
}
