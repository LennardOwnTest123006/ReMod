package dev.remod.loader.adapter;

import dev.remod.common.log.ReModLog;
import dev.remod.common.log.ReModLogger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Finds the best {@link MinecraftVersionAdapter} for a Minecraft version.
 *
 * <p>Adapters come from {@link ServiceLoader}, so support for a new Minecraft
 * release is added by dropping in a jar. The list is discovered once and
 * cached: repeatedly walking the classpath during startup is exactly the kind
 * of avoidable work that makes a loader feel slow.</p>
 */
public final class AdapterRegistry {

    private static final ReModLogger LOG = ReModLog.get("ReMod/Adapters");

    private final List<MinecraftVersionAdapter> adapters;

    private AdapterRegistry(List<MinecraftVersionAdapter> adapters) {
        this.adapters = adapters;
    }

    /** Discovers adapters visible to {@code loader}. */
    public static AdapterRegistry discover(ClassLoader loader) {
        List<MinecraftVersionAdapter> found = new ArrayList<>();
        try {
            for (MinecraftVersionAdapter adapter
                    : ServiceLoader.load(MinecraftVersionAdapter.class, loader)) {
                found.add(adapter);
            }
        } catch (Throwable e) {
            // A broken adapter jar must not stop ReMod from starting; the
            // headless bridge is still a usable fallback.
            LOG.error("Failed while discovering version adapters: " + e, e);
        }
        found.sort(Comparator.comparing(MinecraftVersionAdapter::id));
        LOG.debug(() -> "Discovered " + found.size() + " version adapter(s)");
        return new AdapterRegistry(found);
    }

    /** Uses this class's own loader. */
    public static AdapterRegistry discover() {
        return discover(AdapterRegistry.class.getClassLoader());
    }

    /** Builds a registry from explicit adapters. Used by tests. */
    public static AdapterRegistry of(MinecraftVersionAdapter... adapters) {
        return new AdapterRegistry(new ArrayList<>(List.of(adapters)));
    }

    public List<MinecraftVersionAdapter> adapters() {
        return List.copyOf(adapters);
    }

    /** The highest-scoring adapter for a version, or empty when none applies. */
    public Optional<MinecraftVersionAdapter> select(String minecraftVersion) {
        MinecraftVersionAdapter best = null;
        MinecraftVersionAdapter.Support bestSupport = MinecraftVersionAdapter.Support.UNSUPPORTED;
        for (MinecraftVersionAdapter adapter : adapters) {
            MinecraftVersionAdapter.Support support;
            try {
                support = adapter.supportFor(minecraftVersion);
            } catch (RuntimeException e) {
                LOG.warn("Adapter " + adapter.id() + " threw while reporting support for "
                        + minecraftVersion + "; ignoring it", e);
                continue;
            }
            if (support != null && support.rank() > bestSupport.rank()) {
                best = adapter;
                bestSupport = support;
            }
        }
        if (best == null) {
            LOG.warn("No version adapter supports Minecraft " + minecraftVersion);
            return Optional.empty();
        }
        MinecraftVersionAdapter selected = best;
        MinecraftVersionAdapter.Support selectedSupport = bestSupport;
        LOG.info("Using version adapter " + selected.id() + " (" + selectedSupport.name().toLowerCase(
                java.util.Locale.ROOT) + " support for " + minecraftVersion + ")");
        return Optional.of(selected);
    }

    /** The support level of the best adapter for a version. */
    public MinecraftVersionAdapter.Support bestSupportFor(String minecraftVersion) {
        MinecraftVersionAdapter.Support best = MinecraftVersionAdapter.Support.UNSUPPORTED;
        for (MinecraftVersionAdapter adapter : adapters) {
            try {
                MinecraftVersionAdapter.Support support = adapter.supportFor(minecraftVersion);
                if (support != null && support.rank() > best.rank()) {
                    best = support;
                }
            } catch (RuntimeException e) {
                // Ignored; select() logs it.
            }
        }
        return best;
    }
}
