package dev.remod.api.client;

/**
 * A key the player can press, rebindable from Minecraft's own Controls screen.
 *
 * <p>A keybind is queried, not subscribed to: check {@link #wasPressed()} once
 * per client tick. That mirrors how Minecraft itself consumes key state and
 * avoids the classic bug where a held key fires an action every frame.</p>
 *
 * <pre>{@code
 * Keybind toggle = client.keybinds().register("simplemod.toggle_hud", Key.H, "ReMod Simple Mod");
 * context.events().subscribe(ClientTickEvent.class, event -> {
 *     while (toggle.wasPressed()) {
 *         hudVisible = !hudVisible;
 *     }
 * });
 * }</pre>
 */
public interface Keybind {

    /** The unique id, also used as the translation key in the Controls screen. */
    String id();

    /** The category heading the binding appears under. */
    String category();

    /** The key this binding defaults to. */
    Key defaultKey();

    /** The key currently bound, which the player may have changed. */
    Key boundKey();

    /**
     * Consumes one queued press.
     *
     * @return true if a press was pending; call in a {@code while} loop to
     *         handle multiple presses in one tick
     */
    boolean wasPressed();

    /** True while the key is held down. */
    boolean isDown();
}
