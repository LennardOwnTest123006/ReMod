package dev.remod.loader.adapter;

import dev.remod.api.Side;
import dev.remod.api.service.GameBridge;

/**
 * Support for one family of Minecraft versions.
 *
 * <p>Minecraft's internals change constantly -- the registry API was rewritten
 * for 1.19.3, item properties moved into data components in 1.20.5, and the
 * rendering layer has been reworked more than once. ReMod therefore never
 * assumes one implementation fits every release. Instead each adapter declares
 * which versions it handles and produces a {@link GameBridge} that speaks to
 * that family.</p>
 *
 * <p>Adapters are discovered through {@link java.util.ServiceLoader}, so a new
 * Minecraft release is supported by dropping in a new adapter jar -- no change
 * to the loader, the API, or any mod.</p>
 *
 * <p>Register an implementation by listing it in
 * {@code META-INF/services/dev.remod.loader.adapter.MinecraftVersionAdapter}.</p>
 */
public interface MinecraftVersionAdapter {

    /** A stable id, e.g. {@code remod:generic-1.21}. */
    String id();

    /** A human-readable name for the installer's UI. */
    String displayName();

    /**
     * How well this adapter handles {@code minecraftVersion}.
     *
     * <p>The highest-scoring adapter wins. Returning
     * {@link Support#UNSUPPORTED} is the honest answer for a version this
     * adapter cannot handle, and is far better than a bridge that silently
     * does nothing.</p>
     */
    Support supportFor(String minecraftVersion);

    /**
     * Creates the bridge for a running game.
     *
     * @param minecraftVersion the version actually running
     * @param side             the side this process runs as
     * @param gameClassLoader  the loader holding Minecraft's own classes, or
     *                         {@code null} when no game is present
     */
    GameBridge createBridge(String minecraftVersion, Side side, ClassLoader gameClassLoader);

    /** How completely an adapter supports a version. */
    enum Support {

        /** Cannot handle this version at all. */
        UNSUPPORTED(0),

        /**
         * Handles it, but some capabilities are missing. ReMod loads mods and
         * reports which features are unavailable.
         */
        PARTIAL(1),

        /** Written for this version family and expected to work. */
        FULL(2),

        /** Written for this exact version. Beats a family match. */
        EXACT(3);

        private final int rank;

        Support(int rank) {
            this.rank = rank;
        }

        public int rank() {
            return rank;
        }

        public boolean isUsable() {
            return this != UNSUPPORTED;
        }
    }
}
