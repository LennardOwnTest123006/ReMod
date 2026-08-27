package dev.remod.loader.launch;

import dev.remod.api.Side;
import dev.remod.common.log.ReModLog;
import dev.remod.common.log.ReModLogger;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Finds Minecraft's own entry point on the classpath.
 *
 * <p>ReMod runs as a launch wrapper: the Minecraft launcher starts
 * {@link ReModLaunch} instead of the game, ReMod sets itself up, and then hands
 * control to the real main class with the original arguments. This class
 * locates that main class and, in doing so, determines which side is
 * starting.</p>
 *
 * <p>The candidate names have been stable for the whole modern history of
 * Minecraft, but they are checked rather than assumed, and a failure to find
 * one is reported as a clear message rather than a
 * {@code ClassNotFoundException}.</p>
 */
public final class GameLocator {

    private static final ReModLogger LOG = ReModLog.get("ReMod/Launch");

    private static final String[] CLIENT_MAINS = {
        "net.minecraft.client.main.Main"
    };

    private static final String[] SERVER_MAINS = {
        "net.minecraft.server.Main",
        "net.minecraft.server.MinecraftServer"
    };

    private final Class<?> mainClass;
    private final Side side;

    private GameLocator(Class<?> mainClass, Side side) {
        this.mainClass = mainClass;
        this.side = side;
    }

    /**
     * Locates Minecraft's entry point.
     *
     * <p>The loader passed here decides whether ReMod can affect the game at
     * all. Given the ordinary application loader, Minecraft starts untouched.
     * Given a transforming loader, every game class passes through ReMod's
     * hooks on the way in -- so this call is where the transformation layer
     * takes effect, and it must happen before anything else touches a
     * Minecraft class.</p>
     *
     * @return the located game, or {@code null} when Minecraft is not on the
     *         classpath -- which is the normal case under {@code remod test}
     */
    public static GameLocator locate(ClassLoader loader) {
        for (String name : CLIENT_MAINS) {
            Class<?> found = tryLoad(name, loader);
            if (found != null) {
                LOG.debug(() -> "Found the Minecraft client entry point " + name);
                return new GameLocator(found, Side.CLIENT);
            }
        }
        for (String name : SERVER_MAINS) {
            Class<?> found = tryLoad(name, loader);
            if (found != null) {
                LOG.debug(() -> "Found the Minecraft server entry point " + name);
                return new GameLocator(found, Side.DEDICATED_SERVER);
            }
        }
        return null;
    }

    private static Class<?> tryLoad(String name, ClassLoader loader) {
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    public Class<?> mainClass() {
        return mainClass;
    }

    /** Which side the located entry point starts. */
    public Side side() {
        return side;
    }

    /**
     * Invokes Minecraft's {@code main}, handing over control.
     *
     * @throws LaunchException when the entry point exists but cannot be called
     */
    public void launch(String[] arguments) throws LaunchException {
        Method main;
        try {
            main = mainClass.getMethod("main", String[].class);
        } catch (NoSuchMethodException e) {
            throw new LaunchException(mainClass.getName()
                    + " has no main(String[]) method, so this does not look like a Minecraft jar",
                    "Reinstall the Minecraft version from the official launcher, then run"
                            + " ReMod's installer again.", e);
        }
        if (!Modifier.isStatic(main.getModifiers())) {
            throw new LaunchException(mainClass.getName() + ".main is not static",
                    "The Minecraft jar appears to be modified or corrupted; reinstall it.", null);
        }
        try {
            LOG.info("Handing over to Minecraft (" + mainClass.getName() + ")");
            main.invoke(null, (Object) arguments);
        } catch (IllegalAccessException e) {
            throw new LaunchException("Could not call " + mainClass.getName() + ".main: "
                    + e.getMessage(),
                    "This usually means a security manager or module restriction is in place."
                            + " Launch Minecraft through the official launcher.", e);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new LaunchException("Minecraft threw during startup: " + cause,
                    "This is a Minecraft-side failure. Check the log above; if ReMod mods are"
                            + " involved they are named there.", cause);
        }
    }
}
