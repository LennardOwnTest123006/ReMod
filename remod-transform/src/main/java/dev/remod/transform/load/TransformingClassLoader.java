package dev.remod.transform.load;

import dev.remod.common.io.IOUtil;
import dev.remod.common.log.ReModLog;
import dev.remod.common.log.ReModLogger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Loads Minecraft's classes so ReMod can rewrite them on the way in.
 *
 * <p>This is the piece that makes ReMod able to affect a running game at all.
 * Without it ReMod can only sit alongside Minecraft; with it, ReMod can inject
 * a hook into a class before the JVM ever sees the original.</p>
 *
 * <h2>Why a class loader rather than a Java agent</h2>
 *
 * <p>A {@code -javaagent} would also work, but it has to be on the command line
 * before the JVM starts, which means putting it in the launcher's JVM arguments
 * and hoping every launcher honours it. A class loader needs nothing from
 * outside: ReMod's launch wrapper already runs before Minecraft, so it can
 * simply load the game through this instead. It is the same approach Forge's
 * ModLauncher and the old LaunchWrapper take, for the same reason.</p>
 *
 * <h2>Child-first, but only for the game</h2>
 *
 * <p>The delegation order is inverted for Minecraft's own packages and normal
 * everywhere else. That distinction is essential rather than a detail:</p>
 *
 * <ul>
 *   <li><b>Minecraft classes must be child-first.</b> If the parent loader got
 *       there first, the untransformed class would already be defined and the
 *       transform would silently do nothing.</li>
 *   <li><b>Everything else must be parent-first.</b> ReMod's own classes, the
 *       Java platform, and libraries such as Brigadier have to be the
 *       <em>same</em> classes the rest of ReMod is using -- otherwise the hook
 *       injected into the game would call a different copy of
 *       {@code ReModHooks} than the loader is listening on, and nothing would
 *       appear to happen.</li>
 * </ul>
 */
public final class TransformingClassLoader extends URLClassLoader {

    private static final ReModLogger LOG = ReModLog.get("ReMod/Transform");

    /**
     * Packages loaded child-first, so a transformer can reach them.
     *
     * <p>Minecraft's obfuscated classes live in the default package in a stock
     * jar, so the name check cannot be the only filter -- {@link #isGameClass}
     * also treats any class from the game jars themselves as fair game.</p>
     */
    private static final String[] GAME_PACKAGES = {
        "net.minecraft.",
        "com.mojang.blaze3d.",
        "com.mojang.realmsclient."
    };

    /** Never child-first: sharing these with the parent is what makes hooks work. */
    private static final String[] ALWAYS_PARENT = {
        "java.", "javax.", "jdk.", "sun.", "com.sun.",
        "dev.remod.",
        "com.mojang.brigadier.",
        "org.objectweb.asm."
    };

    private final List<GameTransformer> transformers = new ArrayList<>();
    private final Set<String> gameClassNames = ConcurrentHashMap.newKeySet();
    private final AtomicInteger transformedCount = new AtomicInteger();
    private final AtomicInteger loadedCount = new AtomicInteger();

    static {
        registerAsParallelCapable();
    }

    /**
     * @param classpath the jars Minecraft's classes come from
     * @param parent    the loader holding ReMod's own classes
     */
    public TransformingClassLoader(URL[] classpath, ClassLoader parent) {
        super("ReModGame", classpath, parent);
    }

    /**
     * Builds a loader over the classpath the JVM was started with.
     *
     * <p>The launcher puts Minecraft and ReMod's libraries on the same
     * classpath, so reusing it is exactly right: everything the game needs is
     * already there, and the parent still supplies ReMod's own classes.</p>
     */
    public static TransformingClassLoader overSystemClasspath(ClassLoader parent) {
        List<URL> urls = new ArrayList<>();
        ClassLoader system = ClassLoader.getSystemClassLoader();
        if (system instanceof URLClassLoader) {
            java.util.Collections.addAll(urls, ((URLClassLoader) system).getURLs());
        } else {
            // Java 9 and newer do not expose the app loader as a URLClassLoader,
            // so the classpath has to be read from the system property.
            String classpath = System.getProperty("java.class.path", "");
            for (String entry : classpath.split(java.io.File.pathSeparator)) {
                if (entry.isEmpty()) {
                    continue;
                }
                try {
                    urls.add(java.nio.file.Paths.get(entry).toUri().toURL());
                } catch (Exception e) {
                    LOG.debug(() -> "Skipping unusable classpath entry " + entry);
                }
            }
        }
        return new TransformingClassLoader(urls.toArray(new URL[0]), parent);
    }

    /** Adds a transformer. Must be called before the game class is loaded. */
    public TransformingClassLoader register(GameTransformer transformer) {
        if (transformer != null) {
            transformers.add(transformer);
            LOG.debug(() -> "Registered transformer " + transformer.name());
        }
        return this;
    }

    /** Every Minecraft class this loader has defined, in load order. */
    public Set<String> loadedGameClasses() {
        return new LinkedHashSet<>(gameClassNames);
    }

    public int transformedCount() {
        return transformedCount.get();
    }

    public int loadedGameClassCount() {
        return loadedCount.get();
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded != null) {
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
            if (!isGameClass(name)) {
                return super.loadClass(name, resolve);
            }
            try {
                Class<?> defined = defineGameClass(name);
                if (resolve) {
                    resolveClass(defined);
                }
                return defined;
            } catch (ClassNotFoundException e) {
                // Not on our classpath after all; let the normal order try.
                return super.loadClass(name, resolve);
            }
        }
    }

    /** Reads, transforms and defines one game class. */
    private Class<?> defineGameClass(String name) throws ClassNotFoundException {
        String path = name.replace('.', '/') + ".class";
        URL resource = findResource(path);
        if (resource == null) {
            throw new ClassNotFoundException(name);
        }
        byte[] original;
        try (InputStream in = resource.openStream()) {
            original = IOUtil.readAll(in);
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }

        String internalName = name.replace('.', '/');
        byte[] bytes = applyTransformers(internalName, original);

        definePackageIfAbsent(name);
        ProtectionDomain domain = protectionDomainFor(resource);
        Class<?> defined = defineClass(name, bytes, 0, bytes.length, domain);
        gameClassNames.add(name);
        loadedCount.incrementAndGet();
        return defined;
    }

    /**
     * Runs every interested transformer in turn.
     *
     * <p>A transformer that throws is logged and skipped rather than allowed to
     * stop the game starting: a broken transform should cost its own feature,
     * not the whole session.</p>
     */
    private byte[] applyTransformers(String internalName, byte[] original) {
        byte[] current = original;
        for (GameTransformer transformer : transformers) {
            if (!transformer.handles(internalName)) {
                continue;
            }
            try {
                byte[] result = transformer.transform(internalName, current);
                if (result != null && result != current) {
                    current = result;
                    transformedCount.incrementAndGet();
                    LOG.debug(() -> transformer.name() + " transformed " + internalName);
                }
            } catch (Throwable failure) {
                LOG.error("Transformer " + transformer.name() + " failed on " + internalName
                        + "; loading the class unchanged", failure);
            }
        }
        return current;
    }

    private void definePackageIfAbsent(String className) {
        int lastDot = className.lastIndexOf('.');
        if (lastDot < 0) {
            return;
        }
        String packageName = className.substring(0, lastDot);
        if (getDefinedPackage(packageName) == null) {
            try {
                definePackage(packageName, null, null, null, null, null, null, null);
            } catch (IllegalArgumentException e) {
                // Defined concurrently by another thread; harmless.
            }
        }
    }

    private ProtectionDomain protectionDomainFor(URL resource) {
        try {
            URL source = resource;
            String form = resource.toString();
            int separator = form.indexOf("!/");
            if (form.startsWith("jar:") && separator > 0) {
                source = new java.net.URI(form.substring(4, separator)).toURL();
            }
            return new ProtectionDomain(new CodeSource(source, (java.security.cert.Certificate[]) null),
                    null, this, null);
        } catch (Exception e) {
            return null;
        }
    }

    /** True when {@code name} is Minecraft's to load rather than the parent's. */
    boolean isGameClass(String name) {
        for (String prefix : ALWAYS_PARENT) {
            if (name.startsWith(prefix)) {
                return false;
            }
        }
        for (String prefix : GAME_PACKAGES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        // Obfuscated Minecraft classes have no package at all. Treating every
        // package-less class as the game's is safe here because the parent has
        // already claimed everything else above.
        return name.indexOf('.') < 0;
    }
}
