package dev.remod.api.event.server;

import dev.remod.api.event.Event;
import dev.remod.api.game.ServerHandle;

/** Fired before the server accepts connections. Worlds are not loaded yet. */
public final class ServerStartingEvent implements Event {

    private final ServerHandle server;

    public ServerStartingEvent(ServerHandle server) {
        this.server = server;
    }

    public ServerHandle server() {
        return server;
    }
}
