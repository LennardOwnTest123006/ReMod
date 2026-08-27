package dev.remod.api.event.server;

import dev.remod.api.event.Event;
import dev.remod.api.game.ServerHandle;

/** Fired as the server begins shutting down. Players are still connected. */
public final class ServerStoppingEvent implements Event {

    private final ServerHandle server;

    public ServerStoppingEvent(ServerHandle server) {
        this.server = server;
    }

    public ServerHandle server() {
        return server;
    }
}
