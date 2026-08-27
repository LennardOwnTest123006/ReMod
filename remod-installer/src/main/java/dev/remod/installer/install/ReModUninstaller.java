package dev.remod.installer.install;

import dev.remod.common.io.IOUtil;
import dev.remod.common.log.ReModLog;
import dev.remod.common.log.ReModLogger;
import dev.remod.loader.ReModPaths;
import dev.remod.loader.ReModVersions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Removes a ReMod installation.
 *
 * <p>Deliberately conservative. Uninstalling removes exactly two things: the
 * {@code versions/ReMod-<version>/} directory ReMod created, and ReMod's own
 * launcher profile entry.</p>
 *
 * <p>It does <b>not</b> remove:</p>
 *
 * <ul>
 *   <li>{@code remod/mods/} -- the user's mods, which they will want if they
 *       reinstall;</li>
 *   <li>{@code remod/config/} or {@code remod/data/} -- mod settings and saved
 *       state;</li>
 *   <li>anything under {@code saves/} -- worlds are never touched;</li>
 *   <li>the shared ReMod libraries, which another installed version may still
 *       be using.</li>
 * </ul>
 *
 * <p>Those are listed in the result so the user can delete them themselves if
 * they want to, which is the right way round: deleting a world by accident is
 * unrecoverable, leaving a folder behind is not.</p>
 */
public final class ReModUninstaller {

    private static final ReModLogger LOG = ReModLog.get("ReMod/Install");

    private final ReModPaths paths;

    public ReModUninstaller(ReModPaths paths) {
        this.paths = paths;
    }

    /**
     * Removes the installation for one Minecraft version.
     *
     * @throws InstallException when the target is not a ReMod installation
     */
    public Result uninstall(String minecraftVersion) {
        String versionId = ReModVersions.launcherVersionId(minecraftVersion);
        Path directory = paths.versionsDirectory().resolve(versionId);
        List<String> removed = new ArrayList<>();
        List<String> kept = new ArrayList<>();

        if (Files.isDirectory(directory)) {
            // Verified by content, so a same-named directory that is not ours
            // is never deleted.
            boolean isOurs = InstalledVersions.scan(paths).stream()
                    .anyMatch(installed -> installed.versionId().equals(versionId));
            if (!isOurs) {
                throw new InstallException(
                        directory + " exists but was not created by ReMod.",
                        "ReMod will not delete a version directory it does not recognise."
                                + " Remove it yourself if you are sure.");
            }
            try {
                IOUtil.deleteRecursively(directory);
                removed.add(directory.toString());
                LOG.info("Removed " + directory);
            } catch (IOException e) {
                throw new InstallException(
                        "Could not remove " + directory + ": " + e.getMessage(),
                        "Close the Minecraft Launcher and try again.", e);
            }
        }

        if (new LauncherProfileWriter(paths).removeProfile(minecraftVersion)) {
            removed.add("launcher installation 'ReMod " + minecraftVersion + "'");
        }

        if (Files.isDirectory(paths.modsDirectory())) {
            kept.add("Your mods are still in " + paths.modsDirectory());
        }
        if (Files.isDirectory(paths.configDirectory())) {
            kept.add("Mod settings are still in " + paths.configDirectory());
        }
        if (!InstalledVersions.scan(paths).isEmpty()) {
            kept.add("ReMod libraries were kept because other ReMod installations still"
                    + " use them.");
        }
        if (removed.isEmpty()) {
            throw new InstallException(
                    "ReMod is not installed for Minecraft " + minecraftVersion + ".",
                    "Nothing was changed.");
        }
        return new Result(minecraftVersion, removed, kept);
    }

    /** What an uninstall removed and what it deliberately kept. */
    public static final class Result {

        private final String minecraftVersion;
        private final List<String> removed;
        private final List<String> kept;

        Result(String minecraftVersion, List<String> removed, List<String> kept) {
            this.minecraftVersion = minecraftVersion;
            this.removed = List.copyOf(removed);
            this.kept = List.copyOf(kept);
        }

        public String minecraftVersion() {
            return minecraftVersion;
        }

        public List<String> removed() {
            return removed;
        }

        /** Things left in place on purpose, each with the reason. */
        public List<String> kept() {
            return kept;
        }

        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append("ReMod was removed for Minecraft ").append(minecraftVersion)
                    .append(System.lineSeparator()).append(System.lineSeparator());
            sb.append("Removed:").append(System.lineSeparator());
            for (String entry : removed) {
                sb.append("  - ").append(entry).append(System.lineSeparator());
            }
            if (!kept.isEmpty()) {
                sb.append(System.lineSeparator()).append("Kept:").append(System.lineSeparator());
                for (String entry : kept) {
                    sb.append("  - ").append(entry).append(System.lineSeparator());
                }
            }
            return sb.toString();
        }
    }
}
