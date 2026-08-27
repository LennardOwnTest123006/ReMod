package dev.remod.cli.command;

import dev.remod.adapter.VersionSupportTable;
import dev.remod.cli.CliCommand;
import dev.remod.cli.CommandLine;
import dev.remod.cli.Console;
import dev.remod.common.io.Platform;
import dev.remod.installer.install.InstallException;
import dev.remod.installer.install.InstallRequest;
import dev.remod.installer.install.InstallResult;
import dev.remod.installer.install.InstalledVersions;
import dev.remod.installer.install.ReModInstaller;
import dev.remod.installer.install.ReModUninstaller;
import dev.remod.loader.ReModPaths;

import java.nio.file.Path;
import java.nio.file.Paths;

/** {@code remod install} and {@code remod uninstall} -- the GUI's job, without a display. */
public final class InstallCommand implements CliCommand {

    private final boolean uninstall;

    public InstallCommand(boolean uninstall) {
        this.uninstall = uninstall;
    }

    @Override
    public String name() {
        return uninstall ? "uninstall" : "install";
    }

    @Override
    public String description() {
        return uninstall
                ? "Remove a ReMod installation (mods, settings and worlds are kept)"
                : "Install ReMod for a Minecraft version without opening the GUI";
    }

    @Override
    public String usage() {
        return "remod " + name() + " <minecraft-version> [--directory <path to .minecraft>]"
                + (uninstall ? "" : " [--no-profile] [--no-download]");
    }

    /**
     * Looks the version up in Mojang's manifest, which carries the download
     * URLs and checksums.
     *
     * <p>Returns {@code null} when the manifest is unreachable: the install
     * then proceeds without downloading, and the launcher fetches Minecraft on
     * first launch as it always would.</p>
     */
    private dev.remod.installer.manifest.MinecraftVersionEntry manifestEntry(
            String version, Console console) {
        try {
            return dev.remod.installer.manifest.VersionManifestService.standard()
                    .get().find(version).orElse(null);
        } catch (RuntimeException e) {
            console.print("  Could not reach the Minecraft version list, so Minecraft will"
                    + " not be pre-downloaded.");
            console.print("  The official launcher will download it on first launch.");
            return null;
        }
    }

    @Override
    public int run(CommandLine commandLine, Console console) throws Exception {
        String version = commandLine.positional(0);
        if (version == null || version.isBlank()) {
            console.error("No Minecraft version was given.", "Usage: " + usage());
            return 2;
        }
        Path directory = commandLine.has("directory")
                ? Paths.get(commandLine.option("directory", ".")).toAbsolutePath().normalize()
                : Platform.defaultMinecraftDirectory();

        try {
            if (uninstall) {
                ReModUninstaller.Result result =
                        new ReModUninstaller(new ReModPaths(directory)).uninstall(version);
                console.blank();
                console.print(result.summary().trim());
                return 0;
            }
            boolean download = !commandLine.flag("no-download");
            InstallRequest request = InstallRequest.builder(version, directory)
                    .createLauncherProfile(!commandLine.flag("no-profile"))
                    .downloadMinecraft(download)
                    .manifestEntry(download ? manifestEntry(version, console) : null)
                    .build();
            InstallResult result = new ReModInstaller()
                    .install(request, (what, done, total) -> console.print("  " + what));
            console.blank();
            console.print(result.summary().trim());
            return 0;
        } catch (InstallException e) {
            console.error(e.getMessage(), e.suggestion());
            if (!uninstall && !VersionSupportTable.isInstallable(version)) {
                console.blank();
                console.print("Installed ReMod versions in " + directory + ":");
                for (InstalledVersions.Installed installed
                        : InstalledVersions.scan(new ReModPaths(directory))) {
                    console.bullet(installed.toString());
                }
            }
            return 1;
        }
    }
}
