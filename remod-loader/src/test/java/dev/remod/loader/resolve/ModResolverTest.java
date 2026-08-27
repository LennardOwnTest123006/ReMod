package dev.remod.loader.resolve;

import dev.remod.api.Side;
import dev.remod.api.mod.ModMetadata;
import dev.remod.common.version.ApiVersion;
import dev.remod.loader.ModTestFixtures;
import dev.remod.loader.discovery.ModCandidate;
import dev.remod.loader.discovery.ModSourceKind;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModResolverTest {

    private static final ApiVersion API = ApiVersion.parse("1.21-1.0.0");

    private static ModCandidate candidate(ModTestFixtures.Manifest manifest) {
        String json = manifest.build();
        ModMetadata metadata = ModMetadata.parse(json, "test");
        Path path = Paths.get(metadata.id() + ".jar");
        return new ModCandidate(path, ModSourceKind.JAR, metadata, 1024);
    }

    private static ResolutionResult resolve(ModTestFixtures.Manifest... manifests) {
        return resolve(Side.CLIENT, manifests);
    }

    private static ResolutionResult resolve(Side side, ModTestFixtures.Manifest... manifests) {
        List<ModCandidate> candidates = new ArrayList<>();
        for (ModTestFixtures.Manifest manifest : manifests) {
            candidates.add(candidate(manifest));
        }
        return new ModResolver("1.21.4", API, side).resolve(candidates);
    }

    private static List<String> ids(ResolutionResult result) {
        List<String> ids = new ArrayList<>();
        result.loadOrder().forEach(candidate -> ids.add(candidate.id()));
        return ids;
    }

    @Test
    void acceptsCompatibleMods() {
        ResolutionResult result = resolve(
                ModTestFixtures.manifest("alpha"),
                ModTestFixtures.manifest("beta"));

        assertEquals(2, result.loadedCount());
        assertFalse(result.hasErrors());
    }

    @Test
    void rejectsAModForAnotherMinecraftVersion() {
        ResolutionResult result = resolve(ModTestFixtures.manifest("old").minecraft("1.19.x"));

        assertEquals(0, result.loadedCount());
        ModLoadError error = result.errors().get(0);
        assertEquals(ModLoadError.Reason.INCOMPATIBLE_MINECRAFT, error.reason());
        assertEquals("1.19.x", error.expected());
        assertEquals("1.21.4", error.found());
        assertTrue(error.report().contains("What to do:"));
    }

    @Test
    void rejectsAModBuiltAgainstAnotherApiSeriesAndSaysWhich() {
        ResolutionResult result = resolve(ModTestFixtures.manifest("examplemod")
                .name("ExampleMod").api("1.20-1.0.0"));

        ModLoadError error = result.errors().get(0);
        assertEquals(ModLoadError.Reason.INCOMPATIBLE_API, error.reason());
        assertEquals("1.20-1.0.0", error.expected());
        assertEquals("1.21-1.0.0", error.found());

        String report = error.report();
        assertTrue(report.contains("ReMod Mod Loading Error"), report);
        assertTrue(report.contains("Expected:"), report);
        assertTrue(report.contains("Installed:"), report);
        assertTrue(report.contains("1.20-1.0.0"), report);
        assertTrue(report.contains("1.21-1.0.0"), report);
    }

    @Test
    void rejectsAModNeedingANewerApiBaseline() {
        ResolutionResult result = resolve(ModTestFixtures.manifest("future").api("1.21-1.9.0"));

        ModLoadError error = result.errors().get(0);
        assertEquals(ModLoadError.Reason.INCOMPATIBLE_API, error.reason());
        assertTrue(error.solutions().get(0).contains("Update ReMod"), error.solutions().toString());
    }

    @Test
    void acceptsAModBuiltAgainstAnOlderApiBaseline() {
        ApiVersion newer = ApiVersion.parse("1.21-1.4.0");
        ResolutionResult result = new ModResolver("1.21.4", newer, Side.CLIENT)
                .resolve(List.of(candidate(ModTestFixtures.manifest("older").api("1.21-1.0.0"))));

        assertEquals(1, result.loadedCount());
    }

    @Test
    void rejectsAClientOnlyModOnADedicatedServer() {
        ResolutionResult result = resolve(Side.DEDICATED_SERVER,
                ModTestFixtures.manifest("hudmod").side("client"));

        ModLoadError error = result.errors().get(0);
        assertEquals(ModLoadError.Reason.WRONG_SIDE, error.reason());
        assertTrue(error.solutions().get(0).contains("Remove this mod from the server"));
    }

    @Test
    void keepsTheNewerOfTwoDuplicatesAndNamesTheFileToDelete() {
        ResolutionResult result = resolve(
                ModTestFixtures.manifest("dupe").version("1.0.0"),
                ModTestFixtures.manifest("dupe").version("2.0.0"));

        assertEquals(1, result.loadedCount());
        assertEquals("2.0.0", result.loadOrder().get(0).metadata().version().raw());
        ModLoadError error = result.errors().get(0);
        assertEquals(ModLoadError.Reason.DUPLICATE_MOD, error.reason());
        assertEquals("1.0.0", error.modVersion());
        assertTrue(error.solutions().get(0).startsWith("Delete "), error.solutions().toString());
    }

    @Test
    void rejectsAModWithAMissingDependency() {
        ResolutionResult result = resolve(
                ModTestFixtures.manifest("needy").dependencies("library@>=2.0"));

        ModLoadError error = result.errors().get(0);
        assertEquals(ModLoadError.Reason.MISSING_DEPENDENCY, error.reason());
        assertEquals("library >=2.0", error.expected());
        assertEquals("not installed", error.found());
    }

    @Test
    void rejectsAModWhenTheDependencyVersionIsWrong() {
        ResolutionResult result = resolve(
                ModTestFixtures.manifest("needy").dependencies("library@>=2.0"),
                ModTestFixtures.manifest("library").version("1.5.0"));

        assertEquals(List.of("library"), ids(result));
        ModLoadError error = result.errors().get(0);
        assertEquals(ModLoadError.Reason.UNSATISFIED_DEPENDENCY, error.reason());
        assertEquals("library 1.5.0", error.found());
    }

    @Test
    void cascadesWhenRemovingAModOrphansAnother() {
        // "top" depends on "mid", "mid" depends on the missing "base".
        ResolutionResult result = resolve(
                ModTestFixtures.manifest("mid").dependencies("base"),
                ModTestFixtures.manifest("top").dependencies("mid"));

        assertEquals(0, result.loadedCount());
        assertEquals(2, result.errors().size());
    }

    @Test
    void sortsDependenciesBeforeDependents() {
        ResolutionResult result = resolve(
                ModTestFixtures.manifest("zzz").dependencies("middle"),
                ModTestFixtures.manifest("middle").dependencies("base"),
                ModTestFixtures.manifest("base"));

        assertEquals(List.of("base", "middle", "zzz"), ids(result));
    }

    @Test
    void orderIsDeterministicForUnrelatedMods() {
        List<String> first = ids(resolve(
                ModTestFixtures.manifest("charlie"),
                ModTestFixtures.manifest("alpha"),
                ModTestFixtures.manifest("bravo")));
        List<String> second = ids(resolve(
                ModTestFixtures.manifest("bravo"),
                ModTestFixtures.manifest("charlie"),
                ModTestFixtures.manifest("alpha")));

        assertEquals(first, second);
        assertEquals(List.of("alpha", "bravo", "charlie"), first);
    }

    @Test
    void rejectsEveryModInADependencyCycle() {
        ResolutionResult result = resolve(
                ModTestFixtures.manifest("one").dependencies("two"),
                ModTestFixtures.manifest("two").dependencies("one"));

        assertEquals(0, result.loadedCount());
        assertTrue(result.errors().stream()
                .allMatch(e -> e.reason() == ModLoadError.Reason.DEPENDENCY_CYCLE));
    }

    @Test
    void rejectsAModDeclaredIncompatibleWithAnInstalledMod() {
        ResolutionResult result = resolve(
                ModTestFixtures.manifest("picky").incompatible("rival"),
                ModTestFixtures.manifest("rival"));

        assertEquals(List.of("rival"), ids(result));
        ModLoadError error = result.errors().get(0);
        assertEquals(ModLoadError.Reason.INCOMPATIBLE_MOD, error.reason());
        assertTrue(error.solutions().get(0).contains("Remove either"));
    }

    @Test
    void optionalDependenciesDoNotBlockLoadingButAreOrdered() {
        ResolutionResult result = resolve(
                ModTestFixtures.manifest("main").optionalDependencies("addon", "absent"),
                ModTestFixtures.manifest("addon"));

        assertEquals(List.of("addon", "main"), ids(result));
        assertFalse(result.hasErrors());
    }

    @Test
    void warnsWhenAnOptionalDependencyIsPresentButTooOld() {
        ResolutionResult result = resolve(
                ModTestFixtures.manifest("main").optionalDependencies("addon@>=2.0"),
                ModTestFixtures.manifest("addon").version("1.0.0"));

        assertEquals(2, result.loadedCount());
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("integration features will be disabled"));
    }
}
