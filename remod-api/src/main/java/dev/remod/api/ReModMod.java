package dev.remod.api;

/**
 * The interface every ReMod mod's entrypoint implements.
 *
 * <p>A minimal mod is one class and one manifest:</p>
 *
 * <pre>{@code
 * public final class HelloMod implements ReModMod {
 *     @Override
 *     public void onInitialize(ReModContext context) {
 *         context.logger().info("Hello from " + context.modId());
 *     }
 * }
 * }</pre>
 *
 * <p>{@link #onInitialize} is called during {@link LifecyclePhase#INIT}. The
 * other methods are optional hooks with no-op defaults, so a mod only
 * overrides the phases it actually needs.</p>
 *
 * <p>The instance is created once, by ReMod, using the class's public
 * no-argument constructor.</p>
 */
public interface ReModMod {

    /**
     * The main phase. Register items, blocks, commands and event listeners here.
     *
     * @param context this mod's handle on everything ReMod offers
     */
    void onInitialize(ReModContext context);

    /** Called before any mod initialises. Read configuration here. */
    default void onPreInitialize(ReModContext context) {
    }

    /** Called once every mod has finished {@link #onInitialize}. */
    default void onPostInitialize(ReModContext context) {
    }

    /** Client-only setup. Never called on a dedicated server. */
    default void onClientInitialize(ReModContext context) {
    }

    /** Dedicated-server-only setup. Never called on the client. */
    default void onServerInitialize(ReModContext context) {
    }

    /** Called as the game shuts down. Close files, stop executors, save state. */
    default void onShutdown(ReModContext context) {
    }
}
