package dev.remod.api.event.player;

import dev.remod.api.event.Event;
import dev.remod.api.game.PlayerHandle;
import dev.remod.api.game.ServerHandle;

/** Fired on the server when a player finishes connecting. */
public final class PlayerJoinEvent implements Event {

    private final PlayerHandle player;
    private final ServerHandle server;
    private final boolean firstJoin;

    public PlayerJoinEvent(PlayerHandle player, ServerHandle server, boolean firstJoin) {
        this.player = player;
        this.server = server;
        this.firstJoin = firstJoin;
    }

    public PlayerHandle player() {
        return player;
    }

    public ServerHandle server() {
        return server;
    }

    /** True the first time this player has ever joined this world. */
    public boolean isFirstJoin() {
        return firstJoin;
    }
}
