package dev.remod.loader.runtime;

import dev.remod.api.registry.BlockDefinition;
import dev.remod.api.registry.CreativeTabDefinition;
import dev.remod.api.registry.ItemDefinition;
import dev.remod.api.registry.Registries;
import dev.remod.api.registry.Registry;
import dev.remod.api.service.GameBridge;

import java.util.function.Supplier;

/**
 * The registry set shared by every mod.
 *
 * <p>One instance exists per loader. The "current owner" is whichever mod's
 * lifecycle method is executing, which is how every registration is attributed
 * without mods having to pass their id around.</p>
 */
public final class DefaultRegistries implements Registries {

    private final DefaultRegistry<ItemDefinition> items;
    private final DefaultRegistry<BlockDefinition> blocks;
    private final DefaultRegistry<CreativeTabDefinition> creativeTabs;
    private volatile boolean open = true;

    public DefaultRegistries(Supplier<String> currentOwner, Supplier<GameBridge> bridge) {
        Supplier<Boolean> openCheck = this::isOpen;
        this.items = new DefaultRegistry<>("items", ItemDefinition::id, currentOwner, openCheck,
                entry -> bridge.get() != null && bridge.get().bindItem(entry));
        this.blocks = new DefaultRegistry<>("blocks", BlockDefinition::id, currentOwner, openCheck,
                entry -> bridge.get() != null && bridge.get().bindBlock(entry));
        this.creativeTabs = new DefaultRegistry<>("creative tabs", CreativeTabDefinition::id,
                currentOwner, openCheck,
                entry -> bridge.get() != null && bridge.get().bindCreativeTab(entry));
    }

    @Override
    public Registry<ItemDefinition> items() {
        return items;
    }

    @Override
    public Registry<BlockDefinition> blocks() {
        return blocks;
    }

    @Override
    public Registry<CreativeTabDefinition> creativeTabs() {
        return creativeTabs;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    /** Closes registration, mirroring Minecraft freezing its own registries. */
    public void close() {
        this.open = false;
    }

    /** Reopens registration. Only the loader does this, between phases. */
    public void reopen() {
        this.open = true;
    }

    /** Removes everything one mod registered. */
    public void removeAllOf(String modId) {
        items.removeAllOf(modId);
        blocks.removeAllOf(modId);
        creativeTabs.removeAllOf(modId);
    }

    /** Binds every pending entry once a game bridge becomes available. */
    public int bindAll() {
        return items.bindAll() + blocks.bindAll() + creativeTabs.bindAll();
    }

    /** Total registered entries across all registries. */
    public int totalSize() {
        return items.size() + blocks.size() + creativeTabs.size();
    }
}
