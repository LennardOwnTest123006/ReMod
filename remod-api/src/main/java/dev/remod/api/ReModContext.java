package dev.remod.api;

import dev.remod.api.client.ClientApi;
import dev.remod.api.command.CommandRegistry;
import dev.remod.api.config.Config;
import dev.remod.api.event.EventBus;
import dev.remod.api.game.GameInfo;
import dev.remod.api.network.NetworkApi;
import dev.remod.api.registry.Registries;
import dev.remod.api.resource.ResourceLoader;
import dev.remod.common.log.ReModLogger;
import dev.remod.common.version.ApiVersion;

import java.nio.file.Path;
import java.util.Optional;

/**
 * A mod's handle on everything ReMod provides.
 *
 * <p>Each mod receives its own context. Anything registered through it is
 * attributed to that mod, which is what lets ReMod report "simplemod
 * registered 4 items" and unload or blame a single mod cleanly.</p>
 */
public interface ReModContext {

    /** This mod's id, as declared in its manifest. */
    String modId();

    /** This mod's display name. */
    String modName();

    /** This mod's version. */
    String modVersion();

    /** A logger whose channel is this mod's id. */
    ReModLogger logger();

    /** The phase currently executing. */
    LifecyclePhase phase();

    /** The side this process is running as. */
    Side side();

    /** The Minecraft version and other facts about the running game. */
    GameInfo game();

    /** The ReMod API version this mod is running against. */
    ApiVersion apiVersion();

    /** The ReMod loader version. */
    String loaderVersion();

    /** The event bus. Listeners registered here are removed when the mod unloads. */
    EventBus events();

    /** Content registries: items, blocks, creative tabs. */
    Registries registries();

    /** Command registration. */
    CommandRegistry commands();

    /** Networking between client and server. */
    NetworkApi network();

    /** Reads files packaged inside this mod's own archive. */
    ResourceLoader resources();

    /**
     * This mod's configuration file, created from defaults on first run.
     * Lives at {@code <game>/remod/config/<modid>.json}.
     */
    Config config();

    /** A private directory for this mod's own data: {@code <game>/remod/data/<modid>/}. */
    Path dataDirectory();

    /** The Minecraft game directory this process is running in. */
    Path gameDirectory();

    /**
     * Client-only functionality: keybinds, HUD layers, screens.
     *
     * @return empty on a dedicated server, so a common mod can degrade
     *         gracefully instead of crashing on a missing class
     */
    Optional<ClientApi> client();

    /** True when another mod with this id is loaded. */
    boolean isModLoaded(String modId);

    /** The version of another loaded mod, or empty when it is absent. */
    Optional<String> modVersion(String modId);
}
