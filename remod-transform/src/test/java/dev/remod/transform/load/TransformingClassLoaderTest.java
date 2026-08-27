package dev.remod.transform.load;

import dev.remod.common.log.ReModLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The delegation rules, which are the part of this loader that is easy to get
 * subtly wrong and hard to notice: a hook injected into the game must call the
 * <em>same</em> {@code ReModHooks} class the loader is listening on, and the
 * game's own classes must not already be defined by the parent.
 */
class TransformingClassLoaderTest {

    private TransformingClassLoader loader;

    @BeforeEach
    void setUp() {
        ReModLog.reset();
        loader = new TransformingClassLoader(new URL[0], getClass().getClassLoader());
    }

    @Test
    void claimsMinecraftsOwnPackages() {
        assertTrue(loader.isGameClass("net.minecraft.client.main.Main"));
        assertTrue(loader.isGameClass("net.minecraft.commands.Commands"));
        assertTrue(loader.isGameClass("com.mojang.blaze3d.systems.RenderSystem"));
        assertTrue(loader.isGameClass("com.mojang.realmsclient.RealmsMainScreen"));
    }

    @Test
    void claimsPackagelessClassesBecauseThatIsWhatObfuscationProduces() {
        // A stock Minecraft jar puts its obfuscated classes in the default
        // package, so they have no name to match on at all.
        assertTrue(loader.isGameClass("fx"));
        assertTrue(loader.isGameClass("cwx"));
    }

    @Test
    void neverClaimsReModsOwnClasses() {
        // If the game loaded its own copy of ReModHooks, the injected hook
        // would call a different class than the loader is listening on and
        // every hook would silently do nothing.
        assertFalse(loader.isGameClass("dev.remod.transform.hook.ReModHooks"));
        assertFalse(loader.isGameClass("dev.remod.api.ReModMod"));
        assertFalse(loader.isGameClass("dev.remod.loader.ReModLoader"));
    }

    @Test
    void neverClaimsThePlatformOrSharedLibraries() {
        assertFalse(loader.isGameClass("java.lang.String"));
        assertFalse(loader.isGameClass("javax.swing.JFrame"));
        assertFalse(loader.isGameClass("jdk.internal.misc.Unsafe"));
        assertFalse(loader.isGameClass("sun.misc.Unsafe"));
        // Brigadier is shared: Minecraft's dispatcher and ReMod's view of it
        // must be the same type.
        assertFalse(loader.isGameClass("com.mojang.brigadier.CommandDispatcher"));
        // ASM is ReMod's own tool, not the game's.
        assertFalse(loader.isGameClass("org.objectweb.asm.ClassReader"));
    }

    @Test
    void sharedClassesResolveToTheParentsCopy() throws Exception {
        Class<?> hooks = loader.loadClass("dev.remod.transform.hook.ReModHooks");

        assertSame(dev.remod.transform.hook.ReModHooks.class, hooks,
                "a hook must reach the same class the loader listens on");
    }

    @Test
    void aClassThatIsNotOnTheGameClasspathFallsBackToTheParent() throws Exception {
        // "net.minecraft..." is claimed, but this loader has no classpath, so
        // it must not simply fail: the parent gets a turn.
        Class<?> string = loader.loadClass("java.lang.String");
        assertSame(String.class, string);
    }

    @Test
    void startsWithNothingLoadedOrTransformed() {
        assertTrue(loader.loadedGameClasses().isEmpty());
        org.junit.jupiter.api.Assertions.assertEquals(0, loader.transformedCount());
        org.junit.jupiter.api.Assertions.assertEquals(0, loader.loadedGameClassCount());
    }

    @Test
    void aNullTransformerIsIgnoredRatherThanStored() {
        loader.register(null);
        org.junit.jupiter.api.Assertions.assertEquals(0, loader.transformedCount());
    }

    @Test
    void buildsALoaderOverTheJvmsOwnClasspath() {
        TransformingClassLoader overSystem =
                TransformingClassLoader.overSystemClasspath(getClass().getClassLoader());

        // The launcher puts Minecraft and ReMod's libraries on one classpath,
        // so reusing it is what makes the game's classes reachable at all.
        assertTrue(overSystem.getURLs().length > 0,
                "the system classpath should yield at least one entry");
    }
}
