package dev.remod.api.command;

import dev.remod.api.game.PlayerHandle;

import java.util.Optional;

/** The parsed arguments and caller for one command invocation. */
public interface CommandContext {

    CommandSource source();

    /** The full command line as typed, without the leading slash. */
    String input();

    /**
     * A required argument.
     *
     * @throws IllegalArgumentException when the command declared no such argument
     */
    String getString(String name);

    int getInt(String name);

    double getDouble(String name);

    boolean getBoolean(String name);

    /** The player named by a {@link ArgumentType#PLAYER} argument, if still online. */
    Optional<PlayerHandle> getPlayer(String name);

    /** An optional argument, empty when it was not supplied. */
    Optional<String> optString(String name);

    /** True when the named argument was supplied. */
    boolean has(String name);
}
