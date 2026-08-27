package dev.remod.compat;

import dev.remod.loader.ReModPaths;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * ReMod's interface to another mod loader or server platform.
 *
 * <p>The interface is intentionally shaped around what is honestly achievable
 * rather than around what would be nice. Each bridge answers four questions:</p>
 *
 * <ol>
 *   <li><b>Is it installed?</b> ({@link #detect}) -- so ReMod can warn about
 *       conflicting launcher profiles and point misplaced mods at the right
 *       folder.</li>
 *   <li><b>How far can we go?</b> ({@link #level()}) -- a single honest
 *       statement per platform.</li>
 *   <li><b>Can we coexist?</b> ({@link #coexistenceNotes()}) -- what a user has
 *       to know to run both.</li>
 *   <li><b>Can we load its mods?</b> ({@link #canLoadMod}) -- which today is
 *       {@code false} everywhere, for reasons each bridge states.</li>
 * </ol>
 *
 * <p>Adding a future compatibility module means implementing this interface and
 * registering it with {@link CompatibilityRegistry}; nothing in the loader or
 * the API changes.</p>
 */
public interface LoaderBridge {

    /** The platform this bridge speaks for. */
    LoaderPlatform platform();

    /** How far ReMod can interoperate with it. */
    CompatibilityLevel level();

    /**
     * Looks for an installation of this platform.
     *
     * @param paths the Minecraft installation to inspect
     * @return what was found, or empty when the platform is not installed
     */
    Optional<Detection> detect(ReModPaths paths);

    /**
     * What a user needs to know to run this platform alongside ReMod.
     * Each entry is one sentence, shown in the installer and the docs.
     */
    List<String> coexistenceNotes();

    /**
     * Whether ReMod could load a mod built for this platform.
     *
     * @return always {@code false} in ReMod 1.0.0; {@link #whyNotLoadable()}
     *         explains what would be needed
     */
    default boolean canLoadMod(Path modFile) {
        return false;
    }

    /** A precise explanation of what stands between ReMod and loading these mods. */
    String whyNotLoadable();

    /** What a detection found on disk. */
    final class Detection {

        private final LoaderPlatform platform;
        private final String version;
        private final Path evidence;
        private final List<String> launcherProfiles;

        public Detection(LoaderPlatform platform, String version, Path evidence,
                         List<String> launcherProfiles) {
            this.platform = platform;
            this.version = version;
            this.evidence = evidence;
            this.launcherProfiles = List.copyOf(launcherProfiles);
        }

        public LoaderPlatform platform() {
            return platform;
        }

        /** The detected version, or {@code null} when it could not be determined. */
        public String version() {
            return version;
        }

        /** The file or directory that proved the platform is installed. */
        public Path evidence() {
            return evidence;
        }

        /** Launcher version ids belonging to this platform. */
        public List<String> launcherProfiles() {
            return launcherProfiles;
        }

        @Override
        public String toString() {
            return platform.displayName() + (version == null ? "" : " " + version)
                    + " (" + evidence + ")";
        }
    }
}
