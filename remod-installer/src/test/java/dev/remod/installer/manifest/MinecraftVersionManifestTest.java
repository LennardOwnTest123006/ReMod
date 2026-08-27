package dev.remod.installer.manifest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftVersionManifestTest {

    /** Shaped exactly like Mojang's version_manifest_v2.json. */
    private static final String SAMPLE = "{\n"
            + "  \"latest\": {\"release\": \"1.21.4\", \"snapshot\": \"25w02a\"},\n"
            + "  \"versions\": [\n"
            + "    {\"id\":\"25w02a\",\"type\":\"snapshot\","
            + "     \"url\":\"https://example.invalid/25w02a.json\",\"sha1\":\"aaa\","
            + "     \"releaseTime\":\"2025-01-08T12:00:00+00:00\"},\n"
            + "    {\"id\":\"1.21.4\",\"type\":\"release\","
            + "     \"url\":\"https://example.invalid/1.21.4.json\",\"sha1\":\"bbb\","
            + "     \"releaseTime\":\"2024-12-03T10:12:57+00:00\"},\n"
            + "    {\"id\":\"1.20.1\",\"type\":\"release\","
            + "     \"url\":\"https://example.invalid/1.20.1.json\",\"sha1\":\"ccc\","
            + "     \"releaseTime\":\"2023-06-12T13:25:51+00:00\"},\n"
            + "    {\"id\":\"b1.7.3\",\"type\":\"old_beta\","
            + "     \"url\":\"https://example.invalid/b1.7.3.json\",\"sha1\":\"ddd\","
            + "     \"releaseTime\":\"2011-07-08T10:07:00+00:00\"}\n"
            + "  ]\n"
            + "}";

    @Test
    void parsesLatestPointersAndEveryVersion() {
        MinecraftVersionManifest manifest = MinecraftVersionManifest.parse(SAMPLE);

        assertEquals("1.21.4", manifest.latestRelease().orElseThrow());
        assertEquals("25w02a", manifest.latestSnapshot().orElseThrow());
        assertEquals(4, manifest.size());
    }

    @Test
    void exposesIdTypeUrlChecksumAndDate() {
        MinecraftVersionEntry entry =
                MinecraftVersionManifest.parse(SAMPLE).find("1.21.4").orElseThrow();

        assertEquals("1.21.4", entry.id());
        assertEquals(MinecraftVersionEntry.Type.RELEASE, entry.type());
        assertEquals("https://example.invalid/1.21.4.json", entry.url());
        assertEquals("bbb", entry.sha1());
        assertEquals("1.21", entry.series());
        assertFalse(entry.releaseDate().isEmpty());
    }

    @Test
    void filtersByType() {
        MinecraftVersionManifest manifest = MinecraftVersionManifest.parse(SAMPLE);

        List<MinecraftVersionEntry> releases =
                manifest.ofType(MinecraftVersionEntry.Type.RELEASE);
        assertEquals(2, releases.size());
        assertEquals(1, manifest.ofType(MinecraftVersionEntry.Type.SNAPSHOT).size());
        assertEquals(1, manifest.ofType(MinecraftVersionEntry.Type.OLD_BETA).size());
    }

    @Test
    void searchesByIdSubstring() {
        MinecraftVersionManifest manifest = MinecraftVersionManifest.parse(SAMPLE);

        assertEquals(1, manifest.search("1.21").size());
        assertEquals(2, manifest.search("1.2").size());
        assertEquals(4, manifest.search("").size());
        assertEquals(4, manifest.search(null).size());
        assertTrue(manifest.search("nonexistent").isEmpty());
    }

    @Test
    void keepsMojangsNewestFirstOrdering() {
        List<MinecraftVersionEntry> versions = MinecraftVersionManifest.parse(SAMPLE).versions();
        assertEquals("25w02a", versions.get(0).id());
        assertEquals("b1.7.3", versions.get(versions.size() - 1).id());
    }

    @Test
    void skipsMalformedEntriesRatherThanFailingTheWholeList() {
        String withJunk = "{\"latest\":{\"release\":\"1.21.4\"},\"versions\":["
                + "{\"type\":\"release\"},"
                + "{\"id\":\"1.21.4\",\"type\":\"release\"},"
                + "\"not an object\"]}";

        MinecraftVersionManifest manifest = MinecraftVersionManifest.parse(withJunk);

        assertEquals(1, manifest.size());
        assertEquals("1.21.4", manifest.versions().get(0).id());
    }

    @Test
    void rejectsAnUnusableDocumentWithAnActionableMessage() {
        ManifestException broken = assertThrows(ManifestException.class,
                () -> MinecraftVersionManifest.parse("not json"));
        assertTrue(broken.suggestion().contains("proxy"), broken.suggestion());

        ManifestException empty = assertThrows(ManifestException.class,
                () -> MinecraftVersionManifest.parse("{\"versions\":[]}"));
        assertTrue(empty.getMessage().contains("no versions"), empty.getMessage());
    }

    @Test
    void toleratesAMissingLatestBlock() {
        MinecraftVersionManifest manifest = MinecraftVersionManifest.parse(
                "{\"versions\":[{\"id\":\"1.21.4\",\"type\":\"release\"}]}");

        assertTrue(manifest.latestRelease().isEmpty());
        assertEquals(1, manifest.size());
    }
}
