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
}
