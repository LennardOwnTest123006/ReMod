package dev.remod.installer.install;

import dev.remod.common.json.Json;
import dev.remod.common.json.JsonObject;
import dev.remod.common.log.ReModLog;
import dev.remod.compat.CompatibilityRegistry;
import dev.remod.loader.ReModPaths;
import org.junit.jupiter.api.BeforeEach;
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

/**
 * Covers the install pipeline against a simulated {@code .minecraft} directory,
 * with particular attention to the safety guarantees: nothing outside ReMod's
 * own files is created, modified or removed.
 */
class InstallerTest {

    @TempDir
    Path minecraft;

    private ReModPaths paths;
    private ReModInstaller installer;

    /** Stands in for the jars the real build bundles into ReMod.jar. */
    private static BundledLibraries fakeLibraries() {
        return BundledLibraries.of(List.of(
                new BundledLibraries.Library("dev.remod", "remod-common", "1.0.0",
                        "remod-common-1.0.0.jar"),
                new BundledLibraries.Library("dev.remod", "remod-api", "1.0.0",
                        "remod-api-1.0.0.jar"),
                new BundledLibraries.Library("dev.remod", "remod-loader", "1.0.0",
                        "remod-loader-1.0.0.jar")));
    }

    @BeforeEach
    void setUp() throws IOException {
        ReModLog.reset();
        paths = new ReModPaths(minecraft);
        Files.createDirectories(paths.versionsDirectory());
        Files.createDirectories(minecraft.resolve("assets"));
        Files.writeString(paths.launcherProfilesFile(),
                "{\"profiles\":{\"vanilla-latest\":{\"name\":\"Latest Release\","
                        + "\"type\":\"latest-release\"}},\"selectedProfile\":\"vanilla-latest\","
                        + "\"clientToken\":\"keep-me\"}");
        installer = new ReModInstaller(fakeLibraries(), CompatibilityRegistry.standard());
    }

    private InstallRequest request(String version) {
        return InstallRequest.builder(version, minecraft).build();
    }

    // --- validation -------------------------------------------------------

    @Test
    void refusesAnUnsupportedMinecraftVersion() {
        InstallException error = assertThrows(InstallException.class,
                () -> installer.validate(request("1.8.9")));
        assertTrue(error.getMessage().contains("1.8.9"), error.getMessage());
        assertTrue(error.suggestion().contains("1.17 or newer"), error.suggestion());
    }

    @Test
    void refusesASnapshotRatherThanGuessingItsSeries() {
        assertThrows(InstallException.class, () -> installer.validate(request("24w14a")));
    }

    @Test
    void refusesAFolderThatIsNotAMinecraftInstallation(@TempDir Path elsewhere) {
        InstallException error = assertThrows(InstallException.class,
                () -> installer.validate(InstallRequest.builder("1.21.4", elsewhere).build()));
        assertTrue(error.suggestion().contains("launcher_profiles.json"), error.suggestion());
    }

    @Test
    void refusesAMissingFolder() {
        InstallException error = assertThrows(InstallException.class,
                () -> installer.validate(
                        InstallRequest.builder("1.21.4", minecraft.resolve("nope")).build()));
        assertTrue(error.getMessage().contains("does not exist"), error.getMessage());
    }

    @Test
    void acceptsASupportedVersionInARealLookingInstallation() {
        installer.validate(request("1.21.4"));
        installer.validate(request("1.20.1"));
    }

    // --- the install itself ----------------------------------------------

    @Test
    void writesALauncherVersionThatInheritsFromVanilla() throws IOException {
        InstallResult result = installerWithRealResources().install(request("1.21.4"), null);

        assertEquals("ReMod-1.21.4", result.versionId());
        Path json = result.versionDirectory().resolve("ReMod-1.21.4.json");
        assertTrue(Files.isRegularFile(json));

        JsonObject version = Json.parseObject(Files.readString(json, StandardCharsets.UTF_8));
        assertEquals("ReMod-1.21.4", version.getString("id"));
        // Inheriting rather than copying is what keeps Mojang's files untouched.
        assertEquals("1.21.4", version.getString("inheritsFrom"));
        assertEquals("dev.remod.loader.launch.ReModLaunch", version.getString("mainClass"));
        assertEquals(3, version.getArray("libraries").size());
        assertEquals("dev.remod:remod-common:1.0.0",
                version.getArray("libraries").getObject(0).getString("name"));
        assertEquals("dev.remod:remod-loader:1.0.0",
                version.getArray("libraries").getObject(2).getString("name"));
        assertTrue(version.getObject("arguments").getArray("jvm").asStringList()
                .contains("-Dremod.minecraftVersion=1.21.4"));
        assertEquals("1.21-1.0.0", version.getObject("remod").getString("apiVersion"));
    }

    @Test
    void failsClearlyWhenReModJarWasBuiltWithoutItsLibraries() {
        // fakeLibraries() names files that are not on the classpath, which is
        // what an incomplete build would look like.
        InstallException error = assertThrows(InstallException.class,
                () -> installer.install(request("1.21.4"), null));
        assertTrue(error.getMessage().contains("missing from ReMod.jar"), error.getMessage());
        assertTrue(error.suggestion().contains("gradlew build"), error.suggestion());
    }

    @Test
    void placesLibrariesInMavenLayout() {
        InstallResult result = installerWithRealResources().install(request("1.21.4"), null);

        assertEquals(3, result.librariesInstalled());
        assertTrue(Files.isRegularFile(paths.librariesDirectory()
                .resolve("dev/remod/remod-api/1.0.0/remod-api-test.jar")));
        assertTrue(Files.isRegularFile(paths.librariesDirectory()
                .resolve("dev/remod/remod-loader/1.0.0/remod-loader-test.jar")));
    }

    @Test
    void installsTheApiJarForModDevelopment() {
        InstallResult result = installerWithRealResources().install(request("1.21.4"), null);

        assertTrue(Files.isRegularFile(result.apiJar()), String.valueOf(result.apiJar()));
        // Named after the Minecraft series, so a developer knows which API it is.
        assertEquals("remod-api-1.21-1.0.0.jar", result.apiJar().getFileName().toString());
    }

    @Test
    void addsALauncherProfileWithoutDisturbingExistingOnes() throws IOException {
        installerWithRealResources().install(request("1.21.4"), null);

        JsonObject profiles = Json.parseObject(
                Files.readString(paths.launcherProfilesFile(), StandardCharsets.UTF_8));
        // The user's own data survives untouched.
        assertEquals("keep-me", profiles.getString("clientToken"));
        assertEquals("vanilla-latest", profiles.getString("selectedProfile"));
        assertTrue(profiles.getObject("profiles").has("vanilla-latest"));

        JsonObject remod = profiles.getObject("profiles").getObject("remod-1.21.4");
        assertEquals("ReMod 1.21.4", remod.getString("name"));
        assertEquals("ReMod-1.21.4", remod.getString("lastVersionId"));
        assertEquals("custom", remod.getString("type"));
    }

    @Test
    void backsUpTheLauncherProfilesBeforeTheFirstChange() throws IOException {
        String original = Files.readString(paths.launcherProfilesFile(), StandardCharsets.UTF_8);

        installerWithRealResources().install(request("1.21.4"), null);

        Path backup = paths.launcherProfilesFile()
                .resolveSibling("launcher_profiles.json" + LauncherProfileWriter.BACKUP_SUFFIX);
        assertTrue(Files.isRegularFile(backup));
        assertEquals(original, Files.readString(backup, StandardCharsets.UTF_8));
    }

    @Test
    void refusesToInstallWhenLauncherProfilesIsMissing() throws IOException {
        Files.delete(paths.launcherProfilesFile());

        InstallException error = assertThrows(InstallException.class,
                () -> installerWithRealResources().install(request("1.21.4"), null));
        assertTrue(error.suggestion().contains("official Minecraft Launcher"),
                error.suggestion());
    }

    @Test
    void refusesToInstallWhenLauncherProfilesIsCorruptRatherThanReplacingIt() throws IOException {
        Files.writeString(paths.launcherProfilesFile(), "{ broken json");

        InstallException error = assertThrows(InstallException.class,
                () -> installerWithRealResources().install(request("1.21.4"), null));
        assertTrue(error.suggestion().contains("will not overwrite"), error.suggestion());
        // The damaged file is left exactly as it was.
        assertEquals("{ broken json",
                Files.readString(paths.launcherProfilesFile(), StandardCharsets.UTF_8));
    }

    @Test
    void neverTouchesVanillaVersionsOrUserContent() throws IOException {
        Path vanilla = paths.versionsDirectory().resolve("1.21.4");
        Files.createDirectories(vanilla);
        Files.writeString(vanilla.resolve("1.21.4.json"), "{\"id\":\"1.21.4\"}");
        Path world = minecraft.resolve("saves/My World/level.dat");
        Files.createDirectories(world.getParent());
        Files.writeString(world, "precious");
        Path otherMods = minecraft.resolve("mods/some-fabric-mod.jar");
        Files.createDirectories(otherMods.getParent());
        Files.writeString(otherMods, "fabric mod");

        installerWithRealResources().install(request("1.21.4"), null);

        assertEquals("{\"id\":\"1.21.4\"}",
                Files.readString(vanilla.resolve("1.21.4.json"), StandardCharsets.UTF_8));
        assertEquals("precious", Files.readString(world, StandardCharsets.UTF_8));
        assertEquals("fabric mod", Files.readString(otherMods, StandardCharsets.UTF_8));
    }

    @Test
    void createsTheModsFolderTheResultPointsAt() {
        InstallResult result = installerWithRealResources().install(request("1.21.4"), null);

        assertEquals(paths.modsDirectory(), result.modsDirectory());
        assertTrue(Files.isDirectory(result.modsDirectory()));
        assertTrue(result.summary().contains("Put your mods in"));
        assertTrue(result.summary().contains("ReMod 1.21.4"));
    }

    @Test
    void reinstallingIsIdempotent() throws IOException {
        ReModInstaller real = installerWithRealResources();
        real.install(request("1.21.4"), null);
        String firstJson = Files.readString(
                paths.versionsDirectory().resolve("ReMod-1.21.4/ReMod-1.21.4.json"));

        real.install(request("1.21.4"), null);

        assertEquals(1, InstalledVersions.scan(paths).size());
        assertFalse(firstJson.isEmpty());
    }

    @Test
    void reportsOtherLoadersItFinds() throws IOException {
        Files.createDirectories(paths.versionsDirectory().resolve("fabric-loader-0.15.0-1.21.4"));

        InstallResult result = installerWithRealResources().install(request("1.21.4"), null);

        assertTrue(result.notes().stream().anyMatch(note -> note.contains("Fabric")),
                result.notes().toString());
    }

    // --- listing and uninstalling ----------------------------------------

    @Test
    void listsInstallationsByTheirContentNotTheirName() throws IOException {
        installerWithRealResources().install(request("1.21.4"), null);
        installerWithRealResources().install(request("1.20.1"), null);
        // A decoy directory whose name looks like ours but is not.
        Path decoy = paths.versionsDirectory().resolve("ReMod-9.9.9");
        Files.createDirectories(decoy);
        Files.writeString(decoy.resolve("ReMod-9.9.9.json"), "{\"id\":\"ReMod-9.9.9\"}");

        List<InstalledVersions.Installed> installed = InstalledVersions.scan(paths);

        assertEquals(2, installed.size());
        assertTrue(InstalledVersions.isInstalled(paths, "1.21.4"));
        assertTrue(InstalledVersions.isInstalled(paths, "1.20.1"));
        assertFalse(InstalledVersions.isInstalled(paths, "9.9.9"));
    }

    @Test
    void uninstallRemovesOnlyReModsOwnFiles() throws IOException {
        installerWithRealResources().install(request("1.21.4"), null);
        Path modJar = paths.modsDirectory().resolve("my-mod.jar");
        Files.writeString(modJar, "a mod the user installed");
        Path world = minecraft.resolve("saves/My World/level.dat");
        Files.createDirectories(world.getParent());
        Files.writeString(world, "precious");

        ReModUninstaller.Result result = new ReModUninstaller(paths).uninstall("1.21.4");

        assertFalse(Files.exists(paths.versionsDirectory().resolve("ReMod-1.21.4")));
        assertFalse(Json.parseObject(Files.readString(paths.launcherProfilesFile()))
                .getObject("profiles").has("remod-1.21.4"));
        // Mods, configs and worlds are deliberately kept.
        assertTrue(Files.exists(modJar));
        assertEquals("precious", Files.readString(world, StandardCharsets.UTF_8));
        assertTrue(result.kept().stream().anyMatch(note -> note.contains("mods")),
                result.kept().toString());
        assertTrue(result.summary().contains("Kept:"));
    }

    @Test
    void uninstallRefusesADirectoryItDidNotCreate() throws IOException {
        Path decoy = paths.versionsDirectory().resolve("ReMod-1.21.4");
        Files.createDirectories(decoy);
        Files.writeString(decoy.resolve("ReMod-1.21.4.json"), "{\"id\":\"something else\"}");

        InstallException error = assertThrows(InstallException.class,
                () -> new ReModUninstaller(paths).uninstall("1.21.4"));
        assertTrue(error.getMessage().contains("not created by ReMod"), error.getMessage());
        assertTrue(Files.exists(decoy));
    }

    @Test
    void uninstallingSomethingNotInstalledSaysSoAndChangesNothing() {
        InstallException error = assertThrows(InstallException.class,
                () -> new ReModUninstaller(paths).uninstall("1.21.4"));
        assertTrue(error.getMessage().contains("not installed"), error.getMessage());
        assertTrue(error.suggestion().contains("Nothing was changed"));
    }

    /**
     * An installer whose bundled libraries are backed by real files on the test
     * classpath, so the whole pipeline runs end to end.
     */
    private ReModInstaller installerWithRealResources() {
        return new ReModInstaller(TestLibraries.create(), CompatibilityRegistry.standard());
    }
}
