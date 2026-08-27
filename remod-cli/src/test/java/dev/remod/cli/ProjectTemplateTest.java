package dev.remod.cli;

import dev.remod.cli.command.ProjectTemplate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectTemplateTest {

    @Test
    void derivesALegalModIdFromAnyProjectName() {
        assertEquals("mymod", ProjectTemplate.toModId("MyMod"));
        assertEquals("mysupermod", ProjectTemplate.toModId("My Super Mod!"));
        assertEquals("mod123", ProjectTemplate.toModId("123"));
        assertEquals("test", ProjectTemplate.toModId("-test-"));
        // Too short for the manifest's two-character minimum, so it is padded.
        assertEquals("amod", ProjectTemplate.toModId("a"));
    }

    @Test
    void derivesALegalClassNameFromAnyProjectName() {
        assertEquals("MyMod", ProjectTemplate.toClassName("MyMod"));
        assertEquals("MySuperMod", ProjectTemplate.toClassName("my super mod"));
        assertEquals("MyMod", ProjectTemplate.toClassName("my-mod"));
        assertTrue(Character.isJavaIdentifierStart(
                ProjectTemplate.toClassName("9lives").charAt(0)));
    }

    @Test
    void theGeneratedIdsAreAcceptedByTheManifestValidator() {
        // The mod id rules live in ModMetadata; the template must satisfy them.
        for (String name : new String[]{"MyMod", "My Super Mod!", "123", "a"}) {
            String modId = ProjectTemplate.toModId(name);
            String json = "{\"id\":\"" + modId + "\",\"version\":\"1.0.0\","
                    + "\"minecraft\":\"1.21.x\",\"remod_api\":\""
                    + dev.remod.loader.ReModVersions.apiBaseline() + "\","
                    + "\"entrypoints\":[\"dev.example.Main\"]}";
            dev.remod.api.mod.ModMetadata metadata =
                    dev.remod.api.mod.ModMetadata.parse(json, name);
            assertEquals(modId, metadata.id());
        }
    }

    @Test
    void refusesAMinecraftVersionWithNoApi() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProjectTemplate("Thing", "dev.example", "24w14a", "Author"));
    }

    @Test
    void reportsTheApiVersionForTheChosenMinecraftVersion() {
        ProjectTemplate template =
                new ProjectTemplate("Thing", "dev.example.thing", "1.20.1", "Author");

        // Read the baseline rather than hard-coding it, so bumping the API
        // surface does not break this test.
        assertEquals("1.20-" + dev.remod.loader.ReModVersions.apiBaseline(),
                template.apiVersion().toString());
        assertEquals("thing", template.modId());
        assertEquals("dev.example.thing.Thing", template.mainClass());
    }
}
