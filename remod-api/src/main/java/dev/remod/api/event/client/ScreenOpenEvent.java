package dev.remod.api.event.client;

import dev.remod.api.event.AbstractCancellableEvent;

/**
 * Fired when the client is about to open a screen.
 *
 * <p>The screen is identified by its class name rather than an instance, so a
 * mod can react to vanilla screens without compiling against them. Cancelling
 * prevents the screen from opening.</p>
 */
public final class ScreenOpenEvent extends AbstractCancellableEvent {

    private final String screenClassName;
    private final boolean closing;

    public ScreenOpenEvent(String screenClassName, boolean closing) {
        this.screenClassName = screenClassName;
        this.closing = closing;
    }

    /** The fully qualified class name of the screen, e.g. {@code ...TitleScreen}. */
    public String screenClassName() {
        return screenClassName;
    }

    /** The simple class name, which is what most mods actually match on. */
    public String screenSimpleName() {
        int dot = screenClassName.lastIndexOf('.');
        return dot < 0 ? screenClassName : screenClassName.substring(dot + 1);
    }

    /** True when the screen is being closed rather than opened. */
    public boolean isClosing() {
        return closing;
    }
}
