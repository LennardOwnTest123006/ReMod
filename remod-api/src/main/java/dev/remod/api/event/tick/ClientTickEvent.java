package dev.remod.api.event.tick;

import dev.remod.api.event.Event;

/**
 * Fired twenty times a second on the client thread.
 *
 * <p>This is the hottest event in ReMod. Anything slow here is felt directly by
 * the player, so keep handlers allocation-free and avoid I/O.</p>
 */
public final class ClientTickEvent implements Event {

    private final TickPhase phase;
    private final long tickCount;

    public ClientTickEvent(TickPhase phase, long tickCount) {
        this.phase = phase;
        this.tickCount = tickCount;
    }

    public TickPhase phase() {
        return phase;
    }

    /** Ticks elapsed since the client started. */
    public long tickCount() {
        return tickCount;
    }

    /** True once every {@code interval} ticks; handy for cheap throttling. */
    public boolean every(int interval) {
        return interval > 0 && tickCount % interval == 0;
    }
}
