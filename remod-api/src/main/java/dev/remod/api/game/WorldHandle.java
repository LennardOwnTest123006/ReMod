package dev.remod.api.game;

/** A loaded world/dimension. */
public interface WorldHandle {

    /** The dimension id, e.g. {@code minecraft:overworld}. */
    Identifier dimension();

    /** True for the client's local copy of a world. */
    boolean isClient();

    /** Total world time in ticks. */
    long time();

    /** Time of day in ticks, 0-23999. */
    long timeOfDay();

    /** The number of players currently in this world. */
    int playerCount();
}
