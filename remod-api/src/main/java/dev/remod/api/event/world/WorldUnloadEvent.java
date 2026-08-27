package dev.remod.api.event.world;

import dev.remod.api.event.Event;
import dev.remod.api.game.WorldHandle;

/** Fired as a world unloads. Flush anything you keep per world here. */
public final class WorldUnloadEvent implements Event {

    private final WorldHandle world;

    public WorldUnloadEvent(WorldHandle world) {
        this.world = world;
    }

    public WorldHandle world() {
        return world;
    }
}
