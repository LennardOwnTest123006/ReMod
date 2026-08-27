package dev.remod.loader.runtime;

import dev.remod.api.config.ConfigSpec;
import dev.remod.common.log.ReModLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonConfigTest {

    private static ConfigSpec spec() {
        return ConfigSpec.builder()
                .comment("Printed when a player joins.")
                .define("greeting", "Welcome!")
                .comment("How far the scanner reaches.")
                .defineInRange("scanRadius", 16, 1, 128)
                .define("enabled", true)
                .defineList("blockedItems", List.of("minecraft:tnt"))
                .defineEnum("mode", "normal", List.of("normal", "strict"))
                .build();
    }

    @Test
    void generatesDefaultsAndCommentsOnFirstRun(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("simplemod.json");
        JsonConfig config = new JsonConfig("simplemod", file);
        config.load().withSpec(spec());

        assertTrue(Files.exists(file), "the file should be written when the spec is attached");
        String text = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(text.contains("\"greeting\""), text);
        assertTrue(text.contains("Printed when a player joins."), text);
        assertTrue(text.contains("Accepts an integer between 1 and 128"), text);

        assertEquals("Welcome!", config.getString("greeting"));
        assertEquals(16, config.getInt("scanRadius"));
        assertTrue(config.getBoolean("enabled"));
        assertEquals(List.of("minecraft:tnt"), config.getStringList("blockedItems"));
    }

    @Test
    void readsBackEditedValues(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("simplemod.json");
        Files.writeString(file, "{\"greeting\":\"Hi\",\"scanRadius\":32,\"enabled\":false}");

        JsonConfig config = new JsonConfig("simplemod", file);
        config.load().withSpec(spec());

        assertEquals("Hi", config.getString("greeting"));
        assertEquals(32, config.getInt("scanRadius"));
        assertFalse(config.getBoolean("enabled"));
        // Keys absent from the file still come from the spec.
        assertEquals("normal", config.getString("mode"));
    }

    @Test
    void anOutOfRangeValueFallsBackToTheDefaultWithoutThrowing(@TempDir Path dir)
            throws IOException {
        ReModLog.reset();
        Path file = dir.resolve("simplemod.json");
        Files.writeString(file, "{\"scanRadius\":9999}");

        JsonConfig config = new JsonConfig("simplemod", file);
        config.load().withSpec(spec());

        assertEquals(16, config.getInt("scanRadius"));
        // The user's value is left in the file so they can see what they typed.
        assertTrue(Files.readString(file).contains("9999"));
    }

    @Test
    void aWrongTypeFallsBackToTheDefault(@TempDir Path dir) throws IOException {
        ReModLog.reset();
        Path file = dir.resolve("simplemod.json");
        Files.writeString(file, "{\"enabled\":\"yes please\",\"scanRadius\":\"lots\"}");

        JsonConfig config = new JsonConfig("simplemod", file);
        config.load().withSpec(spec());

        assertTrue(config.getBoolean("enabled"));
        assertEquals(16, config.getInt("scanRadius"));
    }

    @Test
    void aValueOutsideTheAllowedSetFallsBack(@TempDir Path dir) throws IOException {
        ReModLog.reset();
        Path file = dir.resolve("simplemod.json");
        Files.writeString(file, "{\"mode\":\"chaotic\"}");

        JsonConfig config = new JsonConfig("simplemod", file);
        config.load().withSpec(spec());

        assertEquals("normal", config.getString("mode"));
    }

    @Test
    void aMalformedFileIsLeftAloneAndDefaultsAreUsed(@TempDir Path dir) throws IOException {
        ReModLog.reset();
        Path file = dir.resolve("simplemod.json");
        String broken = "{ not json at all";
        Files.writeString(file, broken);

        JsonConfig config = new JsonConfig("simplemod", file);
        config.load().withSpec(ConfigSpec.builder().define("greeting", "Welcome!").build());

        assertEquals("Welcome!", config.getString("greeting"));
    }

    @Test
    void settingAnInvalidValueThrowsWithAnExplanation(@TempDir Path dir) {
        JsonConfig config = new JsonConfig("simplemod", dir.resolve("simplemod.json"));
        config.load().withSpec(spec());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> config.set("scanRadius", 500));
        assertTrue(error.getMessage().contains("between 1 and 128"), error.getMessage());

        config.set("scanRadius", 64);
        assertEquals(64, config.getInt("scanRadius"));
    }

    @Test
    void savesAndReloadsRoundTrip(@TempDir Path dir) {
        Path file = dir.resolve("simplemod.json");
        JsonConfig config = new JsonConfig("simplemod", file);
        config.load().withSpec(spec());
        config.set("greeting", "Changed").set("scanRadius", 4).save();

        JsonConfig reloaded = new JsonConfig("simplemod", file);
        reloaded.load().withSpec(spec());

        assertEquals("Changed", reloaded.getString("greeting"));
        assertEquals(4, reloaded.getInt("scanRadius"));
    }

    @Test
    void resetToDefaultsRestoresEveryKey(@TempDir Path dir) {
        Path file = dir.resolve("simplemod.json");
        JsonConfig config = new JsonConfig("simplemod", file);
        config.load().withSpec(spec());
        config.set("greeting", "Changed").save();

        config.resetToDefaults();

        assertEquals("Welcome!", config.getString("greeting"));
    }

    @Test
    void unknownKeysInTheFileArePreservedOnSave(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("simplemod.json");
        Files.writeString(file, "{\"customKey\":\"kept\"}");

        JsonConfig config = new JsonConfig("simplemod", file);
        config.load().withSpec(spec());
        config.save();

        assertTrue(Files.readString(file).contains("customKey"));
    }
}
