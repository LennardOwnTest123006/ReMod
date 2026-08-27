package dev.remod.api.event.player;

import dev.remod.api.event.AbstractCancellableEvent;
import dev.remod.api.game.PlayerHandle;

/**
 * Fired on the server when a player sends a chat message.
 *
 * <p>Cancelling suppresses the message. The text may also be rewritten, which
 * is how chat-formatting mods work.</p>
 */
public final class PlayerChatEvent extends AbstractCancellableEvent {

    private final PlayerHandle player;
    private final String originalMessage;
    private String message;

    public PlayerChatEvent(PlayerHandle player, String message) {
        this.player = player;
        this.originalMessage = message;
        this.message = message;
    }

    public PlayerHandle player() {
        return player;
    }

    /** The message as the player typed it, unaffected by other listeners. */
    public String originalMessage() {
        return originalMessage;
    }

    /** The message as it stands after earlier listeners have run. */
    public String message() {
        return message;
    }

    public void setMessage(String value) {
        this.message = value == null ? "" : value;
    }
}
