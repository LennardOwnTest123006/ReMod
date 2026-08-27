package dev.remod.api.event.client;

import dev.remod.api.client.gui.DrawContext;
import dev.remod.api.event.AbstractCancellableEvent;

/**
 * Fired every frame while the HUD is drawn.
 *
 * <p>Registering a {@link dev.remod.api.client.gui.HudLayer} is usually
 * preferable: layers can be hidden and removed individually. This event exists
 * for mods that need to draw at a specific point relative to other listeners,
 * or that want to suppress ReMod's HUD drawing entirely by cancelling.</p>
 */
public final class HudRenderEvent extends AbstractCancellableEvent {

    private final DrawContext context;

    public HudRenderEvent(DrawContext context) {
        this.context = context;
    }

    public DrawContext context() {
        return context;
    }
}
