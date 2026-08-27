package dev.remod.api.event.world;

import dev.remod.api.event.Event;
import dev.remod.api.game.WorldHandle;

/** Fired when a world is saved. A good moment to persist mod data alongside it. */
public final class WorldSaveEvent implements Event {

    private final WorldHandle world;

    public WorldSaveEvent(WorldHandle world) {
        this.world = world;
    }

    public WorldHandle world() {
        return world;
    }
}
