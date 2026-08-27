package dev.remod.cli;

import dev.remod.common.log.ReModLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReModCliTest {

    private ByteArrayOutputStream out;
    private ByteArrayOutputStream err;
    private Console console;
    private ReModCli cli;

    @BeforeEach
    void setUp() {
        ReModLog.reset();
        out = new ByteArrayOutputStream();
        err = new ByteArrayOutputStream();
        console = new Console(new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        cli = new ReModCli();
    }

    private String stdout() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private String stderr() {
        return err.toString(StandardCharsets.UTF_8);
    }

    @Test
    void printsHelpWithNoArguments() {
        assertEquals(0, cli.run(new String[0], console));

        String text = stdout();
        for (String command : cli.commands().keySet()) {
            assertTrue(text.contains(command), "help should mention " + command);
        }
        assertTrue(text.contains("tutorial.txt"), text);
    }

    @Test
    void printsHelpForOneCommand() {
        assertEquals(0, cli.run(new String[]{"help", "create"}, console));

        String text = stdout();
        assertTrue(text.contains("remod create"), text);
        assertTrue(text.contains("--package"), text);
    }

    @Test
    void printsTheVersion() {
        assertEquals(0, cli.run(new String[]{"version"}, console));
        assertTrue(stdout().contains("ReMod 1.0.0"), stdout());
    }

    @Test
    void reportsAnUnknownCommandWithASuggestion() {
        assertEquals(2, cli.run(new String[]{"frobnicate"}, console));
        assertTrue(stderr().contains("Unknown command"), stderr());
        assertTrue(stderr().contains("remod help"), stderr());
    }

    @Test
    void createScaffoldsABuildableProject(@TempDir Path dir) throws Exception {
        int exit = cli.run(new String[]{
                "create", "My Test Mod",
                "--package", "dev.example.testmod",
                "--minecraft", "1.21.4",
                "--author", "Tester",
                "--directory", dir.resolve("MyTestMod").toString()}, console);

        assertEquals(0, exit, stderr());
        Path root = dir.resolve("MyTestMod");
        assertTrue(Files.isRegularFile(root.resolve("build.gradle")));
        assertTrue(Files.isRegularFile(root.resolve("settings.gradle")));
        assertTrue(Files.isRegularFile(root.resolve("gradle.properties")));
        assertTrue(Files.isRegularFile(root.resolve("README.md")));

        Path manifest = root.resolve("src/main/resources/remod.mod.json");
        assertTrue(Files.isRegularFile(manifest));
        String json = Files.readString(manifest, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"id\": \"mytestmod\""), json);
        assertTrue(json.contains("\"remod_api\": \"1.21-1.0.0\""), json);
        assertTrue(json.contains("dev.example.testmod.MyTestMod"), json);

        Path source = root.resolve("src/main/java/dev/example/testmod/MyTestMod.java");
        assertTrue(Files.isRegularFile(source));
        String java = Files.readString(source, StandardCharsets.UTF_8);
        assertTrue(java.contains("implements ReModMod"), java);
        assertTrue(java.contains("public void onInitialize"), java);
    }

    @Test
    void theGeneratedManifestIsAcceptedByTheRealParser(@TempDir Path dir) throws Exception {
        cli.run(new String[]{"create", "Parsed Mod", "--minecraft", "1.20.1",
                "--directory", dir.resolve("p").toString()}, console);

        String json = Files.readString(
                dir.resolve("p/src/main/resources/remod.mod.json"), StandardCharsets.UTF_8);
        // processResources fills this in at build time; substitute it here.
        json = json.replace("${modVersion}", "1.0.0");

        dev.remod.api.mod.ModMetadata metadata =
                dev.remod.api.mod.ModMetadata.parse(json, "generated");
        assertEquals("parsedmod", metadata.id());
        assertEquals("1.20-1.0.0", metadata.apiVersion().toString());
        assertTrue(metadata.minecraft().matches("1.20.1"));
    }

    @Test
    void refusesToOverwriteANonEmptyDirectory(@TempDir Path dir) throws Exception {
        Path target = dir.resolve("existing");
        Files.createDirectories(target);
        Files.writeString(target.resolve("important.txt"), "do not delete me");

        int exit = cli.run(new String[]{"create", "Thing",
                "--directory", target.toString()}, console);

        assertEquals(1, exit);
        assertTrue(stderr().contains("already exists"), stderr());
        assertEquals("do not delete me",
                Files.readString(target.resolve("important.txt"), StandardCharsets.UTF_8));
    }

    @Test
    void refusesAMinecraftVersionWithNoApi(@TempDir Path dir) {
        int exit = cli.run(new String[]{"create", "Thing", "--minecraft", "24w14a",
                "--directory", dir.resolve("t").toString()}, console);

        assertEquals(1, exit);
        assertTrue(stderr().contains("no API for Minecraft"), stderr());
    }

    @Test
    void createNeedsAName() {
        assertEquals(2, cli.run(new String[]{"create"}, console));
        assertTrue(stderr().contains("No project name"), stderr());
    }

    @Test
    void initCreatesTheRemodTree(@TempDir Path dir) {
        assertEquals(0, cli.run(new String[]{"init", "--directory", dir.toString()}, console));

        assertTrue(Files.isDirectory(dir.resolve("remod/mods")));
        assertTrue(Files.isDirectory(dir.resolve("remod/config")));
        assertTrue(Files.isDirectory(dir.resolve("remod/logs")));
        assertTrue(stdout().contains("ReMod folders ready"), stdout());
    }

    @Test
    void testCommandLoadsModsAndReportsRegistrations(@TempDir Path dir) throws Exception {
        Path mods = dir.resolve("libs");
        Files.createDirectories(mods);
        writeManifestOnlyJar(mods.resolve("broken.jar"),
                "{\"id\":\"badmod\",\"version\":\"1.0.0\",\"minecraft\":\"1.21.x\","
                        + "\"remod_api\":\"1.21-1.0.0\","
                        + "\"entrypoints\":[\"dev.nowhere.Missing\"]}");

        int exit = cli.run(new String[]{"test", "--mods", mods.toString()}, console);

        // A mod that cannot load is a failure exit code, with a full report.
        assertEquals(1, exit);
        String text = stdout();
        assertTrue(text.contains("Mods loaded:           0"), text);
        assertTrue(text.contains("Entrypoint class not found"), text);
        assertTrue(text.contains("dev.nowhere.Missing"), text);
    }

    @Test
    void testCommandNeedsAModsFolder(@TempDir Path dir) {
        assertEquals(2, cli.run(new String[]{"test"}, console));
        assertTrue(stderr().contains("No mods folder"), stderr());

        assertEquals(1, cli.run(new String[]{
                "test", "--mods", dir.resolve("missing").toString()}, console));
        assertTrue(stderr().contains("is not a folder"), stderr());
    }

    @Test
    void buildRefusesADirectoryThatIsNotAGradleProject(@TempDir Path dir) {
        int exit = cli.run(new String[]{"build", "--directory", dir.toString()}, console);

        assertEquals(1, exit);
        assertTrue(stderr().contains("does not look like a Gradle project"), stderr());
        assertTrue(stderr().contains("remod create"), stderr());
    }

    @Test
    void listInstallsReportsAnEmptyDirectory(@TempDir Path dir) {
        assertEquals(0, cli.run(new String[]{
                "list", "installs", "--directory", dir.toString()}, console));
        assertTrue(stdout().contains("None."), stdout());
    }

    @Test
    void listLoadersPrintsTheCompatibilityMatrix(@TempDir Path dir) {
        assertEquals(0, cli.run(new String[]{
                "list", "loaders", "--directory", dir.toString()}, console));

        String text = stdout();
        assertTrue(text.contains("Fabric"), text);
        assertTrue(text.contains("Paper"), text);
        assertTrue(text.contains("Coexistence only"), text);
        assertTrue(text.contains("Not possible"), text);
        assertFalse(text.contains("docs/compatibility.md") && text.contains("fully supported"));
    }

    @Test
    void listRejectsAnUnknownSubject(@TempDir Path dir) {
        assertEquals(2, cli.run(new String[]{
                "list", "nonsense", "--directory", dir.toString()}, console));
        assertTrue(stderr().contains("Unknown list"), stderr());
    }

    @Test
    void uninstallReportsWhenNothingIsInstalled(@TempDir Path dir) {
        assertEquals(1, cli.run(new String[]{
                "uninstall", "1.21.4", "--directory", dir.toString()}, console));
        assertTrue(stderr().contains("not installed"), stderr());
    }

    private static void writeManifestOnlyJar(Path jar, String manifest) throws Exception {
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(
                Files.newOutputStream(jar))) {
            zip.putNextEntry(new java.util.zip.ZipEntry("remod.mod.json"));
            zip.write(manifest.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }
}
