package dev.remod.loader.launch;

import dev.remod.api.Side;
import dev.remod.api.event.lifecycle.GameStartingEvent;
import dev.remod.api.event.lifecycle.GameStoppingEvent;
import dev.remod.api.service.GameBridge;
import dev.remod.common.log.LogLevel;
import dev.remod.common.log.ReModLog;
import dev.remod.common.log.ReModLogger;
import dev.remod.loader.LoadReport;
import dev.remod.loader.ReModLoader;
import dev.remod.loader.ReModPaths;
import dev.remod.loader.ReModVersions;
import dev.remod.loader.adapter.AdapterRegistry;
import dev.remod.loader.adapter.MinecraftVersionAdapter;
import dev.remod.loader.runtime.HeadlessGameBridge;

import java.util.Optional;

/**
 * ReMod's launch wrapper -- the {@code mainClass} in the generated Minecraft
 * version JSON.
 *
 * <p>The Minecraft launcher starts this class instead of the game. It:</p>
 *
 * <ol>
 *   <li>reads the launcher's arguments to find the game directory and version;</li>
 *   <li>starts logging into {@code <game>/remod/logs/};</li>
 *   <li>locates Minecraft's own entry point, which also tells it which side is
 *       starting;</li>
 *   <li>selects a version adapter and builds the game bridge;</li>
 *   <li>discovers, resolves and initialises mods;</li>
 *   <li>hands the original arguments to Minecraft's {@code main}.</li>
 * </ol>
 *
 * <p>Step 6 is the important one: ReMod does not replace or re-implement
 * Minecraft's startup, and it never modifies the vanilla jar. It runs first,
 * then gets out of the way.</p>
 */
public final class ReModLaunch {

    private static final ReModLogger LOG = ReModLog.get("ReMod");

    private ReModLaunch() {
    }

    public static void main(String[] args) {
        LaunchArguments arguments = LaunchArguments.parse(args);
        ReModPaths paths = new ReModPaths(arguments.gameDirectory());
        String minecraftVersion = arguments.minecraftVersion();

        configureLogging(paths);
        LOG.info("ReMod " + ReModVersions.loaderVersion() + " starting");
        LOG.info("Game directory: " + paths.gameDirectory());

        // Install the transformation layer, when this build has one, BEFORE
        // any Minecraft class is touched: once the application loader has
        // defined one, no transform can reach it any more.
        ClassLoader gameLoader = GameIntegrationSupport.installIfPresent(
                ReModLaunch.class.getClassLoader());
        GameLocator game = GameLocator.locate(gameLoader);
        Side side = game != null ? game.side() : Side.COMMON;
        if (game == null) {
            LOG.warn("Minecraft was not found on the classpath. ReMod will initialise mods but"
                    + " cannot start the game. If you launched ReMod.jar directly, use the"
                    + " installer instead; ReMod is started by the Minecraft launcher.");
        }

        ReModLoader loader = new ReModLoader(paths, minecraftVersion, side);
        loader.installBridge(selectBridge(minecraftVersion, side));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            loader.events().post(new GameStoppingEvent(false));
            loader.shutdown();
        }, "ReMod-Shutdown"));

        LoadReport report = loader.load();
        if (!report.errors().isEmpty()) {
            LOG.warn(report.errors().size() + " mod(s) were not loaded. The game will start"
                    + " without them; see the errors above.");
        }

        loader.events().post(new GameStartingEvent(minecraftVersion));

        if (game == null) {
            LOG.info("No game to start; exiting after mod initialisation.");
            ReModLog.flush();
            return;
        }
        try {
            game.launch(arguments.raw());
        } catch (LaunchException e) {
            LOG.error("ReMod could not start Minecraft: " + e.getMessage()
                    + System.lineSeparator() + "  " + e.suggestion(), e.getCause());
            ReModLog.flush();
            System.exit(1);
        }
    }

    /** Builds the bridge for this version, falling back to headless. */
    private static GameBridge selectBridge(String minecraftVersion, Side side) {
        AdapterRegistry registry = AdapterRegistry.discover(ReModLaunch.class.getClassLoader());
        Optional<MinecraftVersionAdapter> adapter = registry.select(minecraftVersion);
        if (adapter.isEmpty()) {
            LOG.warn("No version adapter claims Minecraft " + minecraftVersion + ". Mods will"
                    + " initialise but will not be able to affect the game. See"
                    + " docs/version-support.md for which versions are supported.");
            return new HeadlessGameBridge(minecraftVersion, side);
        }
        MinecraftVersionAdapter selected = adapter.get();
        MinecraftVersionAdapter.Support support = selected.supportFor(minecraftVersion);
        if (support == MinecraftVersionAdapter.Support.PARTIAL) {
            LOG.warn("Adapter " + selected.id() + " only partially supports Minecraft "
                    + minecraftVersion + "; some mod features will be unavailable.");
        }
        try {
            GameBridge bridge = selected.createBridge(minecraftVersion, side,
                    ReModLaunch.class.getClassLoader());
            if (bridge == null) {
                throw new IllegalStateException("createBridge returned null");
            }
            return bridge;
        } catch (RuntimeException e) {
            LOG.error("Adapter " + selected.id() + " failed to attach to Minecraft "
                    + minecraftVersion + "; falling back to no game integration", e);
            return new HeadlessGameBridge(minecraftVersion, side);
        }
    }

    private static void configureLogging(ReModPaths paths) {
        String level = System.getProperty("remod.logLevel");
        ReModLog.setLevel(LogLevel.parse(level, LogLevel.INFO));
        try {
            paths.createDirectories();
            ReModLog.addFileSink(paths.logsDirectory(), "remod.log");
        } catch (Exception e) {
            // Console logging still works; say so rather than failing to start.
            System.err.println("[ReMod] Could not open a log file under "
                    + paths.logsDirectory() + " (" + e.getMessage()
                    + "); logging to the console only.");
        }
    }
}
