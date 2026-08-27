package dev.remod.api.event.server;

import dev.remod.api.event.Event;
import dev.remod.api.game.ServerHandle;

/** Fired once the server is accepting connections. */
public final class ServerStartedEvent implements Event {

    private final ServerHandle server;

    public ServerStartedEvent(ServerHandle server) {
        this.server = server;
    }

    public ServerHandle server() {
        return server;
    }
}
