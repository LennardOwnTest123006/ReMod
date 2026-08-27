package dev.remod.api.client.gui;

/**
 * Something drawn on top of the game each frame.
 *
 * <p>Layers are drawn in registration order after the vanilla HUD. Keep the
 * body cheap: this runs every frame, so allocation or string formatting here
 * shows up directly in the player's frame rate.</p>
 */
@FunctionalInterface
public interface HudLayer {

    void render(DrawContext context);

    /** Return false to skip drawing this frame. */
    default boolean isVisible() {
        return true;
    }
}
