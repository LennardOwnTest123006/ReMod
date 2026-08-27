package dev.remod.transform.hook;

import dev.remod.common.log.ReModLog;
import dev.remod.common.log.ReModLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The landing point for code injected into Minecraft.
 *
 * <p>Transformed game classes call straight into this class, so every method
 * here is public and static and must stay that way: the injected bytecode names
 * them literally, and renaming one silently breaks a hook that no compiler will
 * complain about.</p>
 *
 * <p>This class is loaded by ReMod's own class loader, not the game's, which is
 * exactly why {@code dev.remod.} is on the parent-first list in
 * {@link dev.remod.transform.load.TransformingClassLoader}. If the game loaded
 * its own copy, the injected call would reach a different class than the one
 * ReMod is listening on and the hooks would appear to do nothing.</p>
 *
 * <p>Everything here is defensive: a hook fires from inside the game's own
 * startup, so an exception escaping it would crash the game rather than the
 * mod. Listener failures are caught, logged and swallowed.</p>
 */
public final class ReModHooks {

    private static final ReModLogger LOG = ReModLog.get("ReMod/Hooks");

    private static final List<Consumer<Object>> DISPATCHER_LISTENERS =
            new CopyOnWriteArrayList<>();

    /** The most recent dispatcher, for listeners registered after the game started. */
    private static volatile Object commandDispatcher;

    private ReModHooks() {
    }

    /**
     * Called from Minecraft's command class once it has built its dispatcher.
     *
     * <p><b>Injected bytecode calls this by name.</b> Signature and name are
     * part of the contract with {@link CommandDispatcherTransformer}.</p>
     *
     * @param dispatcher the game's {@code com.mojang.brigadier.CommandDispatcher},
     *                   passed as {@link Object} so this module needs no
     *                   compile-time dependency on Brigadier
     */
    public static void onCommandDispatcher(Object dispatcher) {
        if (dispatcher == null) {
            return;
        }
        commandDispatcher = dispatcher;
        LOG.info("Minecraft's command dispatcher is available; registering ReMod commands");
        for (Consumer<Object> listener : DISPATCHER_LISTENERS) {
            notifyOne(listener, dispatcher);
        }
    }

    /**
     * Registers a listener for the game's command dispatcher.
     *
     * <p>Fires immediately when the dispatcher already exists, so a listener
     * registered late is not left waiting for an event that has been and
     * gone.</p>
     */
    public static void onCommandDispatcherAvailable(Consumer<Object> listener) {
        if (listener == null) {
            return;
        }
        DISPATCHER_LISTENERS.add(listener);
        Object existing = commandDispatcher;
        if (existing != null) {
            notifyOne(listener, existing);
        }
    }

    private static void notifyOne(Consumer<Object> listener, Object dispatcher) {
        try {
            listener.accept(dispatcher);
        } catch (Throwable failure) {
            // Thrown from inside the game's own startup: swallowing is the only
            // option that does not take the game down with the listener.
            LOG.error("A command-dispatcher listener failed; continuing without it", failure);
        }
    }

    /** The game's command dispatcher, or {@code null} before it exists. */
    public static Object commandDispatcher() {
        return commandDispatcher;
    }

    /** True once a hook has reported a dispatcher. */
    public static boolean isCommandDispatcherAvailable() {
        return commandDispatcher != null;
    }

    /** Clears all state. Used by tests and on shutdown. */
    public static void reset() {
        DISPATCHER_LISTENERS.clear();
        commandDispatcher = null;
    }

    /** The listeners currently registered, for diagnostics. */
    public static List<Consumer<Object>> dispatcherListeners() {
        return new ArrayList<>(DISPATCHER_LISTENERS);
    }
}
