package dev.remod.loader;

import dev.remod.common.io.Platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Every directory ReMod uses, derived from one game directory.
 *
 * <p>ReMod keeps its own files under {@code <game>/remod/} rather than
 * scattering them through {@code .minecraft}. That single subtree is what makes
 * uninstalling clean and makes it impossible for ReMod to be confused with
 * another loader's directories.</p>
 *
 * <pre>
 * .minecraft/
 *   remod/
 *     mods/      &lt;- ReMod mods go here
 *     config/    &lt;- per-mod configuration
 *     data/      &lt;- per-mod private storage
 *     logs/      &lt;- ReMod's own logs
 *     api/       &lt;- installed ReMod API jars, one per Minecraft series
 * </pre>
 */
public final class ReModPaths {

    private final Path gameDirectory;

    public ReModPaths(Path gameDirectory) {
        this.gameDirectory = Objects.requireNonNull(gameDirectory, "gameDirectory")
                .toAbsolutePath().normalize();
    }

    /** Uses the default {@code .minecraft} location for this operating system. */
    public static ReModPaths defaultLocation() {
        return new ReModPaths(Platform.defaultMinecraftDirectory());
    }

    public Path gameDirectory() {
        return gameDirectory;
    }

    /** {@code <game>/remod} */
    public Path remodDirectory() {
        return gameDirectory.resolve("remod");
    }

    /** {@code <game>/remod/mods} -- where ReMod mods are installed. */
    public Path modsDirectory() {
        return remodDirectory().resolve("mods");
    }

    /** {@code <game>/remod/config} */
    public Path configDirectory() {
        return remodDirectory().resolve("config");
    }

    /** {@code <game>/remod/data} */
    public Path dataDirectory() {
        return remodDirectory().resolve("data");
    }

    /** {@code <game>/remod/logs} */
    public Path logsDirectory() {
        return remodDirectory().resolve("logs");
    }

    /** {@code <game>/remod/api} -- installed ReMod API jars. */
    public Path apiDirectory() {
        return remodDirectory().resolve("api");
    }

    /** This mod's private data directory. */
    public Path dataDirectoryFor(String modId) {
        return dataDirectory().resolve(modId);
    }

    /** This mod's configuration file. */
    public Path configFileFor(String modId) {
        return configDirectory().resolve(modId + ".json");
    }

    /** The Minecraft launcher's version directory. */
    public Path versionsDirectory() {
        return gameDirectory.resolve("versions");
    }

    /** The Minecraft launcher's shared library directory. */
    public Path librariesDirectory() {
        return gameDirectory.resolve("libraries");
    }

    /** The Minecraft launcher's profile file. */
    public Path launcherProfilesFile() {
        return gameDirectory.resolve("launcher_profiles.json");
    }

    /** Creates the ReMod subtree. Never touches anything outside {@code remod/}. */
    public ReModPaths createDirectories() throws IOException {
        Files.createDirectories(modsDirectory());
        Files.createDirectories(configDirectory());
        Files.createDirectories(dataDirectory());
        Files.createDirectories(logsDirectory());
        Files.createDirectories(apiDirectory());
        return this;
    }

    /** True when this directory looks like a real Minecraft installation. */
    public boolean looksLikeMinecraftDirectory() {
        return Files.isDirectory(versionsDirectory())
                || Files.isRegularFile(launcherProfilesFile())
                || Files.isDirectory(gameDirectory.resolve("assets"));
    }

    @Override
    public String toString() {
        return gameDirectory.toString();
    }
}
