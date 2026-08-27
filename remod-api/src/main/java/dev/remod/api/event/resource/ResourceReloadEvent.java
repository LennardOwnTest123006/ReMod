package dev.remod.api.event.resource;

import dev.remod.api.Side;
import dev.remod.api.event.Event;

/**
 * Fired when the game reloads resource or data packs -- at startup, on
 * {@code F3+T}, and on {@code /reload}.
 */
public final class ResourceReloadEvent implements Event {

    private final Side side;
    private final boolean initial;

    public ResourceReloadEvent(Side side, boolean initial) {
        this.side = side;
        this.initial = initial;
    }

    /** {@link Side#CLIENT} for resource packs, {@link Side#DEDICATED_SERVER} for data packs. */
    public Side side() {
        return side;
    }

    /** True for the reload that happens during startup. */
    public boolean isInitial() {
        return initial;
    }
}
