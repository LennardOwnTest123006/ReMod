package dev.remod.api.event.player;

import dev.remod.api.event.Event;
import dev.remod.api.game.PlayerHandle;
import dev.remod.api.game.ServerHandle;

/**
 * Fired on the server when a player disconnects.
 *
 * <p>The handle is still readable here but the player is already leaving, so
 * do not try to send them anything.</p>
 */
public final class PlayerQuitEvent implements Event {

    private final PlayerHandle player;
    private final ServerHandle server;

    public PlayerQuitEvent(PlayerHandle player, ServerHandle server) {
        this.player = player;
        this.server = server;
    }

    public PlayerHandle player() {
        return player;
    }

    public ServerHandle server() {
        return server;
    }
}
