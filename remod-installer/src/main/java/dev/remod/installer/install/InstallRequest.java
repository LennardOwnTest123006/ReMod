package dev.remod.installer.install;

import java.nio.file.Path;
import java.util.Objects;

/** What the user asked the installer to do. */
public final class InstallRequest {

    private final String minecraftVersion;
    private final Path minecraftDirectory;
    private final Path gameDirectoryOverride;
    private final boolean createLauncherProfile;

    private InstallRequest(Builder builder) {
        this.minecraftVersion = builder.minecraftVersion;
        this.minecraftDirectory = builder.minecraftDirectory;
        this.gameDirectoryOverride = builder.gameDirectoryOverride;
        this.createLauncherProfile = builder.createLauncherProfile;
    }

    public static Builder builder(String minecraftVersion, Path minecraftDirectory) {
        return new Builder(minecraftVersion, minecraftDirectory);
    }

    public String minecraftVersion() {
        return minecraftVersion;
    }

    /** The {@code .minecraft} directory to install into. */
    public Path minecraftDirectory() {
        return minecraftDirectory;
    }

    /**
     * A separate game directory for this installation, or {@code null} to share
     * the default one. Useful for keeping a modded world apart from vanilla.
     */
    public Path gameDirectoryOverride() {
        return gameDirectoryOverride;
    }

    /** Whether to add an entry to the launcher's installation list. */
    public boolean createLauncherProfile() {
        return createLauncherProfile;
    }

    /** Fluent builder for {@link InstallRequest}. */
    public static final class Builder {

        private final String minecraftVersion;
        private final Path minecraftDirectory;
        private Path gameDirectoryOverride;
        private boolean createLauncherProfile = true;

        private Builder(String minecraftVersion, Path minecraftDirectory) {
            this.minecraftVersion = Objects.requireNonNull(minecraftVersion, "minecraftVersion");
            this.minecraftDirectory = Objects.requireNonNull(minecraftDirectory,
                    "minecraftDirectory");
        }

        public Builder gameDirectory(Path value) {
            this.gameDirectoryOverride = value;
            return this;
        }

        public Builder createLauncherProfile(boolean value) {
            this.createLauncherProfile = value;
            return this;
        }

        public InstallRequest build() {
            return new InstallRequest(this);
        }
    }
}
