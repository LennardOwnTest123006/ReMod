package dev.remod.cli;

import dev.remod.cli.command.BuildCommand;
import dev.remod.cli.command.CreateCommand;
import dev.remod.cli.command.InitCommand;
import dev.remod.cli.command.InstallCommand;
import dev.remod.cli.command.ListCommand;
import dev.remod.cli.command.PlayCommand;
import dev.remod.cli.command.TestCommand;
import dev.remod.common.log.LogLevel;
import dev.remod.common.log.ReModLog;
import dev.remod.installer.install.InstallException;
import dev.remod.installer.manifest.ManifestException;
import dev.remod.loader.ReModVersions;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@code remod} command-line tool.
 *
 * <p>Every command is reachable as {@code java -jar ReMod.jar <command>}; with
 * no arguments, {@code ReMod.jar} opens the installer GUI instead.</p>
 */
public final class ReModCli {

    private final Map<String, CliCommand> commands = new LinkedHashMap<>();

    public ReModCli() {
        register(new CreateCommand());
        register(new BuildCommand());
        register(new TestCommand());
        register(new PlayCommand());
        register(new InitCommand());
        register(new InstallCommand(false));
        register(new InstallCommand(true));
        register(new ListCommand());
    }

    private void register(CliCommand command) {
        commands.put(command.name(), command);
    }

    /** The registered commands, in the order they are listed by {@code help}. */
    public Map<String, CliCommand> commands() {
        return java.util.Collections.unmodifiableMap(commands);
    }

    /**
     * Runs one command line.
     *
     * @return the process exit code
     */
    public int run(String[] args, Console console) {
        CommandLine commandLine = CommandLine.parse(args);
        String verb = commandLine.verb();

        if (verb == null || verb.equals("help") || verb.equals("--help") || verb.equals("-h")) {
            printHelp(console, commandLine.positional(0));
            return 0;
        }
        if (verb.equals("version") || verb.equals("--version")) {
            console.print("ReMod " + ReModVersions.loaderVersion()
                    + " (API baseline " + ReModVersions.apiBaseline() + ")");
            return 0;
        }
        if (commandLine.flag("quiet")) {
            ReModLog.setLevel(LogLevel.WARN);
        }

        CliCommand command = commands.get(verb);
        if (command == null) {
            console.error("Unknown command '" + verb + "'.",
                    "Run 'remod help' to see the available commands.");
            return 2;
        }
        try {
            return command.run(commandLine, console);
        } catch (InstallException e) {
            console.error(e.getMessage(), e.suggestion());
            return 1;
        } catch (ManifestException e) {
            console.error(e.getMessage(), e.suggestion());
            return 1;
        } catch (IllegalArgumentException | IllegalStateException e) {
            console.error(e.getMessage(), "Run 'remod help " + verb + "' for the usage.");
            return 2;
        } catch (Exception e) {
            console.error(verb + " failed: " + e,
                    "If this looks like a ReMod bug, include the message above when reporting"
                            + " it.");
            return 1;
        }
    }

    private void printHelp(Console console, String topic) {
        if (topic != null && commands.containsKey(topic)) {
            CliCommand command = commands.get(topic);
            console.heading("remod " + command.name());
            console.print("  " + command.description());
            console.blank();
            console.print("  Usage: " + command.usage());
            return;
        }
        console.print("ReMod " + ReModVersions.loaderVersion()
                + " -- a mod loader for Minecraft: Java Edition");
        console.heading("Usage");
        console.print("  java -jar ReMod.jar                 Open the installer window");
        console.print("  java -jar ReMod.jar <command> ...   Run a command");
        console.heading("Commands");
        for (CliCommand command : commands.values()) {
            console.print(String.format("  %-11s %s", command.name(), command.description()));
        }
        console.print(String.format("  %-11s %s", "help", "Show this help, or help for one"
                + " command"));
        console.print(String.format("  %-11s %s", "version", "Print the ReMod version"));
        console.heading("Getting started");
        console.print("  java -jar ReMod.jar install 1.21.4     Install ReMod for Minecraft"
                + " 1.21.4");
        console.print("  java -jar ReMod.jar create MyMod       Create a mod project");
        console.print("  cd MyMod && ./gradlew build            Build it");
        console.print("  java -jar ReMod.jar play --mods build/libs   Run and WATCH your mod work");
        console.blank();
        console.print("  'play' runs your mod's commands against a simulated player, so you can");
        console.print("  see them work without launching Minecraft. Try it with the fly mod:");
        console.print("    java -jar ReMod.jar play --mods <folder> --run \"/fly\"");
        console.blank();
        console.print("  The complete beginner's guide is in tutorial.txt.");
    }
}
