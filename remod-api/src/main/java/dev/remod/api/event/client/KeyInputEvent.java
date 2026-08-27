package dev.remod.api.event.client;

import dev.remod.api.client.Key;
import dev.remod.api.event.AbstractCancellableEvent;

/**
 * Fired on the client for a raw key press or release.
 *
 * <p>For an action the player should be able to rebind, register a
 * {@link dev.remod.api.client.Keybind} instead -- it shows up in the Controls
 * screen, this does not.</p>
 */
public final class KeyInputEvent extends AbstractCancellableEvent {

    private final Key key;
    private final Action action;
    private final boolean shift;
    private final boolean control;
    private final boolean alt;

    public KeyInputEvent(Key key, Action action, boolean shift, boolean control, boolean alt) {
        this.key = key;
        this.action = action;
        this.shift = shift;
        this.control = control;
        this.alt = alt;
    }

    public Key key() {
        return key;
    }

    public Action action() {
        return action;
    }

    public boolean isShiftDown() {
        return shift;
    }

    public boolean isControlDown() {
        return control;
    }

    public boolean isAltDown() {
        return alt;
    }

    /** What happened to the key. */
    public enum Action {
        PRESS,
        RELEASE,
        /** A key held long enough to auto-repeat. */
        REPEAT
    }
}
