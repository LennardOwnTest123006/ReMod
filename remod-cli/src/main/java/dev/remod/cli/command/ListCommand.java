package dev.remod.cli.command;

import dev.remod.adapter.VersionSupportTable;
import dev.remod.cli.CliCommand;
import dev.remod.cli.CommandLine;
import dev.remod.cli.Console;
import dev.remod.common.io.Platform;
import dev.remod.compat.CompatibilityRegistry;
import dev.remod.compat.LoaderBridge;
import dev.remod.compat.LoaderPlatform;
import dev.remod.installer.install.InstalledVersions;
import dev.remod.installer.manifest.ManifestException;
import dev.remod.installer.manifest.MinecraftVersionEntry;
import dev.remod.installer.manifest.MinecraftVersionManifest;
import dev.remod.installer.manifest.VersionManifestService;
import dev.remod.loader.ReModPaths;
import dev.remod.loader.discovery.DiscoveryResult;
import dev.remod.loader.discovery.ModCandidate;
import dev.remod.loader.discovery.ModDiscovery;
import dev.remod.loader.discovery.DiscoveryProblem;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/** {@code remod list} -- what is installed, what is available, and what is detected. */
public final class ListCommand implements CliCommand {

    @Override
    public String name() {
        return "list";
    }

    @Override
    public String description() {
        return "List installed ReMod versions, installed mods, or available Minecraft versions";
    }

    @Override
    public String usage() {
        return "remod list [installs|mods|versions|loaders] [--directory <path>]"
                + " [--limit 20] [--offline]";
    }

    @Override
    public int run(CommandLine commandLine, Console console) throws Exception {
        String what = commandLine.positional(0) == null ? "installs" : commandLine.positional(0);
        Path directory = commandLine.has("directory")
                ? Paths.get(commandLine.option("directory", ".")).toAbsolutePath().normalize()
                : Platform.defaultMinecraftDirectory();
        ReModPaths paths = new ReModPaths(directory);

        switch (what.toLowerCase(java.util.Locale.ROOT)) {
            case "installs":
                return listInstalls(console, paths);
            case "mods":
                return listMods(console, paths);
            case "versions":
                return listVersions(console, commandLine);
            case "loaders":
                return listLoaders(console, paths);
            default:
                console.error("Unknown list '" + what + "'.", "Usage: " + usage());
                return 2;
        }
    }

    private int listInstalls(Console console, ReModPaths paths) {
        console.heading("ReMod installations in " + paths.gameDirectory());
        List<InstalledVersions.Installed> installed = InstalledVersions.scan(paths);
        if (installed.isEmpty()) {
            console.print("  None. Run 'remod install <version>' or open ReMod.jar.");
            return 0;
        }
        for (InstalledVersions.Installed entry : installed) {
            console.print("  " + entry.versionId());
            console.field("  Minecraft", entry.minecraftVersion());
            console.field("  ReMod", entry.loaderVersion());
            console.field("  API", entry.apiVersion() == null ? "unknown" : entry.apiVersion());
        }
        return 0;
    }

    private int listMods(Console console, ReModPaths paths) {
        console.heading("Mods in " + paths.modsDirectory());
        DiscoveryResult result = ModDiscovery.scan(paths.modsDirectory());
        if (result.candidates().isEmpty() && result.problems().isEmpty()
                && result.foreignMods().isEmpty()) {
            console.print("  No mods installed.");
            return 0;
        }
        for (ModCandidate candidate : result.candidates()) {
            console.print("  " + candidate.metadata().name() + " "
                    + candidate.metadata().version().raw());
            console.field("  Id", candidate.id());
            console.field("  Minecraft", candidate.metadata().minecraft().raw());
            console.field("  ReMod API", candidate.metadata().apiVersion().toString());
            console.field("  File", candidate.fileName());
        }
        for (DiscoveryProblem problem : result.problems()) {
            console.blank();
            console.print("  Skipped " + problem.fileName() + ": " + problem.detail());
            console.print("    " + problem.suggestion());
        }
        for (DiscoveryResult.ForeignMod foreign : result.foreignMods()) {
            console.blank();
            console.print("  Skipped " + foreign.fileName() + ": this is a "
                    + foreign.loaderName() + " mod, not a ReMod mod.");
        }
        return 0;
    }

    private int listVersions(Console console, CommandLine commandLine) {
        VersionManifestService service = VersionManifestService.standard();
        service.setOffline(commandLine.flag("offline"));
        MinecraftVersionManifest manifest;
        try {
            manifest = service.get();
        } catch (ManifestException e) {
            console.error(e.getMessage(), e.suggestion());
            return 1;
        }
        int limit = Integer.parseInt(commandLine.option("limit", "20"));
        console.heading("Minecraft versions");
        manifest.latestRelease().ifPresent(id -> console.field("Latest release", id));
        manifest.latestSnapshot().ifPresent(id -> console.field("Latest snapshot", id));
        console.blank();
        console.print(String.format("  %-12s %-10s %-12s %s",
                "VERSION", "TYPE", "RELEASED", "REMOD SUPPORT"));
        int shown = 0;
        for (MinecraftVersionEntry entry : manifest.ofType(MinecraftVersionEntry.Type.RELEASE)) {
            if (shown++ >= limit) {
                break;
            }
            console.print(String.format("  %-12s %-10s %-12s %s",
                    entry.id(), entry.type().label(), entry.releaseDate(),
                    VersionSupportTable.supportFor(entry.id()).name()
                            .toLowerCase(java.util.Locale.ROOT)));
        }
        console.blank();
        console.print("  ReMod supports Minecraft " + VersionSupportTable.OLDEST_SUPPORTED
                + " and newer. Use --limit to see more.");
        return 0;
    }

    private int listLoaders(Console console, ReModPaths paths) {
        CompatibilityRegistry registry = CompatibilityRegistry.standard();
        console.heading("Other loaders detected in " + paths.gameDirectory());
        Map<LoaderPlatform, LoaderBridge.Detection> detected = registry.detectAll(paths);
        if (detected.isEmpty()) {
            console.print("  None.");
        } else {
            for (Map.Entry<LoaderPlatform, LoaderBridge.Detection> entry : detected.entrySet()) {
                console.print("  " + entry.getValue());
            }
        }
        console.heading("Compatibility");
        for (String line : registry.matrix().split("\n")) {
            console.print("  " + line);
        }
        console.blank();
        console.print("  Full details, including why each answer is what it is:"
                + " docs/compatibility.md");
        return 0;
    }
}
