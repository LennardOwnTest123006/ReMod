package dev.remod.api.client;

import dev.remod.api.client.gui.HudRegistry;
import dev.remod.api.game.PlayerHandle;
import dev.remod.api.game.Text;
import dev.remod.api.game.WorldHandle;

import java.util.Optional;

/**
 * Client-only functionality.
 *
 * <p>Reached through {@link dev.remod.api.ReModContext#client()}, which is
 * empty on a dedicated server. That is why a common mod can call it safely: no
 * client-only class is loaded unless the {@code Optional} is present.</p>
 */
public interface ClientApi {

    /** Keybind registration. */
    KeybindRegistry keybinds();

    /** HUD layer registration. */
    HudRegistry hud();

    /** The local player, empty while on the title screen. */
    Optional<PlayerHandle> player();

    /** The world the local player is in, empty while on the title screen. */
    Optional<WorldHandle> world();

    /** Prints a message to the local chat log. Nothing is sent to the server. */
    void sendChatMessage(Text message);

    /** Shows a message above the hotbar. */
    void sendActionBar(Text message);

    /** The current frame rate as the client reports it. */
    int framesPerSecond();

    /** Runs {@code task} on the client thread. Safe to call from any thread. */
    void execute(Runnable task);
}
