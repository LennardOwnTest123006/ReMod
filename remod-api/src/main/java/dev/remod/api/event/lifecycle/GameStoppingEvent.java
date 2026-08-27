package dev.remod.api.event.lifecycle;

import dev.remod.api.event.Event;

/** Fired as the process shuts down, before mods receive {@code SHUTDOWN}. */
public final class GameStoppingEvent implements Event {

    private final boolean crashed;

    public GameStoppingEvent(boolean crashed) {
        this.crashed = crashed;
    }

    /** True when the shutdown follows a crash rather than a clean exit. */
    public boolean isCrash() {
        return crashed;
    }
}
