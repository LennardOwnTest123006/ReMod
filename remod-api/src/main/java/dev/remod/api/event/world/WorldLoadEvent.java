package dev.remod.api.event.world;

import dev.remod.api.event.Event;
import dev.remod.api.game.WorldHandle;

/** Fired when a world/dimension finishes loading. */
public final class WorldLoadEvent implements Event {

    private final WorldHandle world;

    public WorldLoadEvent(WorldHandle world) {
        this.world = world;
    }

    public WorldHandle world() {
        return world;
    }
}
