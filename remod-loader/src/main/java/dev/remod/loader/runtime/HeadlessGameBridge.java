package dev.remod.loader.runtime;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A bridge with no Minecraft behind it.
 *
 * <p>ReMod installs this whenever no version adapter matches, and always under
 * {@code remod test}. Mods still initialise, register content and exchange
 * events; nothing reaches a game, and {@link #isGameAttached()} reports that
 * honestly so a mod can adapt instead of crashing on a missing world.</p>
 *
 * <p>Everything registered through it is recorded, which is what makes the
 * loader testable and what lets {@code remod test} tell an author "your mod
 * registered 2 items, 1 block and 1 command" without launching Minecraft.</p>
 */
public final class HeadlessGameBridge implements GameBridge {

    private final String minecraftVersion;
    private final Side side;
    private final HeadlessClientApi client;
    private final List<Identifier> boundItems = new ArrayList<>();
    private final List<Identifier> boundBlocks = new ArrayList<>();
    private final List<Identifier> boundCreativeTabs = new ArrayList<>();
    private final List<String> boundCommands = new ArrayList<>();
    private final List<Identifier> openChannels = new ArrayList<>();

    public HeadlessGameBridge(String minecraftVersion, Side side) {
        this.minecraftVersion = minecraftVersion;
        this.side = side == null ? Side.COMMON : side;
        this.client = this.side == Side.DEDICATED_SERVER ? null : new HeadlessClientApi();
    }

    @Override
    public String id() {
        return "remod:headless";
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
        return false;
    }

    @Override
    public boolean bindItem(RegistryEntry<ItemDefinition> entry) {
        boundItems.add(entry.id());
        // Recorded, but not bound to a game: report that truthfully.
        return false;
    }

    @Override
    public boolean bindBlock(RegistryEntry<BlockDefinition> entry) {
        boundBlocks.add(entry.id());
        return false;
    }

    @Override
    public boolean bindCreativeTab(RegistryEntry<CreativeTabDefinition> entry) {
        boundCreativeTabs.add(entry.id());
        return false;
    }

    @Override
    public boolean bindCommand(CommandSpec command, String ownerModId) {
        boundCommands.add(command.name());
        return false;
    }

    @Override
    public boolean openNetworkChannel(Identifier channelId) {
        openChannels.add(channelId);
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

    @Override
    public Set<Capability> capabilities() {
        // Nothing is actually supported; the headless bridge does not pretend.
        return Collections.unmodifiableSet(EnumSet.noneOf(Capability.class));
    }

    /** The client API this bridge exposes, or {@code null} on a dedicated server. */
    public HeadlessClientApi headlessClient() {
        return client;
    }

    public List<Identifier> recordedItems() {
        return Collections.unmodifiableList(boundItems);
    }

    public List<Identifier> recordedBlocks() {
        return Collections.unmodifiableList(boundBlocks);
    }

    public List<Identifier> recordedCreativeTabs() {
        return Collections.unmodifiableList(boundCreativeTabs);
    }

    public List<String> recordedCommands() {
        return Collections.unmodifiableList(boundCommands);
    }

    public List<Identifier> recordedChannels() {
        return Collections.unmodifiableList(openChannels);
    }
}
