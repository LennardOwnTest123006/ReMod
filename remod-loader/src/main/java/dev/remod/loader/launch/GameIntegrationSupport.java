package dev.remod.loader.launch;

import dev.remod.common.log.ReModLog;
import dev.remod.common.log.ReModLogger;

import java.lang.reflect.Method;

/**
 * Installs ReMod's class-transformation layer when the build includes it.
 *
 * <p>The loader deliberately does not depend on {@code remod-transform}: the
 * transformation layer needs ASM, and a server operator or a test harness may
 * reasonably run the loader without either. So it is reached reflectively, and
 * its absence is a normal, supported state rather than a failure.</p>
 *
 * <p>When the layer is present, Minecraft is loaded through it and ReMod's
 * hooks take effect. When it is not, Minecraft is loaded normally and ReMod
 * behaves exactly as it did before -- mods load, nothing reaches the game.</p>
 */
final class GameIntegrationSupport {

    private static final ReModLogger LOG = ReModLog.get("ReMod/Launch");

    private static final String INTEGRATION_CLASS = "dev.remod.transform.GameIntegration";

    private static volatile Object integration;

    private GameIntegrationSupport() {
    }

    /**
     * Installs the layer if it is on the classpath.
     *
     * @return the class loader Minecraft should be loaded through: the
     *         transforming one when available, otherwise {@code parent}
     */
    static ClassLoader installIfPresent(ClassLoader parent) {
        try {
            Class<?> type = Class.forName(INTEGRATION_CLASS, true, parent);
            Method install = type.getMethod("install", ClassLoader.class);
            Object installed = install.invoke(null, parent);
            integration = installed;
            ClassLoader loader = (ClassLoader) type.getMethod("classLoader").invoke(installed);
            // Minecraft's own code resolves classes through the context loader
            // in a few places, so it has to agree with ours.
            Thread.currentThread().setContextClassLoader(loader);
            return loader;
        } catch (ClassNotFoundException e) {
            LOG.info("No class-transformation layer on the classpath; Minecraft will start"
                    + " untouched and ReMod will not be able to affect it.");
            return parent;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOG.error("The class-transformation layer failed to install; starting Minecraft"
                    + " without it", e);
            return parent;
        }
    }

    /** The installed integration, or {@code null} when the layer is absent. */
    static Object integration() {
        return integration;
    }
}
