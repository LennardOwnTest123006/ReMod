package dev.remod.transform;

import dev.remod.api.command.CommandSpec;
import dev.remod.common.log.ReModLog;
import dev.remod.common.log.ReModLogger;
import dev.remod.transform.hook.BrigadierCommandBridge;
import dev.remod.transform.hook.CommandDispatcherTransformer;
import dev.remod.transform.hook.ReModHooks;
import dev.remod.transform.load.TransformingClassLoader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The one object that owns ReMod's connection to a running Minecraft.
 *
 * <p>Set up before the game starts, it installs the transforming class loader
 * and the hooks, then holds the commands mods register until Minecraft's
 * dispatcher appears -- which happens partway through the game's own startup,
 * long after mods have finished initialising.</p>
 *
 * <p>That ordering is the whole reason this class buffers: a mod registers
 * {@code /fly} during {@code INIT}, and the dispatcher it has to go into does
 * not exist yet. Queueing and flushing on the hook is what closes the gap.</p>
 */
public final class GameIntegration {

    private static final ReModLogger LOG = ReModLog.get("ReMod/Integration");

    private final TransformingClassLoader classLoader;
    private final BrigadierCommandBridge commandBridge;

    /** Commands registered before the dispatcher existed, in registration order. */
    private final Map<String, Pending> pending = new LinkedHashMap<>();
    private volatile Object dispatcher;
    private volatile boolean attached;

    /**
     * The instance the launch wrapper installed.
     *
     * <p>A static handle because the version adapter is created later, by a
     * different part of ReMod, and has no other way to reach it.</p>
     */
    private static volatile GameIntegration installed;

    private GameIntegration(TransformingClassLoader classLoader,
                            BrigadierCommandBridge commandBridge) {
        this.classLoader = classLoader;
        this.commandBridge = commandBridge;
    }

    /**
     * Installs the transforming loader over the JVM's classpath and registers
     * ReMod's hooks.
     *
     * <p>Must be called before anything touches a Minecraft class: once the
     * parent loader has defined one, the transform can no longer reach it.</p>
     */
    public static GameIntegration install(ClassLoader parent) {
        TransformingClassLoader loader = TransformingClassLoader.overSystemClasspath(parent)
                .register(new CommandDispatcherTransformer());
        GameIntegration integration =
                new GameIntegration(loader, new BrigadierCommandBridge(parent));
        ReModHooks.onCommandDispatcherAvailable(integration::onDispatcher);
        installed = integration;
        LOG.info("Game integration installed; Minecraft will be loaded through ReMod's"
                + " transforming class loader");
        return integration;
    }

    /** The integration the launch wrapper installed, or {@code null} when none was. */
    public static GameIntegration installed() {
        return installed;
    }

    /** Clears the installed instance. Used by tests and on shutdown. */
    public static void clearInstalled() {
        installed = null;
    }

    /** The loader Minecraft's own classes must be loaded through. */
    public TransformingClassLoader classLoader() {
        return classLoader;
    }

    /** True once Minecraft's command dispatcher has been handed over. */
    public boolean isAttached() {
        return attached;
    }

    /**
     * Queues a command for registration with the game.
     *
     * <p>Registers immediately when the dispatcher already exists, so a command
     * added after startup -- from a reload, say -- is not left waiting.</p>
     *
     * @return true when the command was registered with the game there and then
     */
    public synchronized boolean registerCommand(CommandSpec command, String ownerModId,
                                                Supplier<CommandExecutorAdapter> executor) {
        if (command == null) {
            return false;
        }
        Pending entry = new Pending(command, ownerModId, executor);
        pending.put(command.name(), entry);
        Object active = dispatcher;
        if (active == null) {
            LOG.debug(() -> "/" + command.name() + " from " + ownerModId
                    + " is queued until Minecraft's command dispatcher exists");
            return false;
        }
        return registerNow(active, entry);
    }

    /** Called from the injected hook once the game has built its dispatcher. */
    private synchronized void onDispatcher(Object active) {
        this.dispatcher = active;
        this.attached = true;
        if (!commandBridge.isAvailable()) {
            LOG.error("Minecraft handed over a command dispatcher but Brigadier is not"
                    + " reachable from ReMod's class loader; commands cannot be registered");
            return;
        }
        List<Pending> queued = new ArrayList<>(pending.values());
        if (queued.isEmpty()) {
            LOG.debug(() -> "No ReMod commands to register");
            return;
        }
        int registered = 0;
        for (Pending entry : queued) {
            if (registerNow(active, entry)) {
                registered++;
            }
        }
        LOG.info("Registered " + registered + " of " + queued.size()
                + " ReMod command(s) with Minecraft");
    }

    private boolean registerNow(Object active, Pending entry) {
        return commandBridge.register(active, entry.command,
                (command, context) -> entry.executor.get().execute(command, context));
    }

    /** The commands ReMod has queued or registered, for diagnostics. */
    public synchronized List<String> registeredCommandNames() {
        return new ArrayList<>(pending.keySet());
    }

    /** Runs a mod's command body against Minecraft's own command context. */
    @FunctionalInterface
    public interface CommandExecutorAdapter {

        /**
         * @param command          the registered spec
         * @param brigadierContext Brigadier's {@code CommandContext}
         * @return the command's result count
         */
        int execute(CommandSpec command, Object brigadierContext);
    }

    private static final class Pending {

        private final CommandSpec command;
        private final String ownerModId;
        private final Supplier<CommandExecutorAdapter> executor;

        Pending(CommandSpec command, String ownerModId,
                Supplier<CommandExecutorAdapter> executor) {
            this.command = command;
            this.ownerModId = ownerModId;
            this.executor = executor;
        }
    }
}
