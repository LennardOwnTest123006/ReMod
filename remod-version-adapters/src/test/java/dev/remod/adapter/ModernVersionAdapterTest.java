package dev.remod.adapter;

import dev.remod.adapter.generic.ModernGameBridge;
import dev.remod.adapter.generic.ModernVersionAdapter;
import dev.remod.api.Side;
import dev.remod.api.service.GameBridge;
import dev.remod.common.log.ReModLog;
import dev.remod.loader.adapter.AdapterRegistry;
import dev.remod.loader.adapter.MinecraftVersionAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModernVersionAdapterTest {

    @BeforeEach
    void quietLogging() {
        ReModLog.reset();
    }

    @Test
    void isDiscoveredThroughServiceLoader() {
        AdapterRegistry registry =
                AdapterRegistry.discover(ModernVersionAdapterTest.class.getClassLoader());

        assertFalse(registry.adapters().isEmpty(), "the service file should register an adapter");
        Optional<MinecraftVersionAdapter> selected = registry.select("1.21.4");
        assertTrue(selected.isPresent());
        assertEquals(ModernVersionAdapter.ID, selected.get().id());
    }

    @Test
    void isNotSelectedForAnUnsupportedVersion() {
        AdapterRegistry registry =
                AdapterRegistry.discover(ModernVersionAdapterTest.class.getClassLoader());

        assertTrue(registry.select("1.8.9").isEmpty());
        assertEquals(MinecraftVersionAdapter.Support.UNSUPPORTED,
                registry.bestSupportFor("1.8.9"));
    }

    @Test
    void namesTheBridgeAfterTheMinecraftSeries() {
        GameBridge bridge = new ModernVersionAdapter()
                .createBridge("1.21.4", Side.CLIENT, getClass().getClassLoader());

        assertEquals("remod:modern-1.21", bridge.id());
        assertEquals("1.21.4", bridge.minecraftVersion());
        assertEquals(Side.CLIENT, bridge.side());
    }

    @Test
    void reportsHonestlyWhenNoMinecraftIsPresent() {
        // The test JVM has no Minecraft on its classpath, which is precisely
        // the situation the bridge must not lie about.
        ModernGameBridge bridge = (ModernGameBridge) new ModernVersionAdapter()
                .createBridge("1.21.4", Side.CLIENT, getClass().getClassLoader());

        assertFalse(bridge.isGameAttached());
        assertFalse(bridge.mappedClassesVisible());
        assertTrue(bridge.capabilities().isEmpty());
        for (GameBridge.Capability capability : GameBridge.Capability.values()) {
            assertFalse(bridge.supports(capability), capability.name());
        }
    }

    @Test
    void offersTheClientApiOnTheClientAndNotOnAServer() {
        GameBridge clientSide = new ModernVersionAdapter()
                .createBridge("1.21.4", Side.CLIENT, getClass().getClassLoader());
        GameBridge serverSide = new ModernVersionAdapter()
                .createBridge("1.21.4", Side.DEDICATED_SERVER, getClass().getClassLoader());

        assertTrue(clientSide.client().isPresent());
        assertTrue(serverSide.client().isEmpty());
        assertTrue(clientSide.server().isEmpty());
    }

    @Test
    void higherScoringAdaptersWin() {
        MinecraftVersionAdapter specialist = new MinecraftVersionAdapter() {
            @Override
            public String id() {
                return "test:specialist";
            }

            @Override
            public String displayName() {
                return "Specialist";
            }

            @Override
            public Support supportFor(String minecraftVersion) {
                return "1.21.4".equals(minecraftVersion) ? Support.EXACT : Support.UNSUPPORTED;
            }

            @Override
            public GameBridge createBridge(String v, Side s, ClassLoader l) {
                throw new UnsupportedOperationException();
            }
        };
        AdapterRegistry registry = AdapterRegistry.of(new ModernVersionAdapter(), specialist);

        assertEquals("test:specialist", registry.select("1.21.4").orElseThrow().id());
        assertEquals(ModernVersionAdapter.ID, registry.select("1.20.1").orElseThrow().id());
    }

    @Test
    void anAdapterThatThrowsIsIgnoredRatherThanBreakingSelection() {
        MinecraftVersionAdapter broken = new MinecraftVersionAdapter() {
            @Override
            public String id() {
                return "test:broken";
            }

            @Override
            public String displayName() {
                return "Broken";
            }

            @Override
            public Support supportFor(String minecraftVersion) {
                throw new IllegalStateException("adapter bug");
            }

            @Override
            public GameBridge createBridge(String v, Side s, ClassLoader l) {
                throw new UnsupportedOperationException();
            }
        };
        AdapterRegistry registry = AdapterRegistry.of(broken, new ModernVersionAdapter());

        assertEquals(ModernVersionAdapter.ID, registry.select("1.21.4").orElseThrow().id());
    }
}
