package dev.remod.loader;

import dev.remod.api.LifecyclePhase;
import dev.remod.api.ReModMod;
import dev.remod.api.Side;
import dev.remod.api.event.lifecycle.ModsLoadedEvent;
import dev.remod.api.mod.ModMetadata;
import dev.remod.api.service.GameBridge;
import dev.remod.api.service.ReModServices;
import dev.remod.common.log.ReModLog;
import dev.remod.common.log.ReModLogger;
import dev.remod.common.version.ApiVersion;
import dev.remod.loader.discovery.DiscoveryResult;
import dev.remod.loader.discovery.ModCandidate;
import dev.remod.loader.discovery.ModDiscovery;
import dev.remod.loader.resolve.ModLoadError;
import dev.remod.loader.resolve.ModResolver;
import dev.remod.loader.resolve.ResolutionResult;
import dev.remod.loader.runtime.ArchiveResourceLoader;
import dev.remod.loader.runtime.DefaultCommandRegistry;
import dev.remod.loader.runtime.DefaultEventBus;
import dev.remod.loader.runtime.DefaultGameInfo;
import dev.remod.loader.runtime.DefaultNetworkApi;
import dev.remod.loader.runtime.DefaultReModContext;
import dev.remod.loader.runtime.DefaultRegistries;
import dev.remod.loader.runtime.EntrypointFactory;
import dev.remod.loader.runtime.HeadlessGameBridge;
import dev.remod.loader.runtime.JsonConfig;
import dev.remod.loader.runtime.ModClassLoader;
import dev.remod.loader.runtime.ModContainer;
import dev.remod.loader.runtime.ModEventBus;
import dev.remod.loader.runtime.ModInstantiationException;
import dev.remod.loader.discovery.ModSourceKind;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Discovers, resolves and runs mods.
 *
 * <p>The loader owns the whole sequence:</p>
 *
 * <pre>
 *   scan mods folder  -&gt;  resolve compatibility and order  -&gt;  construct entrypoints
 *   -&gt;  PRE_INIT  -&gt;  INIT  -&gt;  POST_INIT  -&gt;  CLIENT_INIT / SERVER_INIT
 * </pre>
 *
 * <p>A mod that throws in any phase is marked failed, has its registrations and
 * listeners removed, and is skipped in later phases. Every other mod continues.
 * This is the difference between "one bad mod" and "the game will not start".</p>
 */
public final class ReModLoader {

    private static final ReModLogger LOG = ReModLog.get("ReMod");

    private final ReModPaths paths;
    private final String minecraftVersion;
    private final Side side;
    private final ApiVersion apiVersion;

    private final DefaultEventBus eventBus = new DefaultEventBus();
    private final DefaultRegistries registries;
    private final DefaultCommandRegistry commands;
    private final Map<String, ModContainer> containers = new LinkedHashMap<>();
    private final Map<String, ModMetadata> loadedMetadata = new LinkedHashMap<>();

    private volatile GameBridge bridge;
    private volatile String currentOwner = "remod";
    private ModClassLoader classLoader;
    private LoadReport report;

    public ReModLoader(ReModPaths paths, String minecraftVersion, Side side) {
        this.paths = paths;
        this.minecraftVersion = minecraftVersion;
        this.side = side == null ? Side.COMMON : side;
        ApiVersion resolved = ReModVersions.apiVersionFor(minecraftVersion);
        // A weekly snapshot has no derivable series; fall back to the raw id so
        // mods declaring that exact snapshot can still be matched.
        this.apiVersion = resolved != null ? resolved
                : ApiVersion.of(minecraftVersion, ReModVersions.apiBaseline());
        this.registries = new DefaultRegistries(() -> currentOwner, () -> bridge);
        this.commands = new DefaultCommandRegistry(() -> currentOwner, () -> bridge);
    }

    /**
     * Installs the bridge to the running game.
     *
     * <p>Called by the launcher once a version adapter has been selected. When
     * none is installed, a {@link HeadlessGameBridge} is used and mods are told
     * honestly that no game is attached.</p>
     */
    public void installBridge(GameBridge gameBridge) {
        this.bridge = gameBridge;
        ReModServices.clear();
        ReModServices.installGameBridge(gameBridge);
    }

    public GameBridge bridge() {
        return bridge;
    }

    public ReModPaths paths() {
        return paths;
    }

    public ApiVersion apiVersion() {
        return apiVersion;
    }

    public String minecraftVersion() {
        return minecraftVersion;
    }

    public Side side() {
        return side;
    }

    public DefaultEventBus events() {
        return eventBus;
    }

    public DefaultRegistries registries() {
        return registries;
    }

    public DefaultCommandRegistry commands() {
        return commands;
    }

    /** The mods that loaded, in load order. */
    public List<ModContainer> containers() {
        return Collections.unmodifiableList(new ArrayList<>(containers.values()));
    }

    public Optional<ModContainer> container(String modId) {
        return Optional.ofNullable(containers.get(modId));
    }

    /** The report from the last {@link #load()}, or {@code null} before one. */
    public LoadReport report() {
        return report;
    }

    /**
     * Runs the full load sequence.
     *
     * <p>Never throws for a mod-caused failure; those become entries in the
     * returned report.</p>
     */
    public LoadReport load() {
        long started = System.currentTimeMillis();
        if (bridge == null) {
            installBridge(new HeadlessGameBridge(minecraftVersion, side));
            LOG.warn("No version adapter is bound; mods will load but nothing will reach the"
                    + " game. This is expected under 'remod test'.");
        }

        LOG.info("Starting ReMod " + ReModVersions.loaderVersion());
        LOG.info("Minecraft version: " + minecraftVersion + " (" + side.token() + ")");
        LOG.info("ReMod API: " + apiVersion + " via adapter " + bridge.id());
        LOG.info("Mods directory: " + paths.modsDirectory());

        try {
            paths.createDirectories();
        } catch (IOException e) {
            LOG.error("Could not create ReMod's directories under " + paths.remodDirectory()
                    + ": " + e.getMessage() + ". Check the folder is writable.");
        }

        DiscoveryResult discovery = ModDiscovery.scan(paths.modsDirectory());
        reportDiscovery(discovery);

        ResolutionResult resolution =
                new ModResolver(minecraftVersion, apiVersion, side).resolve(discovery.candidates());
        List<ModLoadError> errors = new ArrayList<>(resolution.errors());
        for (ModLoadError error : resolution.errors()) {
            LOG.error(System.lineSeparator() + error.report());
        }
        for (String warning : resolution.warnings()) {
            LOG.warn(warning);
        }

        classLoader = ModClassLoader.forMods(resolution.loadOrder(),
                ReModLoader.class.getClassLoader());
        construct(resolution.loadOrder(), errors);

        runPhase(LifecyclePhase.PRE_INIT, errors);
        registries.reopen();
        runPhase(LifecyclePhase.INIT, errors);
        runPhase(LifecyclePhase.POST_INIT, errors);
        registries.close();
        if (side == Side.CLIENT) {
            runPhase(LifecyclePhase.CLIENT_INIT, errors);
        } else if (side == Side.DEDICATED_SERVER) {
            runPhase(LifecyclePhase.SERVER_INIT, errors);
        }

        List<ModContainer> active = new ArrayList<>();
        List<String> activeIds = new ArrayList<>();
        for (ModContainer container : containers.values()) {
            if (container.isActive()) {
                active.add(container);
                activeIds.add(container.id());
            }
        }
        eventBus.post(new ModsLoadedEvent(activeIds));

        long duration = System.currentTimeMillis() - started;
        LOG.info("Loaded " + active.size() + " mod" + (active.size() == 1 ? "" : "s")
                + " in " + duration + " ms"
                + (errors.isEmpty() ? "" : " (" + errors.size() + " rejected)"));
        if (!active.isEmpty()) {
            for (ModContainer container : active) {
                LOG.info("  - " + container.metadata().name() + " "
                        + container.metadata().version().raw() + " (" + container.id() + ")");
            }
        }
        LOG.info("ReMod startup completed");

        report = new LoadReport(active, errors, resolution.warnings(),
                discovery.problems(), discovery.foreignMods(), duration);
        return report;
    }

    private void reportDiscovery(DiscoveryResult discovery) {
        LOG.info("Found " + discovery.candidates().size() + " ReMod mod"
                + (discovery.candidates().size() == 1 ? "" : "s"));
        for (dev.remod.loader.discovery.DiscoveryProblem problem : discovery.problems()) {
            LOG.warn("Skipping " + problem.summary() + " -- " + problem.suggestion());
        }
        for (DiscoveryResult.ForeignMod foreign : discovery.foreignMods()) {
            LOG.warn("Skipping " + foreign.fileName() + ": this is a " + foreign.loaderName()
                    + " mod, not a ReMod mod. ReMod cannot load it directly. Move it to that"
                    + " loader's mods folder -- see docs/compatibility.md.");
        }
    }

    private void construct(List<ModCandidate> candidates, List<ModLoadError> errors) {
        for (ModCandidate candidate : candidates) {
            loadedMetadata.put(candidate.id(), candidate.metadata());
        }
        for (ModCandidate candidate : candidates) {
            ModMetadata metadata = candidate.metadata();
            ModEventBus modBus = new ModEventBus(eventBus, metadata.id());
            JsonConfig config = new JsonConfig(metadata.id(), paths.configFileFor(metadata.id()));
            config.load();
            DefaultReModContext context = new DefaultReModContext(
                    metadata,
                    modBus,
                    registries,
                    commands,
                    new DefaultNetworkApi(metadata.id(), () -> bridge),
                    new ArchiveResourceLoader(candidate.path(),
                            candidate.kind() == ModSourceKind.DIRECTORY),
                    config,
                    paths.dataDirectoryFor(metadata.id()),
                    paths.gameDirectory(),
                    new DefaultGameInfo(minecraftVersion, side, () -> bridge),
                    apiVersion,
                    side,
                    () -> bridge,
                    loadedMetadata);
            ModContainer container = new ModContainer(candidate, context);
            containers.put(metadata.id(), container);

            boolean constructed = true;
            for (String entrypoint : metadata.entrypoints()) {
                try {
                    ReModMod mod = EntrypointFactory.instantiate(entrypoint, classLoader,
                            metadata.id());
                    container.addEntrypoint(mod);
                } catch (ModInstantiationException e) {
                    constructed = false;
                    container.fail(e);
                    ModLoadError error = ModLoadError.builder(
                                    e.getCause() instanceof ClassNotFoundException
                                            ? ModLoadError.Reason.ENTRYPOINT_MISSING
                                            : ModLoadError.Reason.ENTRYPOINT_INVALID)
                            .mod(metadata.id(), metadata.name(), metadata.version().raw())
                            .file(candidate.fileName())
                            .detail(e.getMessage())
                            .solution(e.suggestion())
                            .cause(e.getCause())
                            .build();
                    errors.add(error);
                    LOG.error(System.lineSeparator() + error.report(), e.getCause());
                    break;
                }
            }
            if (constructed) {
                container.state(ModContainer.State.CONSTRUCTED);
            }
        }
    }

    /** Runs one lifecycle phase across every still-active mod. */
    private void runPhase(LifecyclePhase phase, List<ModLoadError> errors) {
        if (!phase.appliesTo(side)) {
            return;
        }
        LOG.debug(() -> "Running " + phase.label());
        for (ModContainer container : containers.values()) {
            if (!container.isActive() || container.entrypoints().isEmpty()) {
                continue;
            }
            container.context().phase(phase);
            currentOwner = container.id();
            try {
                for (ReModMod mod : container.entrypoints()) {
                    dispatch(phase, mod, container);
                }
                container.state(stateAfter(phase));
            } catch (Throwable failure) {
                failMod(container, phase, failure, errors);
            } finally {
                currentOwner = "remod";
            }
        }
    }

    private void dispatch(LifecyclePhase phase, ReModMod mod, ModContainer container) {
        switch (phase) {
            case PRE_INIT:     mod.onPreInitialize(container.context()); break;
            case INIT:         mod.onInitialize(container.context()); break;
            case POST_INIT:    mod.onPostInitialize(container.context()); break;
            case CLIENT_INIT:  mod.onClientInitialize(container.context()); break;
            case SERVER_INIT:  mod.onServerInitialize(container.context()); break;
            case SHUTDOWN:     mod.onShutdown(container.context()); break;
            default: break;
        }
    }

    private static ModContainer.State stateAfter(LifecyclePhase phase) {
        switch (phase) {
            case PRE_INIT:  return ModContainer.State.PRE_INITIALISED;
            case INIT:      return ModContainer.State.INITIALISED;
            case POST_INIT: return ModContainer.State.POST_INITIALISED;
            case SHUTDOWN:  return ModContainer.State.STOPPED;
            default:        return ModContainer.State.SIDE_INITIALISED;
        }
    }

    /**
     * Isolates a failed mod: its registrations, commands and listeners are
     * withdrawn so the rest of the game does not inherit a half-initialised mod.
     */
    private void failMod(ModContainer container, LifecyclePhase phase, Throwable failure,
                         List<ModLoadError> errors) {
        container.fail(failure);
        registries.removeAllOf(container.id());
        commands.removeAllOf(container.id());
        container.context().modEvents().unsubscribeAll();
        eventBus.unsubscribeAllOf(container.id());

        ModMetadata metadata = container.metadata();
        ModLoadError error = ModLoadError.builder(ModLoadError.Reason.INITIALISATION_FAILED)
                .mod(metadata.id(), metadata.name(), metadata.version().raw())
                .file(container.candidate().fileName())
                .detail("Threw " + failure.getClass().getSimpleName()
                        + " during " + phase.label()
                        + (failure.getMessage() == null ? "" : ": " + failure.getMessage()))
                .solution("This is a bug in " + metadata.name() + ", not in ReMod or your setup."
                        + (metadata.issues() == null
                                ? " Report it to the mod's author with the stack trace below."
                                : " Report it at " + metadata.issues() + "."))
                .solution("Remove " + container.candidate().fileName()
                        + " from the mods folder to start without it.")
                .cause(failure)
                .build();
        errors.add(error);
        LOG.error(System.lineSeparator() + error.report(), failure);

        // Mods that depended on this one cannot work either.
        for (ModContainer other : containers.values()) {
            if (!other.isActive() || other == container) {
                continue;
            }
            for (dev.remod.api.mod.ModDependency dependency :
                    other.metadata().dependencies(
                            dev.remod.api.mod.ModDependency.Kind.REQUIRED)) {
                if (dependency.modId().equals(container.id())) {
                    other.state(ModContainer.State.DISABLED);
                    errors.add(ModLoadError.builder(ModLoadError.Reason.DEPENDENCY_FAILED)
                            .mod(other.id(), other.metadata().name(),
                                    other.metadata().version().raw())
                            .file(other.candidate().fileName())
                            .detail("Depends on " + container.id() + ", which failed to load.")
                            .expected(container.id() + " loaded")
                            .found(container.id() + " failed during " + phase.label())
                            .solution("Fix or remove " + metadata.name() + " first.")
                            .build());
                    break;
                }
            }
        }
    }

    /** Runs the shutdown phase and releases resources. Safe to call twice. */
    public void shutdown() {
        LOG.info("Shutting down ReMod");
        currentOwner = "remod";
        for (ModContainer container : containers.values()) {
            if (container.state() == ModContainer.State.STOPPED || !container.isActive()) {
                continue;
            }
            currentOwner = container.id();
            try {
                for (ReModMod mod : container.entrypoints()) {
                    mod.onShutdown(container.context());
                }
                container.state(ModContainer.State.STOPPED);
            } catch (Throwable failure) {
                LOG.error("Mod '" + container.id() + "' threw during shutdown; continuing",
                        failure);
            } finally {
                currentOwner = "remod";
            }
        }
        eventBus.unsubscribeAll();
        ReModServices.clear();
        if (classLoader != null) {
            try {
                classLoader.close();
            } catch (IOException e) {
                LOG.debug(() -> "Could not close the mod class loader: " + e.getMessage());
            }
        }
        ReModLog.flush();
    }

    /** Applies {@code action} to each loaded mod's context, for adapters to drive events. */
    public void forEachContext(Consumer<DefaultReModContext> action) {
        for (ModContainer container : containers.values()) {
            if (container.isActive()) {
                action.accept(container.context());
            }
        }
    }
}
