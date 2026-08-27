package dev.remod.api.event.tick;

import dev.remod.api.event.Event;
import dev.remod.api.game.ServerHandle;

/** Fired twenty times a second on the server thread. */
public final class ServerTickEvent implements Event {

    private final TickPhase phase;
    private final long tickCount;
    private final ServerHandle server;

    public ServerTickEvent(TickPhase phase, long tickCount, ServerHandle server) {
        this.phase = phase;
        this.tickCount = tickCount;
        this.server = server;
    }

    public TickPhase phase() {
        return phase;
    }

    public long tickCount() {
        return tickCount;
    }

    public ServerHandle server() {
        return server;
    }

    public boolean every(int interval) {
        return interval > 0 && tickCount % interval == 0;
    }
}
