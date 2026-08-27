package dev.remod.api.command;

/** The body of a command. */
@FunctionalInterface
public interface CommandExecutor {

    /**
     * Runs the command.
     *
     * @return a result count, following Minecraft's convention where 0 means
     *         "did nothing" and any positive number means success; used by
     *         command blocks and {@code /execute store}
     * @throws CommandException to report a user-facing failure
     */
    int execute(CommandContext context) throws CommandException;
}
