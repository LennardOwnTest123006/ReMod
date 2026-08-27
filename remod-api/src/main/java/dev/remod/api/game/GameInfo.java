package dev.remod.api.game;

import dev.remod.api.Side;

/** Facts about the Minecraft installation this process is running. */
public interface GameInfo {

    /** The exact Minecraft version id, e.g. {@code 1.21.4}. */
    String minecraftVersion();

    /** The release series, e.g. {@code 1.21}, or {@code null} for a snapshot. */
    String minecraftSeries();

    /** The side this process runs as. */
    Side side();

    /**
     * True when ReMod is attached to a real Minecraft process.
     *
     * <p>False when running under {@code remod test} or a unit test, where
     * mods still initialise and register content but no game exists to bind
     * it to. A mod that must touch live game state should check this.</p>
     */
    boolean isGameAttached();

    /** The id of the version adapter bound to this game, e.g. {@code remod:generic-1.21}. */
    String adapterId();
}
