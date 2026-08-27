package dev.remod.api.client.gui;

import dev.remod.api.game.Text;

/**
 * The drawing surface handed to a {@link HudLayer}.
 *
 * <p>A small, version-stable subset of Minecraft's rendering: text, rectangles
 * and textures positioned in scaled screen coordinates. Minecraft's rendering
 * internals have been rewritten several times (immediate mode, then
 * {@code RenderSystem}, then the 1.21 render-pipeline work); routing mods
 * through this interface is what stops each rewrite from breaking every HUD
 * mod.</p>
 */
public interface DrawContext {

    /** Screen width in scaled (GUI) pixels. */
    int screenWidth();

    /** Screen height in scaled (GUI) pixels. */
    int screenHeight();

    /** Fraction of a tick elapsed, for smooth animation. */
    float partialTick();

    /** Draws text with a drop shadow, as vanilla HUD text does. */
    void drawText(Text text, int x, int y, int argbColor);

    /** Draws text without a shadow. */
    void drawTextPlain(Text text, int x, int y, int argbColor);

    /** Draws text centred horizontally on {@code centerX}. */
    void drawCenteredText(Text text, int centerX, int y, int argbColor);

    /** Fills a rectangle with a solid ARGB colour. */
    void fill(int x, int y, int width, int height, int argbColor);

    /** Draws a one-pixel outline. */
    void drawBorder(int x, int y, int width, int height, int argbColor);

    /** The rendered width of {@code text} in scaled pixels. */
    int textWidth(Text text);

    /** The line height of the font in scaled pixels. */
    int lineHeight();
}
