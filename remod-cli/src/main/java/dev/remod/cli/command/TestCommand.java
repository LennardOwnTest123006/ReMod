package dev.remod.cli.command;

import dev.remod.api.Side;
import dev.remod.api.registry.RegistryEntry;
import dev.remod.cli.CliCommand;
import dev.remod.cli.CommandLine;
import dev.remod.cli.Console;
import dev.remod.common.log.LogLevel;
import dev.remod.common.log.ReModLog;
import dev.remod.loader.LoadReport;
import dev.remod.loader.ReModLoader;
import dev.remod.loader.ReModPaths;
import dev.remod.loader.resolve.ModLoadError;
import dev.remod.loader.runtime.HeadlessGameBridge;
import dev.remod.loader.runtime.ModContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * {@code remod test} -- loads mods without starting Minecraft.
 *
 * <p>The fastest feedback loop a mod author has. It runs the real loader
 * against a real mods folder, with a headless bridge in place of the game, and
 * prints exactly what each mod registered and any errors -- in a second or two
 * rather than the minute a full Minecraft launch takes.</p>
 */
public final class TestCommand implements CliCommand {

    @Override
    public String name() {
        return "test";
    }

    @Override
    public String description() {
        return "Load mods without starting Minecraft and report what they registered";
    }

    @Override
    public String usage() {
        return "remod test [--mods <folder>] [--minecraft 1.21.4] [--side client|server]"
                + " [--verbose]";
    }

    @Override
    public int run(CommandLine commandLine, Console console) throws Exception {
        String minecraftVersion = commandLine.option("minecraft", "1.21.4");
        Side side = Side.parse(commandLine.option("side", "client"));
        if (commandLine.flag("verbose")) {
            ReModLog.setLevel(LogLevel.DEBUG);
        }

        Path workspace = Files.createTempDirectory("remod-test");
        ReModPaths paths = new ReModPaths(workspace);
        paths.createDirectories();

        String modsOption = commandLine.option("mods", null);
        if (modsOption != null) {
            Path source = Paths.get(modsOption).toAbsolutePath().normalize();
            if (!Files.isDirectory(source)) {
                console.error(source + " is not a folder.",
                        "Point --mods at a folder containing your built mod jars,"
                                + " for example build/libs.");
                return 1;
            }
            int copied = copyMods(source, paths.modsDirectory());
            console.print("Loading " + copied + " file(s) from " + source);
        } else {
            console.error("No mods folder was given.", "Usage: " + usage());
            return 2;
        }

        ReModLoader loader = new ReModLoader(paths, minecraftVersion, side);
        HeadlessGameBridge bridge = new HeadlessGameBridge(minecraftVersion, side);
        loader.installBridge(bridge);

        LoadReport report = loader.load();
        printReport(console, loader, report, minecraftVersion, side);
        loader.shutdown();

        deleteQuietly(workspace);
        return report.errors().isEmpty() ? 0 : 1;
    }

    private void printReport(Console console, ReModLoader loader, LoadReport report,
                             String minecraftVersion, Side side) {
        console.heading("Result");
        console.field("Minecraft", minecraftVersion);
        console.field("Side", side.token());
        console.field("ReMod API", loader.apiVersion().toString());
        console.field("Mods loaded", String.valueOf(report.loadedCount()));
        console.field("Mods rejected", String.valueOf(report.errors().size()));
        console.field("Took", report.durationMillis() + " ms");

        if (!report.loaded().isEmpty()) {
            console.heading("Loaded mods");
            for (ModContainer container : report.loaded()) {
                console.print("  " + container.metadata().name() + " "
                        + container.metadata().version().raw()
                        + " (" + container.id() + ")");
                printOwned(console, loader, container.id());
            }
        }

        if (!report.errors().isEmpty()) {
            console.heading("Errors");
            for (ModLoadError error : report.errors()) {
                console.blank();
                console.print(error.report().trim());
            }
        }

        if (!report.warnings().isEmpty()) {
            console.heading("Warnings");
            for (String warning : report.warnings()) {
                console.bullet(warning);
            }
        }

        console.heading("Note");
        console.print("This ran with no Minecraft attached, so registrations were recorded but");
        console.print("not bound to a game. That is enough to prove your mod loads, its"
                + " manifest is");
        console.print("valid and its initialisation runs without throwing.");
    }

    private void printOwned(Console console, ReModLoader loader, String modId) {
        printEntries(console, "items", loader.registries().items().entriesOf(modId));
        printEntries(console, "blocks", loader.registries().blocks().entriesOf(modId));
        printEntries(console, "creative tabs",
                loader.registries().creativeTabs().entriesOf(modId));
        java.util.List<String> commands = new java.util.ArrayList<>();
        loader.commands().commands().forEach(command ->
                loader.commands().ownerOf(command.name())
                        .filter(modId::equals)
                        .ifPresent(owner -> commands.add("/" + command.name())));
        if (!commands.isEmpty()) {
            console.print("      commands: " + String.join(", ", commands));
        }
    }

    private void printEntries(Console console, String label,
                              java.util.Collection<? extends RegistryEntry<?>> entries) {
        if (entries.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (RegistryEntry<?> entry : entries) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(entry.id());
        }
        console.print("      " + label + ": " + sb);
    }

    private static int copyMods(Path source, Path target) throws IOException {
        int copied = 0;
        try (java.util.stream.Stream<Path> stream = Files.list(source)) {
            for (Path file : stream.sorted().toList()) {
                String name = file.getFileName().toString();
                if (Files.isRegularFile(file)
                        && dev.remod.common.io.SafeZip.isArchiveName(name)
                        && !name.endsWith("-sources.jar")) {
                    Files.copy(file, target.resolve(name));
                    copied++;
                }
            }
        }
        return copied;
    }

    private static void deleteQuietly(Path path) {
        try {
            dev.remod.common.io.IOUtil.deleteRecursively(path);
        } catch (IOException e) {
            // A leftover temp directory is harmless.
        }
    }
}
