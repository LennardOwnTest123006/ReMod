package dev.remod.api.service;

import dev.remod.api.Side;
import dev.remod.api.client.ClientApi;
import dev.remod.api.command.CommandSpec;
import dev.remod.api.game.Identifier;
import dev.remod.api.game.ServerHandle;
import dev.remod.api.registry.BlockDefinition;
import dev.remod.api.registry.CreativeTabDefinition;
import dev.remod.api.registry.ItemDefinition;
import dev.remod.api.registry.RegistryEntry;

import java.util.Optional;

/**
 * The boundary between ReMod's version-independent API and one specific
 * Minecraft build.
 *
 * <p>This is the single most important interface in ReMod's architecture.
 * Everything a mod does that must touch the game -- registering an item,
 * adding a command, drawing a HUD -- arrives here as a version-independent
 * description, and the {@code remod-version-adapters} module implementing this
 * interface turns it into calls against the Minecraft build actually
 * running.</p>
 *
 * <p>Consequently, adding support for a new Minecraft release means writing one
 * adapter, not touching the API, the loader or any mod.</p>
 *
 * <p>Every {@code bind*} method returns a boolean rather than throwing:
 * an adapter that cannot express a particular feature on its Minecraft version
 * reports that honestly, ReMod logs it once against the owning mod, and the
 * rest of the mod still loads.</p>
 */
public interface GameBridge {

    /** A stable id for this adapter, e.g. {@code remod:generic-1.21}. */
    String id();

    /** The Minecraft version this bridge is bound to. */
    String minecraftVersion();

    /** The side this process runs as. */
    Side side();

    /**
     * True when this bridge is attached to a live Minecraft process.
     *
     * <p>False for the no-op bridge ReMod uses under {@code remod test} and in
     * unit tests, where mods still register content but no game exists.</p>
     */
    boolean isGameAttached();

    /** Binds a registered item to the running game. */
    boolean bindItem(RegistryEntry<ItemDefinition> entry);

    /** Binds a registered block (and its block item, when it has one). */
    boolean bindBlock(RegistryEntry<BlockDefinition> entry);

    /** Binds a registered creative tab. */
    boolean bindCreativeTab(RegistryEntry<CreativeTabDefinition> entry);

    /** Installs a command into the running game's command tree. */
    boolean bindCommand(CommandSpec command, String ownerModId);

    /** Opens a custom-payload channel. */
    boolean openNetworkChannel(Identifier channelId);

    /** The running server, empty until one exists. */
    Optional<ServerHandle> server();

    /** Client functionality, empty on a dedicated server. */
    Optional<ClientApi> client();

    /**
     * Which optional capabilities this adapter actually implements on this
     * Minecraft version. ReMod reports the gaps rather than letting a mod
     * discover them as silent no-ops.
     */
    java.util.Set<Capability> capabilities();

    default boolean supports(Capability capability) {
        return capabilities().contains(capability);
    }

    /** The optional things an adapter may or may not be able to do. */
    enum Capability {
        ITEM_REGISTRATION,
        BLOCK_REGISTRATION,
        CREATIVE_TABS,
        COMMANDS,
        KEYBINDS,
        HUD_RENDERING,
        NETWORKING,
        SCREEN_EVENTS,
        RESOURCE_RELOAD_EVENTS
    }
}
