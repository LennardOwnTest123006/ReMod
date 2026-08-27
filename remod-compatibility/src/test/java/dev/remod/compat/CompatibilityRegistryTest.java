package dev.remod.compat;

import dev.remod.common.log.ReModLog;
import dev.remod.loader.ReModPaths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatibilityRegistryTest {

    @TempDir
    Path gameDir;

    private CompatibilityRegistry registry;
    private ReModPaths paths;

    @BeforeEach
    void setUp() {
        ReModLog.reset();
        registry = CompatibilityRegistry.standard();
        paths = new ReModPaths(gameDir);
    }

    @Test
    void detectsNothingInACleanInstallation() {
        Map<LoaderPlatform, LoaderBridge.Detection> found = registry.detectAll(paths);
        assertTrue(found.isEmpty(), found.toString());
    }

    @Test
    void detectsFabricFromItsLauncherProfile() throws IOException {
        Files.createDirectories(paths.versionsDirectory()
                .resolve("fabric-loader-0.15.11-1.21.4"));

        Map<LoaderPlatform, LoaderBridge.Detection> found = registry.detectAll(paths);

        assertTrue(found.containsKey(LoaderPlatform.FABRIC), found.toString());
        LoaderBridge.Detection detection = found.get(LoaderPlatform.FABRIC);
        assertEquals("1.21.4", detection.version());
        assertEquals(1, detection.launcherProfiles().size());
    }

    @Test
    void detectsForgeAndNeoForgeSeparately() throws IOException {
        Files.createDirectories(paths.versionsDirectory().resolve("1.20.1-forge-47.2.0"));
        Files.createDirectories(paths.versionsDirectory().resolve("neoforge-21.1.66"));

        Map<LoaderPlatform, LoaderBridge.Detection> found = registry.detectAll(paths);

        assertTrue(found.containsKey(LoaderPlatform.FORGE));
        assertTrue(found.containsKey(LoaderPlatform.NEOFORGE));
    }

    @Test
    void detectsAnotherLoadersModsFolder() throws IOException {
        Files.createDirectories(gameDir.resolve("mods"));

        Map<LoaderPlatform, LoaderBridge.Detection> found = registry.detectAll(paths);

        // The shared "mods" folder is claimed by every client/server loader,
        // which is exactly the ambiguity a user needs warning about.
        assertTrue(found.containsKey(LoaderPlatform.FABRIC));
        assertTrue(found.get(LoaderPlatform.FABRIC).launcherProfiles().isEmpty());
    }

    @Test
    void detectsAPaperServerJar() throws IOException {
        Files.writeString(gameDir.resolve("paper-1.21.4-123.jar"), "");

        Map<LoaderPlatform, LoaderBridge.Detection> found = registry.detectAll(paths);

        assertTrue(found.containsKey(LoaderPlatform.PAPER), found.toString());
    }

    @Test
    void everyClientServerLoaderIsCoexistenceOnly() {
        for (LoaderPlatform platform : new LoaderPlatform[]{
                LoaderPlatform.FABRIC, LoaderPlatform.QUILT,
                LoaderPlatform.FORGE, LoaderPlatform.NEOFORGE}) {
            LoaderBridge bridge = registry.bridgeFor(platform).orElseThrow();
            assertEquals(CompatibilityLevel.COEXISTENCE, bridge.level(), platform.name());
            assertFalse(bridge.canLoadMod(gameDir), platform.name());
            assertFalse(bridge.whyNotLoadable().isBlank(), platform.name());
            assertFalse(bridge.coexistenceNotes().isEmpty(), platform.name());
        }
    }

    @Test
    void serverPluginPlatformsAreDeclaredImpossibleRatherThanExperimental() {
        for (LoaderPlatform platform : new LoaderPlatform[]{
                LoaderPlatform.BUKKIT, LoaderPlatform.SPIGOT, LoaderPlatform.PAPER}) {
            LoaderBridge bridge = registry.bridgeFor(platform).orElseThrow();
            assertEquals(CompatibilityLevel.NOT_POSSIBLE, bridge.level(), platform.name());
            assertTrue(bridge.whyNotLoadable().contains("org.bukkit"), platform.name());
        }
    }

    @Test
    void noBridgeClaimsToLoadAnotherLoadersMods() {
        for (LoaderBridge bridge : registry.bridges()) {
            assertFalse(bridge.canLoadMod(gameDir.resolve("anything.jar")),
                    bridge.platform().displayName() + " must not claim to load foreign mods");
        }
    }

    @Test
    void theMatrixListsEveryBridge() {
        String matrix = registry.matrix();
        for (LoaderBridge bridge : registry.bridges()) {
            assertTrue(matrix.contains(bridge.platform().displayName()),
                    bridge.platform().displayName());
        }
        assertFalse(matrix.contains("yes"), "no platform loads foreign mods yet:\n" + matrix);
    }
}
