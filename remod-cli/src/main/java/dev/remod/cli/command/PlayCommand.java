package dev.remod.cli.command;

import dev.remod.api.Side;
import dev.remod.api.event.player.PlayerJoinEvent;
import dev.remod.api.game.GameMode;
import dev.remod.cli.CliCommand;
import dev.remod.cli.CommandLine;
import dev.remod.cli.Console;
import dev.remod.common.log.LogLevel;
import dev.remod.common.log.ReModLog;
import dev.remod.loader.LoadReport;
import dev.remod.loader.ReModLoader;
import dev.remod.loader.ReModPaths;
import dev.remod.loader.runtime.HeadlessGameBridge;
import dev.remod.loader.sim.SimulatedCommands;
import dev.remod.loader.sim.SimulatedPlayer;
import dev.remod.loader.sim.SimulatedServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * {@code remod play} -- run your mod's commands against a real, simulated
 * player and watch them work, with no Minecraft needed.
 *
 * <p>This is the answer to "how do I know it actually works?". It loads the
 * mods, gives them a genuinely working single-player world -- a player whose
 * flight state really changes, a server that really is single-player -- and
 * then runs whatever commands you type through the mod's own code. When
 * {@code /fly} flips {@code isFlying()} from false to true, that is the mod
 * doing it, not a script pretending.</p>
 *
 * <p>It does not launch Minecraft and it does not need the game's obfuscated
 * internals, so it always works and always shows the truth about what the mod
 * does.</p>
 */
public final class PlayCommand implements CliCommand {

    @Override
    public String name() {
        return "play";
    }

    @Override
    public String description() {
        return "Run your mods' commands against a simulated player, to see them work";
    }

    @Override
    public String usage() {
        return "remod play [--mods <folder>] [--player Steve] [--gamemode survival]"
                + " [--run \"/fly\"]";
    }

    @Override
    public int run(CommandLine commandLine, Console console) throws Exception {
        String modsOption = commandLine.option("mods", null);
        if (modsOption == null) {
            console.error("No mods folder was given.", "Usage: " + usage());
            return 2;
        }
        Path source = Paths.get(modsOption).toAbsolutePath().normalize();
        if (!Files.isDirectory(source)) {
            console.error(source + " is not a folder.",
                    "Point --mods at a folder with your built mod jars, e.g. build/libs.");
            return 1;
        }

        // Keep the console readable: send loader chatter to the log level only.
        ReModLog.setLevel(commandLine.flag("verbose") ? LogLevel.DEBUG : LogLevel.WARN);

        Path workspace = Files.createTempDirectory("remod-play");
        ReModPaths paths = new ReModPaths(workspace);
        paths.createDirectories();
        copyMods(source, paths.modsDirectory());

        ReModLoader loader = new ReModLoader(paths, commandLine.option("minecraft", "1.21.4"),
                Side.COMMON);
        loader.installBridge(new HeadlessGameBridge(loader.minecraftVersion(), Side.COMMON));
        LoadReport report = loader.load();

        if (report.loadedCount() == 0) {
            console.error("No mods loaded from " + source + ".",
                    "Build your mod first, then point --mods at build/libs.");
            for (var error : report.errors()) {
                console.blank();
                console.print(error.report().trim());
            }
            return 1;
        }

        String playerName = commandLine.option("player", "Steve");
        GameMode mode = GameMode.parse(commandLine.option("gamemode", "survival"),
                GameMode.SURVIVAL);
        SimulatedPlayer player = new SimulatedPlayer(playerName, 4, mode);
        SimulatedServer server = SimulatedServer.singlePlayer(player);
        SimulatedCommands commands = new SimulatedCommands(loader.commands(), server);

        // Fire the join event, so mods that greet a joining player behave as
        // they would in a real world.
        loader.events().post(new PlayerJoinEvent(player, server, true));
        int greeted = player.inbox().size();

        console.heading("ReMod Play -- a simulated single-player world");
        console.field("World owner", playerName + " (permission level 4)");
        console.field("Game mode", mode.token());
        console.print("  Mods loaded:");
        report.loaded().forEach(container ->
                console.print("    - " + container.metadata().name() + " "
                        + container.metadata().version().raw()));
        console.print("  Commands available:");
        loader.commands().commands().forEach(spec ->
                console.print("    /" + spec.name()
                        + (spec.aliases().isEmpty() ? ""
                                : " (or /" + String.join(", /", spec.aliases()) + ")")));
        for (int i = 0; i < greeted; i++) {
            console.print("  [chat] " + player.inbox().get(i));
        }

        String scripted = commandLine.option("run", null);
        if (scripted != null) {
            for (String line : scripted.split(";")) {
                runLine(console, commands, player, line.trim());
            }
            summarise(console, player);
            deleteQuietly(workspace);
            loader.shutdown();
            return 0;
        }

        console.blank();
        console.print("Type a command to run it. Try:  /fly   then   /fly status");
        console.print("Type 'state' to see the player, or 'quit' to exit.");
        console.blank();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            console.out().print("> ");
            console.out().flush();
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("exit")) {
                    break;
                }
                if (line.equalsIgnoreCase("state")) {
                    summarise(console, player);
                } else if (!line.isEmpty()) {
                    runLine(console, commands, player, line);
                }
                console.out().print("> ");
                console.out().flush();
            }
        }
        deleteQuietly(workspace);
        loader.shutdown();
        return 0;
    }

    /** Runs one line and shows what the mod did, and its effect on the player. */
    private void runLine(Console console, SimulatedCommands commands,
                         SimulatedPlayer player, String line) {
        if (line.isEmpty()) {
            return;
        }
        boolean wasFlying = player.isFlying();
        boolean couldFly = player.isFlightAllowed();
        int inboxBefore = player.inbox().size();
        int actionBarBefore = player.actionBar().size();

        SimulatedCommands.Result result = commands.run(line, player);

        console.print("$ " + (line.startsWith("/") ? line : "/" + line));
        if (!result.commandFound()) {
            console.print("  Unknown command. Type a command a loaded mod registered.");
            return;
        }
        result.feedback().forEach(message -> console.print("  [reply] " + message));
        result.errors().forEach(message -> console.print("  [error] " + message));
        for (int i = inboxBefore; i < player.inbox().size(); i++) {
            console.print("  [chat]  " + player.inbox().get(i));
        }
        for (int i = actionBarBefore; i < player.actionBar().size(); i++) {
            console.print("  [hotbar] " + player.actionBar().get(i));
        }
        // Show the real state change the mod caused -- this is the proof.
        if (player.isFlightAllowed() != couldFly || player.isFlying() != wasFlying) {
            console.print("  -> flight allowed: " + couldFly + " -> "
                    + player.isFlightAllowed()
                    + ",  flying: " + wasFlying + " -> " + player.isFlying());
        }
    }

    private void summarise(Console console, SimulatedPlayer player) {
        console.heading("Player state");
        console.field("Name", player.name());
        console.field("Game mode", player.gameMode().token());
        console.field("Flight allowed", String.valueOf(player.isFlightAllowed()));
        console.field("Currently flying", String.valueOf(player.isFlying()));
        console.field("Flight speed", String.valueOf(player.flightSpeed()));
    }

    private static void copyMods(Path source, Path target) throws IOException {
        try (var stream = Files.list(source)) {
            for (Path file : stream.sorted().toList()) {
                String jar = file.getFileName().toString();
                if (Files.isRegularFile(file)
                        && dev.remod.common.io.SafeZip.isArchiveName(jar)
                        && !jar.endsWith("-sources.jar")) {
                    Files.copy(file, target.resolve(jar));
                }
            }
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            dev.remod.common.io.IOUtil.deleteRecursively(path);
        } catch (IOException e) {
            // A leftover temp directory is harmless.
        }
    }
}
