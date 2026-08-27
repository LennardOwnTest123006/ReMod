package dev.remod.api.service;

import java.util.Optional;

/**
 * The service locator the loader populates and the API reads.
 *
 * <p>Mods should not normally touch this: everything here is reachable through
 * {@link dev.remod.api.ReModContext}. It is public because version adapters and
 * compatibility bridges, which live outside the API module, need to install
 * themselves.</p>
 */
public final class ReModServices {

    private static volatile GameBridge gameBridge;

    private ReModServices() {
    }

    /** The active bridge, empty before the loader has bound one. */
    public static Optional<GameBridge> gameBridge() {
        return Optional.ofNullable(gameBridge);
    }

    /**
     * Installs the bridge. Called once by the loader during startup.
     *
     * @throws IllegalStateException if a bridge is already installed, which
     *         would mean two adapters both claimed this Minecraft version
     */
    public static synchronized void installGameBridge(GameBridge bridge) {
        if (gameBridge != null && bridge != null) {
            throw new IllegalStateException("A game bridge is already installed ("
                    + gameBridge.id() + "); refusing to replace it with " + bridge.id());
        }
        gameBridge = bridge;
    }

    /** Removes the installed bridge. Called on shutdown and by tests. */
    public static synchronized void clear() {
        gameBridge = null;
    }
}
