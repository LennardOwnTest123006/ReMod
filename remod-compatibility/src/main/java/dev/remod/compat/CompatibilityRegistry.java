package dev.remod.compat;

import dev.remod.compat.bridge.FabricBridge;
import dev.remod.compat.bridge.ForgeBridge;
import dev.remod.compat.bridge.NeoForgeBridge;
import dev.remod.compat.bridge.QuiltBridge;
import dev.remod.compat.bridge.ServerPlatformBridge;
import dev.remod.common.log.ReModLog;
import dev.remod.common.log.ReModLogger;
import dev.remod.loader.ReModPaths;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The set of loader bridges ReMod ships, and the entry point for detection.
 *
 * <p>Adding a future compatibility module is a matter of implementing
 * {@link LoaderBridge} and adding it here -- the loader, the API and existing
 * mods are unaffected.</p>
 */
public final class CompatibilityRegistry {

    private static final ReModLogger LOG = ReModLog.get("ReMod/Compat");

    private final List<LoaderBridge> bridges;

    private CompatibilityRegistry(List<LoaderBridge> bridges) {
        this.bridges = List.copyOf(bridges);
    }

    /** The bridges shipped with ReMod. */
    public static CompatibilityRegistry standard() {
        List<LoaderBridge> bridges = new ArrayList<>();
        bridges.add(new FabricBridge());
        bridges.add(new QuiltBridge());
        bridges.add(new ForgeBridge());
        bridges.add(new NeoForgeBridge());
        bridges.add(ServerPlatformBridge.paper());
        bridges.add(ServerPlatformBridge.spigot());
        bridges.add(ServerPlatformBridge.bukkit());
        return new CompatibilityRegistry(bridges);
    }

    /** Builds a registry from explicit bridges. Used by tests. */
    public static CompatibilityRegistry of(LoaderBridge... bridges) {
        return new CompatibilityRegistry(List.of(bridges));
    }

    public List<LoaderBridge> bridges() {
        return bridges;
    }

    public Optional<LoaderBridge> bridgeFor(LoaderPlatform platform) {
        for (LoaderBridge bridge : bridges) {
            if (bridge.platform() == platform) {
                return Optional.of(bridge);
            }
        }
        return Optional.empty();
    }

    /**
     * Detects every other loader present in a Minecraft installation.
     *
     * <p>Used by the installer to warn about conflicting profiles before
     * writing anything, and to explain misplaced mods afterwards.</p>
     */
    public Map<LoaderPlatform, LoaderBridge.Detection> detectAll(ReModPaths paths) {
        Map<LoaderPlatform, LoaderBridge.Detection> found = new LinkedHashMap<>();
        for (LoaderBridge bridge : bridges) {
            try {
                bridge.detect(paths).ifPresent(
                        detection -> found.put(bridge.platform(), detection));
            } catch (RuntimeException e) {
                LOG.debug(() -> "Detection for " + bridge.platform().displayName()
                        + " failed: " + e);
            }
        }
        if (!found.isEmpty()) {
            LOG.info("Other loaders detected: " + found.keySet());
        }
        return found;
    }

    /**
     * A plain-text compatibility matrix.
     *
     * <p>{@code docs/compatibility.md} is generated from this, so the
     * documentation cannot claim something the code does not.</p>
     */
    public String matrix() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-12s %-18s %s%n", "PLATFORM", "LEVEL", "MOD LOADING"));
        for (LoaderBridge bridge : bridges) {
            sb.append(String.format("%-12s %-18s %s%n",
                    bridge.platform().displayName(),
                    bridge.level().label(),
                    bridge.canLoadMod(null) ? "yes" : "no"));
        }
        return sb.toString();
    }
}
