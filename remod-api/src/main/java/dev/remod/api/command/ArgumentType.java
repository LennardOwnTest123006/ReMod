package dev.remod.api.command;

/**
 * The argument kinds ReMod commands can take.
 *
 * <p>A deliberately small set: these are the types whose parsing and
 * suggestion behaviour has been stable across every Minecraft version ReMod
 * targets, so a command written once keeps working.</p>
 */
public enum ArgumentType {

    /** A single word, or a quoted string. */
    STRING,

    /** The rest of the command line, spaces included. Must be the last argument. */
    GREEDY_STRING,

    INTEGER,

    DOUBLE,

    BOOLEAN,

    /** The name of an online player, with tab-completion. */
    PLAYER
}
