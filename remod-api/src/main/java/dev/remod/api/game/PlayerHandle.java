package dev.remod.api.game;

import java.util.UUID;

/**
 * A player, as far as ReMod exposes one.
 *
 * <p>Deliberately narrow. It covers what a mod needs for chat, commands,
 * permissions and positioning without ReMod pretending to mirror Minecraft's
 * entire entity hierarchy across versions.</p>
 */
public interface PlayerHandle {

    UUID uuid();

    /** The player's current name. */
    String name();

    /** Sends a chat message to this player only. */
    void sendMessage(Text message);

    /** Shows a message above the hotbar rather than in the chat log. */
    void sendActionBar(Text message);

    Vec3 position();

    /** The id of the dimension the player is in, e.g. {@code minecraft:overworld}. */
    Identifier dimension();

    /**
     * The player's permission level, using Minecraft's own 0-4 scale where 2 is
     * the usual threshold for cheat-like commands and 4 is full operator.
     */
    int permissionLevel();

    default boolean isOperator() {
        return permissionLevel() >= 2;
    }

    /** True when this handle still refers to a connected player. */
    boolean isOnline();

    /** The player's current game mode. */
    GameMode gameMode();

    // --- flight abilities -------------------------------------------------
    //
    // These mirror the four fields Minecraft keeps in a player's abilities:
    // whether flight is permitted, whether the player is currently flying, and
    // the flying speed. They are on PlayerHandle rather than behind a separate
    // service because they are per-player state, and a mod that changes them
    // for one player must not be able to reach another's by accident.

    /**
     * True when this player is allowed to fly.
     *
     * <p>Creative and spectator grant this regardless of what a mod set, so
     * check {@link #gameMode()} before concluding a mod's own toggle is what
     * put the player in the air.</p>
     */
    boolean isFlightAllowed();

    /**
     * Allows or forbids flight for this player.
     *
     * <p>Forbidding it while the player is airborne drops them, exactly as
     * leaving creative mode does. A mod that wants to avoid that should check
     * {@link #isFlying()} first.</p>
     *
     * <p>Has no effect in a game mode that grants flight on its own.</p>
     */
    void setFlightAllowed(boolean allowed);

    /** True when the player is currently airborne under flight, not falling. */
    boolean isFlying();

    /**
     * Starts or stops flight now.
     *
     * <p>Ignored when {@link #isFlightAllowed()} is false: the game would
     * simply drop the player again on the next tick.</p>
     */
    void setFlying(boolean flying);

    /**
     * The flying speed. Minecraft's default is {@code 0.05}.
     *
     * <p>The scale is the game's own, not a multiplier: {@code 0.1} is roughly
     * twice vanilla speed.</p>
     */
    float flightSpeed();

    /**
     * Sets the flying speed.
     *
     * @param speed a value in the game's own scale, normally between
     *              {@code 0.0} and {@code 1.0}; implementations clamp rather
     *              than throw, because a mod should not be able to crash a
     *              session with an out-of-range number
     */
    void setFlightSpeed(float speed);
}
