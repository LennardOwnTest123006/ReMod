package dev.remod.api.event.lifecycle;

import dev.remod.api.event.Event;

import java.util.Collections;
import java.util.List;

/** Fired once every mod has completed {@code POST_INIT}. */
public final class ModsLoadedEvent implements Event {

    private final List<String> modIds;

    public ModsLoadedEvent(List<String> modIds) {
        this.modIds = Collections.unmodifiableList(modIds);
    }

    /** The ids of every successfully loaded mod, in load order. */
    public List<String> modIds() {
        return modIds;
    }

    public int modCount() {
        return modIds.size();
    }
}
