package dev.remod.cli;

/** One {@code remod} subcommand. */
public interface CliCommand {

    /** The verb, e.g. {@code create}. */
    String name();

    /** The one-line description shown by {@code remod help}. */
    String description();

    /** The usage line, e.g. {@code remod create <name> [--package ...]}. */
    String usage();

    /**
     * Runs the command.
     *
     * @return the process exit code: 0 for success
     */
    int run(CommandLine commandLine, Console console) throws Exception;
}
