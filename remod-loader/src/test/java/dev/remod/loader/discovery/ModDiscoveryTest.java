package dev.remod.loader.discovery;

import dev.remod.loader.ModTestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModDiscoveryTest {

    @Test
    void findsJarsAndExplodedDirectories(@TempDir Path dir) throws IOException {
        ModTestFixtures.writeModJar(dir, "alpha.jar",
                ModTestFixtures.manifest("alpha").build());
        ModTestFixtures.writeModDirectory(dir, "beta-dev",
                ModTestFixtures.manifest("beta").build());

        DiscoveryResult result = ModDiscovery.scan(dir);

        assertEquals(2, result.candidates().size());
        assertTrue(result.problems().isEmpty(), result.problems().toString());
        assertEquals(ModSourceKind.JAR, result.candidates().get(0).kind());
        assertEquals(ModSourceKind.DIRECTORY, result.candidates().get(1).kind());
    }

    @Test
    void returnsEmptyForAMissingDirectory(@TempDir Path dir) {
        DiscoveryResult result = ModDiscovery.scan(dir.resolve("does-not-exist"));
        assertTrue(result.isEmpty());
        assertTrue(result.problems().isEmpty());
    }

    @Test
    void identifiesModsForOtherLoadersByName(@TempDir Path dir) throws IOException {
        writeJarWith(dir, "some-fabric-mod.jar", "fabric.mod.json", "{}");
        writeJarWith(dir, "some-forge-mod.jar", "META-INF/mods.toml", "modLoader=\"javafml\"");
        writeJarWith(dir, "some-plugin.jar", "plugin.yml", "name: Thing");

        DiscoveryResult result = ModDiscovery.scan(dir);

        assertTrue(result.candidates().isEmpty());
        assertEquals(3, result.foreignMods().size());
        assertEquals("Fabric", result.foreignMods().get(0).loaderName());
        assertEquals("Forge", result.foreignMods().get(1).loaderName());
        assertEquals("Bukkit/Spigot/Paper", result.foreignMods().get(2).loaderName());
    }

    @Test
    void reportsAJarWithNoRecognisableManifest(@TempDir Path dir) throws IOException {
        writeJarWith(dir, "mystery.jar", "com/example/Thing.class", "not bytecode");

        DiscoveryResult result = ModDiscovery.scan(dir);

        assertEquals(1, result.problems().size());
        assertEquals(DiscoveryProblem.Kind.NOT_A_REMOD_MOD, result.problems().get(0).kind());
        assertTrue(result.problems().get(0).suggestion().contains("another loader"));
    }

    @Test
    void reportsAMalformedManifestWithoutStoppingOtherMods(@TempDir Path dir) throws IOException {
        ModTestFixtures.writeModJar(dir, "good.jar", ModTestFixtures.manifest("good").build());
        ModTestFixtures.writeModJar(dir, "broken.jar", "{ this is not json ");
        ModTestFixtures.writeModJar(dir, "incomplete.jar",
                ModTestFixtures.manifest("incomplete").remove("version").build());

        DiscoveryResult result = ModDiscovery.scan(dir);

        assertEquals(1, result.candidates().size());
        assertEquals("good", result.candidates().get(0).id());
        assertEquals(2, result.problems().size());
        for (DiscoveryProblem problem : result.problems()) {
            assertEquals(DiscoveryProblem.Kind.INVALID_MANIFEST, problem.kind());
        }
        assertTrue(result.problems().get(1).detail().contains("version"),
                result.problems().get(1).detail());
    }

    @Test
    void reportsACorruptedArchive(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("truncated.jar"), "this is not a zip file at all");

        DiscoveryResult result = ModDiscovery.scan(dir);

        assertEquals(1, result.problems().size());
        assertEquals(DiscoveryProblem.Kind.UNREADABLE, result.problems().get(0).kind());
        assertTrue(result.problems().get(0).suggestion().contains("Download the mod again"));
    }

    @Test
    void skipsDisabledAndHiddenEntries(@TempDir Path dir) throws IOException {
        ModTestFixtures.writeModJar(dir, "alpha.jar", ModTestFixtures.manifest("alpha").build());
        ModTestFixtures.writeModJar(dir, "beta.jar.disabled",
                ModTestFixtures.manifest("beta").build());
        ModTestFixtures.writeModJar(dir, ".hidden.jar",
                ModTestFixtures.manifest("hidden").build());

        DiscoveryResult result = ModDiscovery.scan(dir);

        assertEquals(1, result.candidates().size());
        assertEquals("alpha", result.candidates().get(0).id());
        assertTrue(result.problems().isEmpty());
    }

    @Test
    void ignoresUnrelatedFilesAndPlainDirectories(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("README.txt"), "put your mods here");
        Files.createDirectories(dir.resolve("screenshots"));

        DiscoveryResult result = ModDiscovery.scan(dir);

        assertTrue(result.candidates().isEmpty());
        assertTrue(result.problems().isEmpty());
        assertTrue(result.foreignMods().isEmpty());
    }

    @Test
    void acceptsTheRemodExtensionAsWellAsJar(@TempDir Path dir) throws IOException {
        ModTestFixtures.writeModJar(dir, "packaged.remod",
                ModTestFixtures.manifest("packaged").build(),
                Map.of("dev/example/Main.class", "x"));

        DiscoveryResult result = ModDiscovery.scan(dir);

        assertEquals(1, result.candidates().size());
        assertFalse(result.candidates().get(0).fileSize() < 0);
    }

    private static void writeJarWith(Path dir, String fileName, String entry, String content)
            throws IOException {
        Files.createDirectories(dir);
        try (OutputStream out = Files.newOutputStream(dir.resolve(fileName));
             ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(entry));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }
}
