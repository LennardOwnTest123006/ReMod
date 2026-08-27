package dev.remod.installer.install;

import java.nio.file.Path;
import java.util.Objects;

/** What the user asked the installer to do. */
public final class InstallRequest {

    private final String minecraftVersion;
    private final Path minecraftDirectory;
    private final Path gameDirectoryOverride;
    private final boolean createLauncherProfile;
    private final boolean downloadMinecraft;
    private final dev.remod.installer.manifest.MinecraftVersionEntry manifestEntry;

    private InstallRequest(Builder builder) {
        this.minecraftVersion = builder.minecraftVersion;
        this.minecraftDirectory = builder.minecraftDirectory;
        this.gameDirectoryOverride = builder.gameDirectoryOverride;
        this.createLauncherProfile = builder.createLauncherProfile;
        this.downloadMinecraft = builder.downloadMinecraft;
        this.manifestEntry = builder.manifestEntry;
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

    /**
     * Whether to download the vanilla Minecraft files now.
     *
     * <p>Requires {@link #manifestEntry()}. When false, or when no entry is
     * supplied, the official launcher downloads them on first launch instead --
     * ReMod's profile inherits from the vanilla version either way.</p>
     */
    public boolean downloadMinecraft() {
        return downloadMinecraft && manifestEntry != null;
    }

    /** The manifest entry for this version, or {@code null} when unavailable. */
    public dev.remod.installer.manifest.MinecraftVersionEntry manifestEntry() {
        return manifestEntry;
    }

    /** Fluent builder for {@link InstallRequest}. */
    public static final class Builder {

        private final String minecraftVersion;
        private final Path minecraftDirectory;
        private Path gameDirectoryOverride;
        private boolean createLauncherProfile = true;
        private boolean downloadMinecraft = true;
        private dev.remod.installer.manifest.MinecraftVersionEntry manifestEntry;

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

        /** Download the vanilla Minecraft files during the install. Default true. */
        public Builder downloadMinecraft(boolean value) {
            this.downloadMinecraft = value;
            return this;
        }

        /** The manifest entry, which carries the download URLs and checksums. */
        public Builder manifestEntry(dev.remod.installer.manifest.MinecraftVersionEntry value) {
            this.manifestEntry = value;
            return this;
        }

        public InstallRequest build() {
            return new InstallRequest(this);
        }
    }
}
