package dev.remod;

import dev.remod.cli.Console;
import dev.remod.cli.ReModCli;
import dev.remod.installer.gui.InstallerWindow;
import dev.remod.installer.gui.Theme;
import dev.remod.loader.ReModVersions;

/**
 * The entry point of {@code ReMod.jar}.
 *
 * <p>One artifact, two front doors:</p>
 *
 * <ul>
 *   <li><b>Double-clicked</b>, or run with no arguments, it opens the installer
 *       window -- which is what a player expects from a downloaded jar.</li>
 *   <li><b>Given arguments</b>, it runs the {@code remod} command-line tool, so
 *       a developer or a server operator never has to open a GUI.</li>
 * </ul>
 *
 * <p>If the GUI cannot open -- a headless machine, or a broken display -- the
 * failure is explained and the CLI help is printed, rather than a stack trace
 * about {@code HeadlessException}.</p>
 */
public final class ReMod {

    private ReMod() {
    }

    public static void main(String[] args) {
        if (args != null && args.length > 0) {
            System.exit(new ReModCli().run(args, Console.standard()));
        }
        if (Theme.isHeadless()) {
            System.out.println("ReMod " + ReModVersions.loaderVersion());
            System.out.println();
            System.out.println("No display is available, so the installer window cannot open.");
            System.out.println("Use the command line instead:");
            System.out.println();
            new ReModCli().run(new String[]{"help"}, Console.standard());
            System.exit(0);
        }
        try {
            InstallerWindow.launch();
        } catch (RuntimeException | Error e) {
            System.err.println("ReMod could not open its window: " + e);
            System.err.println("Run 'java -jar ReMod.jar help' to use ReMod from the command"
                    + " line instead.");
            System.exit(1);
        }
    }
}
