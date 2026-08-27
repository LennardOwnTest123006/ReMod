package dev.remod.loader.runtime;

import dev.remod.api.LifecyclePhase;
import dev.remod.api.ReModContext;
import dev.remod.api.Side;
import dev.remod.api.client.ClientApi;
import dev.remod.api.command.CommandRegistry;
import dev.remod.api.config.Config;
import dev.remod.api.event.EventBus;
import dev.remod.api.game.GameInfo;
import dev.remod.api.mod.ModMetadata;
import dev.remod.api.network.NetworkApi;
import dev.remod.api.registry.Registries;
import dev.remod.api.resource.ResourceLoader;
import dev.remod.api.service.GameBridge;
import dev.remod.common.log.ReModLog;
import dev.remod.common.log.ReModLogger;
import dev.remod.common.version.ApiVersion;
import dev.remod.loader.ReModVersions;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** The per-mod {@link ReModContext} the loader hands to each entrypoint. */
public final class DefaultReModContext implements ReModContext {

    private final ModMetadata metadata;
    private final ReModLogger logger;
    private final ModEventBus events;
    private final Registries registries;
    private final CommandRegistry commands;
    private final NetworkApi network;
    private final ResourceLoader resources;
    private final Config config;
    private final Path dataDirectory;
    private final Path gameDirectory;
    private final GameInfo game;
    private final ApiVersion apiVersion;
    private final Side side;
    private final Supplier<GameBridge> bridge;
    private final Map<String, ModMetadata> loadedMods;
    private volatile LifecyclePhase phase = LifecyclePhase.PRE_INIT;

    public DefaultReModContext(ModMetadata metadata, ModEventBus events, Registries registries,
                               CommandRegistry commands, NetworkApi network,
                               ResourceLoader resources, Config config, Path dataDirectory,
                               Path gameDirectory, GameInfo game, ApiVersion apiVersion, Side side,
                               Supplier<GameBridge> bridge, Map<String, ModMetadata> loadedMods) {
        this.metadata = metadata;
        this.logger = ReModLog.get(metadata.id());
        this.events = events;
        this.registries = registries;
        this.commands = commands;
        this.network = network;
        this.resources = resources;
        this.config = config;
        this.dataDirectory = dataDirectory;
        this.gameDirectory = gameDirectory;
        this.game = game;
        this.apiVersion = apiVersion;
        this.side = side;
        this.bridge = bridge;
        this.loadedMods = loadedMods;
    }

    @Override
    public String modId() {
        return metadata.id();
    }

    @Override
    public String modName() {
        return metadata.name();
    }

    @Override
    public String modVersion() {
        return metadata.version().raw();
    }

    @Override
    public ReModLogger logger() {
        return logger;
    }

    @Override
    public LifecyclePhase phase() {
        return phase;
    }

    /** Updated by the loader as it moves between phases. */
    public void phase(LifecyclePhase value) {
        this.phase = value;
    }

    @Override
    public Side side() {
        return side;
    }

    @Override
    public GameInfo game() {
        return game;
    }

    @Override
    public ApiVersion apiVersion() {
        return apiVersion;
    }

    @Override
    public String loaderVersion() {
        return ReModVersions.loaderVersion();
    }

    @Override
    public EventBus events() {
        return events;
    }

    /** The concrete bus, so the loader can detach this mod's listeners. */
    public ModEventBus modEvents() {
        return events;
    }

    @Override
    public Registries registries() {
        return registries;
    }

    @Override
    public CommandRegistry commands() {
        return commands;
    }

    @Override
    public NetworkApi network() {
        return network;
    }

    @Override
    public ResourceLoader resources() {
        return resources;
    }

    @Override
    public Config config() {
        return config;
    }

    @Override
    public Path dataDirectory() {
        return dataDirectory;
    }

    @Override
    public Path gameDirectory() {
        return gameDirectory;
    }

    @Override
    public Optional<ClientApi> client() {
        if (side == Side.DEDICATED_SERVER) {
            return Optional.empty();
        }
        GameBridge active = bridge.get();
        return active == null ? Optional.empty() : active.client();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return loadedMods.containsKey(modId);
    }

    @Override
    public Optional<String> modVersion(String modId) {
        ModMetadata other = loadedMods.get(modId);
        return Optional.ofNullable(other == null ? null : other.version().raw());
    }

    /** The metadata this context was built from. */
    public ModMetadata metadata() {
        return metadata;
    }
}
