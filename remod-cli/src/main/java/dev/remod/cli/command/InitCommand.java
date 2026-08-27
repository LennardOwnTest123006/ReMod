package dev.remod.cli.command;

import dev.remod.cli.CliCommand;
import dev.remod.cli.CommandLine;
import dev.remod.cli.Console;
import dev.remod.common.io.Platform;
import dev.remod.loader.ReModPaths;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * {@code remod init} -- creates ReMod's directory tree in a Minecraft folder.
 *
 * <p>Normally the installer does this. It exists as a command for server
 * operators and scripted setups, where there is no launcher to install a
 * profile into but the mods and config folders are still needed.</p>
 */
public final class InitCommand implements CliCommand {

    @Override
    public String name() {
        return "init";
    }

    @Override
    public String description() {
        return "Create ReMod's folders in a Minecraft or server directory";
    }

    @Override
    public String usage() {
        return "remod init [--directory <path to .minecraft or server folder>]";
    }

    @Override
    public int run(CommandLine commandLine, Console console) throws Exception {
        String option = commandLine.option("directory", null);
        Path directory = option != null
                ? Paths.get(option).toAbsolutePath().normalize()
                : Platform.defaultMinecraftDirectory();

        ReModPaths paths = new ReModPaths(directory);
        paths.createDirectories();

        console.heading("ReMod folders ready");
        console.field("Game directory", paths.gameDirectory().toString());
        console.field("Mods", paths.modsDirectory().toString());
        console.field("Config", paths.configDirectory().toString());
        console.field("Data", paths.dataDirectory().toString());
        console.field("Logs", paths.logsDirectory().toString());
        console.field("API jars", paths.apiDirectory().toString());
        console.blank();
        if (!paths.looksLikeMinecraftDirectory()) {
            console.print("Note: this does not look like a Minecraft client installation.");
            console.print("That is fine for a server, but 'remod install' needs a real"
                    + " .minecraft folder.");
        } else {
            console.print("Put your ReMod mods in the mods folder above.");
        }
        return 0;
    }
}
