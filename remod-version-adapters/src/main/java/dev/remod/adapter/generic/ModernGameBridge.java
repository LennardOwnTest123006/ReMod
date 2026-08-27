package dev.remod.adapter.generic;

import dev.remod.adapter.reflect.MinecraftReflection;
import dev.remod.api.Side;
import dev.remod.api.client.ClientApi;
import dev.remod.api.command.CommandSpec;
import dev.remod.api.game.Identifier;
import dev.remod.api.game.ServerHandle;
import dev.remod.api.registry.BlockDefinition;
import dev.remod.api.registry.CreativeTabDefinition;
import dev.remod.api.registry.ItemDefinition;
import dev.remod.api.registry.RegistryEntry;
import dev.remod.api.service.GameBridge;
import dev.remod.common.log.ReModLog;
import dev.remod.common.log.ReModLogger;
import dev.remod.loader.runtime.HeadlessClientApi;
import dev.remod.transform.GameIntegration;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * The bridge for modern Minecraft (1.17 and newer).
 *
 * <p>What this bridge does today, on every supported version:</p>
 *
 * <ul>
 *   <li>reports the running Minecraft version, side and adapter to mods;</li>
 *   <li>accepts every registration and command a mod makes, so ReMod's own
 *       registries are populated and mods can discover each other's content;</li>
 *   <li>provides the client API on the client side;</li>
 *   <li>runs a diagnostic probe for Minecraft's mapped class names and reports
 *       what it found.</li>
 * </ul>
 *
 * <p><b>What it does not do, stated plainly.</b> Content registered by a mod is
 * not inserted into Minecraft's own registries, and commands are not inserted
 * into Minecraft's command tree. {@link #capabilities()} therefore returns an
 * empty set, and every {@code bind*} method returns {@code false}. Mods are
 * told this through {@code context.game().isGameAttached()} and
 * {@code bridge.supports(...)} rather than discovering it as a silent no-op in
 * someone's world.</p>
 *
 * <p><b>Why.</b> Reaching Minecraft's registries means naming classes such as
 * {@code net.minecraft.core.registries.BuiltInRegistries}. Those names exist
 * only when the game runs against Mojang's official mappings; a stock launcher
 * install is obfuscated, where the same class is called something like
 * {@code fx}. On top of that, Minecraft freezes its registries at the end of
 * startup, so registration has to be injected <em>during</em> the game's own
 * bootstrap rather than called afterwards. Doing this properly needs a
 * remapping and bytecode-transformation layer; ReMod does not ship one yet, and
 * pretending otherwise would produce mods that appear to load and then do
 * nothing.</p>
 *
 * <p>{@link #mappedClassesVisible()} reports the probe's result, and the
 * roadmap for closing the gap is in {@code docs/version-support.md}.</p>
 */
public final class ModernGameBridge implements GameBridge {

    private static final ReModLogger LOG = ReModLog.get("ReMod/Bridge");

    /** Mojang-mapped names probed for as a diagnostic at attach time. */
    private static final String BUILT_IN_REGISTRIES =
            "net.minecraft.core.registries.BuiltInRegistries";
    private static final String REGISTRY = "net.minecraft.core.Registry";
    private static final String RESOURCE_LOCATION = "net.minecraft.resources.ResourceLocation";
    private static final String COMMAND_DISPATCHER = "com.mojang.brigadier.CommandDispatcher";

    private final String adapterId;
    private final String minecraftVersion;
    private final Side side;
    private final MinecraftReflection reflection;
    private final boolean gameAttached;
    private final boolean mappedClassesVisible;
    private final boolean brigadierVisible;
    private final ClientApi client;
    /** The transformation layer, when one was installed before the game started. */
    private final GameIntegration integration;

    public ModernGameBridge(String adapterId, String minecraftVersion, Side side,
                            ClassLoader gameClassLoader) {
        this(adapterId, minecraftVersion, side, gameClassLoader, null);
    }

    /**
     * @param integration the installed transformation layer, or {@code null}
     *                    when ReMod is running without one
     */
    public ModernGameBridge(String adapterId, String minecraftVersion, Side side,
                            ClassLoader gameClassLoader, GameIntegration integration) {
        this.integration = integration;
        this.adapterId = adapterId;
        this.minecraftVersion = minecraftVersion;
        this.side = side == null ? Side.COMMON : side;
        this.reflection = new MinecraftReflection(gameClassLoader);
        this.gameAttached = reflection.findClass("net.minecraft.client.main.Main").isPresent()
                || reflection.findClass("net.minecraft.server.Main").isPresent()
                || reflection.findClass("net.minecraft.server.MinecraftServer").isPresent();
        this.mappedClassesVisible =
                reflection.hasAll(BUILT_IN_REGISTRIES, REGISTRY, RESOURCE_LOCATION);
        this.brigadierVisible = reflection.findClass(COMMAND_DISPATCHER).isPresent();
        this.client = this.side == Side.DEDICATED_SERVER ? null : new HeadlessClientApi();
        describeAttachment();
    }

    /** Logs exactly one honest summary of what this bridge will and will not do. */
    private void describeAttachment() {
        if (!gameAttached) {
            LOG.info("Adapter " + adapterId + " loaded with no Minecraft process attached."
                    + " Mods will initialise and exchange events, but nothing will reach a game.");
            return;
        }
        LOG.info("Adapter " + adapterId + " attached to Minecraft " + minecraftVersion + ".");
        LOG.info("Mods load and receive the full ReMod lifecycle and event stream.");
        LOG.warn("Content binding is not active: items, blocks, creative tabs and commands are"
                + " recorded by ReMod but are not inserted into Minecraft's own registries."
                + " See docs/version-support.md.");
        LOG.debug(() -> "Diagnostic probe: Mojang-mapped registry classes "
                + (mappedClassesVisible ? "are" : "are not") + " visible; Brigadier "
                + (brigadierVisible ? "is" : "is not") + " visible.");
    }

    @Override
    public String id() {
        return adapterId;
    }

    @Override
    public String minecraftVersion() {
        return minecraftVersion;
    }

    @Override
    public Side side() {
        return side;
    }

    @Override
    public boolean isGameAttached() {
        return gameAttached;
    }

    @Override
    public boolean bindItem(RegistryEntry<ItemDefinition> entry) {
        return notBound("item", entry.id().toString());
    }

    @Override
    public boolean bindBlock(RegistryEntry<BlockDefinition> entry) {
        return notBound("block", entry.id().toString());
    }

    @Override
    public boolean bindCreativeTab(RegistryEntry<CreativeTabDefinition> entry) {
        return notBound("creative tab", entry.id().toString());
    }

    /**
     * Hands a mod's command to the transformation layer.
     *
     * <p>Returns false when the command was only queued, which is the normal
     * case: mods register during {@code INIT}, and Minecraft does not build its
     * command dispatcher until later in its own startup. The layer registers
     * everything it holds the moment the dispatcher appears.</p>
     */
    @Override
    public boolean bindCommand(CommandSpec command, String ownerModId) {
        if (integration == null) {
            return notBound("command", "/" + command.name() + " (from " + ownerModId + ")");
        }
        boolean live = integration.registerCommand(command, ownerModId,
                () -> new ModCommandExecutor(command, ownerModId));
        if (!live) {
            LOG.debug(() -> "/" + command.name() + " from " + ownerModId
                    + " is queued for Minecraft's command dispatcher");
        }
        return live;
    }

    @Override
    public boolean openNetworkChannel(Identifier channelId) {
        return notBound("network channel", channelId.toString());
    }

    /** One place that records the registration and reports the truth about it. */
    private boolean notBound(String what, String name) {
        LOG.debug(() -> "Recorded " + what + " " + name
                + " in ReMod's registry; it is not bound into Minecraft on this build");
        return false;
    }

    @Override
    public Optional<ServerHandle> server() {
        return Optional.empty();
    }

    @Override
    public Optional<ClientApi> client() {
        return Optional.ofNullable(client);
    }

    /**
     * What this bridge can actually do, claimed only where it is true.
     *
     * <p>{@link Capability#COMMANDS} appears only when a transformation layer
     * was installed and Brigadier is reachable, because those are exactly the
     * conditions under which a registered command reaches the game.</p>
     */
    @Override
    public Set<Capability> capabilities() {
        EnumSet<Capability> capabilities = EnumSet.noneOf(Capability.class);
        if (integration != null && brigadierVisible) {
            capabilities.add(Capability.COMMANDS);
        }
        return Collections.unmodifiableSet(capabilities);
    }

    /**
     * True when Minecraft's Mojang-mapped registry classes were found.
     *
     * <p>Diagnostic only: it distinguishes a development (deobfuscated)
     * environment from a stock obfuscated install, which is the first thing to
     * know when working on the binding layer.</p>
     */
    public boolean mappedClassesVisible() {
        return mappedClassesVisible;
    }

    /** True when Brigadier's dispatcher class was found. */
    public boolean brigadierVisible() {
        return brigadierVisible;
    }

    /** The reflection helper, exposed for adapter-level tests. */
    public MinecraftReflection reflection() {
        return reflection;
    }
}
