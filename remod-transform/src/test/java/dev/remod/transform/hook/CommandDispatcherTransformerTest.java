package dev.remod.transform.hook;

import dev.remod.common.log.ReModLog;
import dev.remod.transform.load.TransformingClassLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the transformation mechanism end to end.
 *
 * <p>Real Minecraft is not available to a build machine, so these tests compile
 * stand-in classes shaped the way the game's are -- a class holding a dispatcher
 * as instance state, assigned in its constructor -- load them through the real
 * {@link TransformingClassLoader}, and check that the injected hook fires with
 * the right object.</p>
 *
 * <p>What this does <em>not</em> prove is that Minecraft's actual command class
 * has that shape on every version. That is stated in the release notes rather
 * than assumed here.</p>
 */
class CommandDispatcherTransformerTest {

    private static final String DISPATCHER_DESCRIPTOR = FakeDispatcher.DESCRIPTOR;

    /** Shaped like Minecraft's Commands class: dispatcher field, built in the constructor. */
    private static final String GAME_COMMANDS_SOURCE =
            "package net.minecraft.commands;\n"
            + "import dev.remod.transform.hook.FakeDispatcher;\n"
            + "public class Commands {\n"
            + "    private final FakeDispatcher dispatcher;\n"
            + "    public Commands() {\n"
            + "        this.dispatcher = new FakeDispatcher();\n"
            + "        this.dispatcher.register(\"vanilla-gamemode\");\n"
            + "        this.dispatcher.register(\"vanilla-time\");\n"
            + "    }\n"
            + "    public FakeDispatcher dispatcher() { return dispatcher; }\n"
            + "}\n";

    /** A game class with no dispatcher: must be left completely alone. */
    private static final String UNRELATED_SOURCE =
            "package net.minecraft.world;\n"
            + "public class Level {\n"
            + "    public int answer() { return 42; }\n"
            + "}\n";

    @TempDir
    Path workspace;

    private URL[] gameClasspath;

    @BeforeEach
    void setUp() throws Exception {
        ReModLog.reset();
        ReModHooks.reset();
        Path classes = compile(Map.of(
                "net.minecraft.commands.Commands", GAME_COMMANDS_SOURCE,
                "net.minecraft.world.Level", UNRELATED_SOURCE));
        gameClasspath = new URL[]{classes.toUri().toURL()};
    }

    @AfterEach
    void tearDown() {
        ReModHooks.reset();
        ReModLog.reset();
    }

    @Test
    void injectsAHookThatFiresWithTheGamesOwnDispatcher() throws Exception {
        AtomicReference<Object> captured = new AtomicReference<>();
        ReModHooks.onCommandDispatcherAvailable(captured::set);

        TransformingClassLoader loader =
                new TransformingClassLoader(gameClasspath, getClass().getClassLoader())
                        .register(new CommandDispatcherTransformer(DISPATCHER_DESCRIPTOR));

        Class<?> commands = loader.loadClass("net.minecraft.commands.Commands");
        Object instance = commands.getConstructor().newInstance();

        // The hook fired during construction, before anyone asked for it.
        assertNotNull(captured.get(), "the hook should have fired from the constructor");

        Object dispatcher = commands.getMethod("dispatcher").invoke(instance);
        assertSame(dispatcher, captured.get(),
                "the hook must hand over the game's own dispatcher, not a copy");

        // And it is fully built: vanilla's own registrations already happened,
        // which is why the hook goes at the end of the constructor.
        FakeDispatcher fake = (FakeDispatcher) dispatcher;
        assertEquals(List.of("vanilla-gamemode", "vanilla-time"), fake.registered());
    }

    @Test
    void findsTheCommandClassWithoutKnowingItsName() throws Exception {
        CommandDispatcherTransformer transformer =
                new CommandDispatcherTransformer(DISPATCHER_DESCRIPTOR);
        TransformingClassLoader loader =
                new TransformingClassLoader(gameClasspath, getClass().getClassLoader())
                        .register(transformer);

        loader.loadClass("net.minecraft.world.Level");
        assertNull(transformer.hookedClass());

        loader.loadClass("net.minecraft.commands.Commands");

        // Identified purely by the type of its field, which is what makes this
        // work on an obfuscated jar where the class name is meaningless.
        assertEquals("net/minecraft/commands/Commands", transformer.hookedClass());
    }

    @Test
    void leavesClassesWithoutADispatcherUntouched() throws Exception {
        TransformingClassLoader loader =
                new TransformingClassLoader(gameClasspath, getClass().getClassLoader())
                        .register(new CommandDispatcherTransformer(DISPATCHER_DESCRIPTOR));

        Class<?> level = loader.loadClass("net.minecraft.world.Level");
        Object instance = level.getConstructor().newInstance();

        assertEquals(42, level.getMethod("answer").invoke(instance));
        assertEquals(0, loader.transformedCount());
        assertFalse(ReModHooks.isCommandDispatcherAvailable());
    }

    @Test
    void aListenerRegisteredAfterTheGameStartedStillGetsTheDispatcher() throws Exception {
        TransformingClassLoader loader =
                new TransformingClassLoader(gameClasspath, getClass().getClassLoader())
                        .register(new CommandDispatcherTransformer(DISPATCHER_DESCRIPTOR));
        Class<?> commands = loader.loadClass("net.minecraft.commands.Commands");
        commands.getConstructor().newInstance();

        // Registered late: the dispatcher has been and gone.
        AtomicReference<Object> captured = new AtomicReference<>();
        ReModHooks.onCommandDispatcherAvailable(captured::set);

        assertNotNull(captured.get(), "a late listener must not wait forever");
    }

    @Test
    void aListenerThatThrowsDoesNotTakeTheGameDown() throws Exception {
        ReModHooks.onCommandDispatcherAvailable(dispatcher -> {
            throw new IllegalStateException("a mod's listener is broken");
        });
        AtomicReference<Object> survivor = new AtomicReference<>();
        ReModHooks.onCommandDispatcherAvailable(survivor::set);

        TransformingClassLoader loader =
                new TransformingClassLoader(gameClasspath, getClass().getClassLoader())
                        .register(new CommandDispatcherTransformer(DISPATCHER_DESCRIPTOR));
        Class<?> commands = loader.loadClass("net.minecraft.commands.Commands");

        // Constructing the game class must succeed despite the broken listener.
        commands.getConstructor().newInstance();

        assertNotNull(survivor.get(), "the other listener should still have run");
    }

    @Test
    void theTransformedClassStillBehavesNormally() throws Exception {
        TransformingClassLoader loader =
                new TransformingClassLoader(gameClasspath, getClass().getClassLoader())
                        .register(new CommandDispatcherTransformer(DISPATCHER_DESCRIPTOR));

        Class<?> commands = loader.loadClass("net.minecraft.commands.Commands");
        Object first = commands.getConstructor().newInstance();
        Object second = commands.getConstructor().newInstance();

        // Injecting a call must not have disturbed the constructor's own work.
        assertNotNull(commands.getMethod("dispatcher").invoke(first));
        assertNotNull(commands.getMethod("dispatcher").invoke(second));
        assertEquals(1, loader.transformedCount());
    }

    private static void assertNull(Object value) {
        org.junit.jupiter.api.Assertions.assertNull(value);
    }

    /** Compiles sources into a directory that stands in for the Minecraft jar. */
    private Path compile(Map<String, String> sources) throws IOException {
        Path classes = workspace.resolve("game-classes");
        Files.createDirectories(classes);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("These tests need a JDK, not a JRE");
        }
        List<JavaFileObject> units = new ArrayList<>();
        sources.forEach((name, source) -> units.add(new SimpleJavaFileObject(
                URI.create("string:///" + name.replace('.', '/') + ".java"),
                JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        }));
        StringWriter diagnostics = new StringWriter();
        try (StandardJavaFileManager files =
                     compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            boolean ok = compiler.getTask(diagnostics, files, null,
                    List.of("-classpath", System.getProperty("java.class.path"),
                            "-d", classes.toString()),
                    null, units).call();
            if (!ok) {
                throw new IllegalStateException("Stand-in game classes failed to compile:\n"
                        + diagnostics);
            }
        }
        return classes;
    }
}
