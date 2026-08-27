package dev.remod.api.event.player;

import dev.remod.api.event.Event;
import dev.remod.api.game.PlayerHandle;

/** Fired on the server after a player respawns. */
public final class PlayerRespawnEvent implements Event {

    private final PlayerHandle player;
    private final boolean afterEndPortal;

    public PlayerRespawnEvent(PlayerHandle player, boolean afterEndPortal) {
        this.player = player;
        this.afterEndPortal = afterEndPortal;
    }

    public PlayerHandle player() {
        return player;
    }

    /** True when the player returned through the End portal rather than dying. */
    public boolean isAfterEndPortal() {
        return afterEndPortal;
    }
}
