package dev.remod.common.io;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Operating-system specifics: where Minecraft lives, where ReMod keeps its own
 * state, and how to open a folder in the desktop file manager.
 */
public final class Platform {

    /** The operating-system families ReMod distinguishes. */
    public enum Os { WINDOWS, MACOS, LINUX, OTHER }

    private static final Os CURRENT = detect();

    private Platform() {
    }

    private static Os detect() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.contains("win")) {
            return Os.WINDOWS;
        }
        if (name.contains("mac") || name.contains("darwin")) {
            return Os.MACOS;
        }
        if (name.contains("nux") || name.contains("nix") || name.contains("aix")
                || name.contains("bsd") || name.contains("sunos")) {
            return Os.LINUX;
        }
        return Os.OTHER;
    }

    public static Os os() {
        return CURRENT;
    }

    public static boolean isWindows() {
        return CURRENT == Os.WINDOWS;
    }

    public static boolean isMac() {
        return CURRENT == Os.MACOS;
    }

    /** The Minecraft launcher's {@code osName} token, as used in version JSON rules. */
    public static String minecraftOsName() {
        switch (CURRENT) {
            case WINDOWS: return "windows";
            case MACOS:   return "osx";
            case LINUX:   return "linux";
            default:      return "unknown";
        }
    }

    public static Path userHome() {
        return Paths.get(System.getProperty("user.home", "."));
    }

    /**
     * The default {@code .minecraft} directory for this operating system.
     *
     * <p>Honours the {@code remod.minecraftDir} system property first, so tests
     * and users with relocated installs can override it without editing
     * anything.</p>
     */
    public static Path defaultMinecraftDirectory() {
        String override = System.getProperty("remod.minecraftDir");
        if (override != null && !override.isEmpty()) {
            return Paths.get(override);
        }
        switch (CURRENT) {
            case WINDOWS: {
                String appData = System.getenv("APPDATA");
                if (appData != null && !appData.isEmpty()) {
                    return Paths.get(appData, ".minecraft");
                }
                return userHome().resolve("AppData/Roaming/.minecraft");
            }
            case MACOS:
                return userHome().resolve("Library/Application Support/minecraft");
            default:
                return userHome().resolve(".minecraft");
        }
    }

    /**
     * Every place a Minecraft installation plausibly lives on this machine, in
     * preference order. The installer offers these when the default is absent.
     */
    public static List<Path> candidateMinecraftDirectories() {
        List<Path> candidates = new ArrayList<>();
        candidates.add(defaultMinecraftDirectory());
        switch (CURRENT) {
            case WINDOWS:
                candidates.add(userHome().resolve("AppData/Roaming/.minecraft"));
                candidates.add(Paths.get("C:/Program Files (x86)/Minecraft Launcher/.minecraft"));
                break;
            case MACOS:
                candidates.add(userHome().resolve("Library/Application Support/minecraft"));
                break;
            default:
                candidates.add(userHome().resolve(".minecraft"));
                // Flatpak and Snap relocate the launcher's home directory.
                candidates.add(userHome().resolve(
                        ".var/app/com.mojang.Minecraft/data/minecraft"));
                candidates.add(userHome().resolve("snap/mc-installer/current/.minecraft"));
                break;
        }
        List<Path> distinct = new ArrayList<>();
        for (Path candidate : candidates) {
            Path normalised = candidate.toAbsolutePath().normalize();
            if (!distinct.contains(normalised)) {
                distinct.add(normalised);
            }
        }
        return distinct;
    }

    /** The first candidate directory that actually exists, or {@code null}. */
    public static Path findExistingMinecraftDirectory() {
        for (Path candidate : candidateMinecraftDirectories()) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * ReMod's own state directory -- caches, logs and the installed API jars.
     *
     * <p>Kept outside {@code .minecraft} so that uninstalling ReMod from one
     * Minecraft install never touches another, and so a user who deletes
     * {@code .minecraft} does not lose ReMod's download cache.</p>
     */
    public static Path remodHome() {
        String override = System.getProperty("remod.home");
        if (override != null && !override.isEmpty()) {
            return Paths.get(override);
        }
        switch (CURRENT) {
            case WINDOWS: {
                String appData = System.getenv("APPDATA");
                if (appData != null && !appData.isEmpty()) {
                    return Paths.get(appData, "ReMod");
                }
                return userHome().resolve("AppData/Roaming/ReMod");
            }
            case MACOS:
                return userHome().resolve("Library/Application Support/ReMod");
            default: {
                String xdg = System.getenv("XDG_DATA_HOME");
                if (xdg != null && !xdg.isEmpty()) {
                    return Paths.get(xdg, "remod");
                }
                return userHome().resolve(".local/share/remod");
            }
        }
    }

    /** Opens {@code path} in the desktop file manager. Returns false when unsupported. */
    public static boolean openInFileManager(Path path) {
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
                if (desktop.isSupported(java.awt.Desktop.Action.OPEN)) {
                    desktop.open(path.toFile());
                    return true;
                }
            }
            // Headless Linux desktops usually still have xdg-open.
            if (!isWindows() && !isMac()) {
                new ProcessBuilder("xdg-open", path.toString()).start();
                return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }
}
