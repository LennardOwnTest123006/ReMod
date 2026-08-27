package dev.remod.loader;

import dev.remod.api.Side;
import dev.remod.api.game.Identifier;
import dev.remod.common.log.ReModLog;
import dev.remod.loader.resolve.ModLoadError;
import dev.remod.loader.runtime.HeadlessGameBridge;
import dev.remod.loader.runtime.ModContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the whole loader against genuinely compiled mod jars: discovery,
 * resolution, class loading, the lifecycle phases and failure isolation.
 */
class ReModLoaderIntegrationTest {

    @TempDir
    Path gameDir;

    private ReModPaths paths;

    @BeforeEach
    void setUp() throws IOException {
        ReModLog.reset();
        paths = new ReModPaths(gameDir).createDirectories();
    }

    @AfterEach
    void tearDown() {
        dev.remod.api.service.ReModServices.clear();
        ReModLog.reset();
    }

    private ReModLoader loader(Side side) {
        ReModLoader loader = new ReModLoader(paths, "1.21.4", side);
        loader.installBridge(new HeadlessGameBridge("1.21.4", side));
        return loader;
    }

    private static final String GREETER_SOURCE =
            "package dev.test;\n"
            + "import dev.remod.api.*;\n"
            + "import dev.remod.api.game.*;\n"
            + "import dev.remod.api.registry.*;\n"
            + "import dev.remod.api.command.*;\n"
            + "import dev.remod.api.event.lifecycle.*;\n"
            + "import java.nio.file.*;\n"
            + "public class Greeter implements ReModMod {\n"
            + "    public static final java.util.List<String> PHASES ="
            + "            java.util.Collections.synchronizedList(new java.util.ArrayList<>());\n"
            + "    @Override public void onPreInitialize(ReModContext c) { PHASES.add(\"pre\"); }\n"
            + "    @Override public void onInitialize(ReModContext c) {\n"
            + "        PHASES.add(\"init\");\n"
            + "        c.registries().items().register(ItemDefinition.builder("
            + "                Identifier.of(c.modId(), \"ruby\")).maxStackSize(16).build());\n"
            + "        c.registries().blocks().register(BlockDefinition.builder("
            + "                Identifier.of(c.modId(), \"ruby_block\")).build());\n"
            + "        c.commands().register(CommandBuilder.create(c.modId())"
            + "                .description(\"test\").executes(ctx -> 1));\n"
            + "        c.events().subscribe(ModsLoadedEvent.class,"
            + "                e -> PHASES.add(\"loaded:\" + e.modCount()));\n"
            + "        try { Files.createDirectories(c.dataDirectory()); }"
            + "        catch (Exception e) { throw new RuntimeException(e); }\n"
            + "    }\n"
            + "    @Override public void onPostInitialize(ReModContext c) { PHASES.add(\"post\"); }\n"
            + "    @Override public void onClientInitialize(ReModContext c) {"
            + "        PHASES.add(\"client\"); }\n"
            + "    @Override public void onServerInitialize(ReModContext c) {"
            + "        PHASES.add(\"server\"); }\n"
            + "    @Override public void onShutdown(ReModContext c) { PHASES.add(\"shutdown\"); }\n"
            + "}\n";

    private static final String CRASHER_SOURCE =
            "package dev.test;\n"
            + "import dev.remod.api.*;\n"
            + "public class Crasher implements ReModMod {\n"
            + "    @Override public void onInitialize(ReModContext c) {\n"
            + "        throw new IllegalStateException(\"deliberate failure\");\n"
            + "    }\n"
            + "}\n";

    @Test
    void runsTheFullLifecycleAndRegistersContent() throws Exception {
        ModJarCompiler.buildModJar(paths.modsDirectory(), "greeter.jar",
                ModTestFixtures.manifest("greeter").name("Greeter")
                        .entrypoints("dev.test.Greeter").build(),
                Map.of("dev.test.Greeter", GREETER_SOURCE));

        ReModLoader loader = loader(Side.CLIENT);
        LoadReport report = loader.load();

        assertEquals(1, report.loadedCount(), report.errors().toString());
        assertTrue(report.errors().isEmpty(), report.errors().toString());

        ModContainer container = loader.container("greeter").orElseThrow();
        assertEquals(ModContainer.State.SIDE_INITIALISED, container.state());

        List<String> phases = phasesOf(container);
        assertEquals(List.of("pre", "init", "post", "client", "loaded:1"), phases);

        assertEquals(1, loader.registries().items().size());
        assertEquals(1, loader.registries().blocks().size());
        assertTrue(loader.registries().items()
                .contains(Identifier.of("greeter", "ruby")));
        assertTrue(loader.commands().find("greeter").isPresent());
        assertEquals("greeter", loader.commands().ownerOf("greeter").orElseThrow());

        // The headless bridge saw the registrations even though nothing bound.
        HeadlessGameBridge bridge = (HeadlessGameBridge) loader.bridge();
        assertEquals(List.of(Identifier.of("greeter", "ruby")), bridge.recordedItems());
        assertEquals(List.of("greeter"), bridge.recordedCommands());

        assertTrue(Files.isDirectory(paths.dataDirectoryFor("greeter")));
        assertTrue(Files.isRegularFile(paths.configFileFor("greeter"))
                || !Files.exists(paths.configFileFor("greeter")));

        loader.shutdown();
        assertTrue(phasesOf(container).contains("shutdown"));
    }

    @Test
    void runsServerInitOnADedicatedServerAndSkipsClientInit() throws Exception {
        ModJarCompiler.buildModJar(paths.modsDirectory(), "greeter.jar",
                ModTestFixtures.manifest("greeter").entrypoints("dev.test.Greeter").build(),
                Map.of("dev.test.Greeter", GREETER_SOURCE));

        ReModLoader loader = loader(Side.DEDICATED_SERVER);
        loader.load();

        List<String> phases = phasesOf(loader.container("greeter").orElseThrow());
        assertTrue(phases.contains("server"), phases.toString());
        assertFalse(phases.contains("client"), phases.toString());
    }

    @Test
    void oneCrashingModDoesNotStopTheOthers() throws Exception {
        ModJarCompiler.buildModJar(paths.modsDirectory(), "greeter.jar",
                ModTestFixtures.manifest("greeter").entrypoints("dev.test.Greeter").build(),
                Map.of("dev.test.Greeter", GREETER_SOURCE));
        ModJarCompiler.buildModJar(paths.modsDirectory(), "crasher.jar",
                ModTestFixtures.manifest("crasher").name("Crasher")
                        .entrypoints("dev.test.Crasher").build(),
                Map.of("dev.test.Crasher", CRASHER_SOURCE));

        ReModLoader loader = loader(Side.CLIENT);
        LoadReport report = loader.load();

        assertEquals(1, report.loadedCount());
        assertEquals("greeter", report.loaded().get(0).id());

        ModLoadError error = report.errors().get(0);
        assertEquals(ModLoadError.Reason.INITIALISATION_FAILED, error.reason());
        assertEquals("crasher", error.modId());
        assertTrue(error.detail().contains("IllegalStateException"), error.detail());
        assertTrue(error.detail().contains("deliberate failure"), error.detail());
        assertTrue(error.report().contains("This is a bug in Crasher"), error.report());
        assertEquals(ModContainer.State.FAILED,
                loader.container("crasher").orElseThrow().state());
    }

    @Test
    void aModDependingOnACrashedModIsDisabledWithItsOwnExplanation() throws Exception {
        ModJarCompiler.buildModJar(paths.modsDirectory(), "crasher.jar",
                ModTestFixtures.manifest("crasher").entrypoints("dev.test.Crasher").build(),
                Map.of("dev.test.Crasher", CRASHER_SOURCE));
        ModJarCompiler.buildModJar(paths.modsDirectory(), "dependent.jar",
                ModTestFixtures.manifest("dependent").dependencies("crasher")
                        .entrypoints("dev.test.Greeter").build(),
                Map.of("dev.test.Greeter", GREETER_SOURCE));

        ReModLoader loader = loader(Side.CLIENT);
        LoadReport report = loader.load();

        assertEquals(0, report.loadedCount());
        assertTrue(report.errors().stream()
                .anyMatch(e -> e.reason() == ModLoadError.Reason.DEPENDENCY_FAILED
                        && e.modId().equals("dependent")), report.errors().toString());
    }

    @Test
    void reportsAnEntrypointThatDoesNotExist() throws Exception {
        ModTestFixtures.writeModJar(paths.modsDirectory(), "ghost.jar",
                ModTestFixtures.manifest("ghost").entrypoints("dev.test.NotThere").build());

        LoadReport report = loader(Side.CLIENT).load();

        assertEquals(0, report.loadedCount());
        ModLoadError error = report.errors().get(0);
        assertEquals(ModLoadError.Reason.ENTRYPOINT_MISSING, error.reason());
        assertTrue(error.detail().contains("dev.test.NotThere"), error.detail());
        assertTrue(error.solutions().get(0).contains("remod.mod.json"),
                error.solutions().toString());
    }

    @Test
    void reportsAnEntrypointThatIsNotAReModMod() throws Exception {
        ModJarCompiler.buildModJar(paths.modsDirectory(), "wrong.jar",
                ModTestFixtures.manifest("wrong").entrypoints("dev.test.NotAMod").build(),
                Map.of("dev.test.NotAMod",
                        "package dev.test;\npublic class NotAMod { }\n"));

        LoadReport report = loader(Side.CLIENT).load();

        ModLoadError error = report.errors().get(0);
        assertEquals(ModLoadError.Reason.ENTRYPOINT_INVALID, error.reason());
        assertTrue(error.detail().contains("ReModMod"), error.detail());
    }

    @Test
    void loadsMultipleModsInDependencyOrder() throws Exception {
        ModJarCompiler.buildModJar(paths.modsDirectory(), "base.jar",
                ModTestFixtures.manifest("baselib").entrypoints("dev.test.Greeter").build(),
                Map.of("dev.test.Greeter", GREETER_SOURCE));
        ModTestFixtures.writeModJar(paths.modsDirectory(), "top.jar",
                ModTestFixtures.manifest("topmod").dependencies("baselib")
                        .entrypoints("dev.test.Greeter").build());

        ReModLoader loader = loader(Side.CLIENT);
        LoadReport report = loader.load();

        assertEquals(2, report.loadedCount(), report.errors().toString());
        assertEquals("baselib", report.loaded().get(0).id());
        assertEquals("topmod", report.loaded().get(1).id());
        // A mod can see its dependency through the context.
        assertTrue(loader.container("topmod").orElseThrow().context().isModLoaded("baselib"));
    }

    @Test
    void loadsCleanlyWithNoModsInstalled() {
        LoadReport report = loader(Side.CLIENT).load();

        assertEquals(0, report.loadedCount());
        assertTrue(report.isClean());
        assertTrue(Files.isDirectory(paths.modsDirectory()));
    }

    @Test
    void writesItsOwnLogFileUnderTheGameDirectory() throws Exception {
        ReModLog.addFileSink(paths.logsDirectory(), "remod.log");
        loader(Side.CLIENT).load();
        ReModLog.flush();

        Path log = paths.logsDirectory().resolve("remod.log");
        assertTrue(Files.exists(log));
        String text = Files.readString(log);
        assertTrue(text.contains("[ReMod/INFO] Starting ReMod"), text);
        assertTrue(text.contains("ReMod startup completed"), text);
    }

    /** Reads the static PHASES list out of the mod's own class loader. */
    @SuppressWarnings("unchecked")
    private static List<String> phasesOf(ModContainer container) throws Exception {
        Class<?> type = container.entrypoints().get(0).getClass();
        return (List<String>) type.getField("PHASES").get(null);
    }
}
