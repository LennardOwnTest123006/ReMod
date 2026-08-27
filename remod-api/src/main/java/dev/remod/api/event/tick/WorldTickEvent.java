package dev.remod.api.event.tick;

import dev.remod.api.event.Event;
import dev.remod.api.game.WorldHandle;

/** Fired once per world, per server tick. */
public final class WorldTickEvent implements Event {

    private final TickPhase phase;
    private final WorldHandle world;

    public WorldTickEvent(TickPhase phase, WorldHandle world) {
        this.phase = phase;
        this.world = world;
    }

    public TickPhase phase() {
        return phase;
    }

    public WorldHandle world() {
        return world;
    }
}
