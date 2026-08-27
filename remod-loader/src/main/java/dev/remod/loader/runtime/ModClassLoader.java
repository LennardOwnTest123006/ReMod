package dev.remod.loader.runtime;

import dev.remod.common.log.ReModLog;
import dev.remod.common.log.ReModLogger;
import dev.remod.loader.discovery.ModCandidate;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The class loader that mod code runs in.
 *
 * <p>One loader holds every mod, rather than one per mod. That is a deliberate
 * trade: mods can call into each other's classes (which dependencies require)
 * at the cost of not being able to load two versions of the same library. Mod
 * ecosystems universally make the same choice, because cross-mod integration is
 * the whole point of dependencies.</p>
 *
 * <p>The parent is the loader ReMod itself was loaded by, so
 * {@code dev.remod.api} classes are shared: a mod's {@code ReModMod} is the
 * same type the loader checks against, which is what makes
 * {@code instanceof} work across the boundary.</p>
 *
 * <p>Resource isolation is handled separately, by
 * {@link ArchiveResourceLoader}, which reads a mod's own archive directly. That
 * way one mod cannot read another's files simply by asking this loader for a
 * path.</p>
 */
public final class ModClassLoader extends URLClassLoader {

    private static final ReModLogger LOG = ReModLog.get("ReMod/ClassLoader");

    static {
        // Allows parallel class loading, so mods initialising on several threads
        // cannot deadlock against each other.
        registerAsParallelCapable();
    }

    private ModClassLoader(URL[] urls, ClassLoader parent) {
        super("ReModMods", urls, parent);
    }

    /**
     * Builds a loader over every discovered mod.
     *
     * @param parent the loader holding ReMod's own classes
     */
    public static ModClassLoader forMods(Collection<ModCandidate> candidates, ClassLoader parent) {
        List<URL> urls = new ArrayList<>();
        for (ModCandidate candidate : candidates) {
            try {
                urls.add(candidate.path().toUri().toURL());
            } catch (MalformedURLException e) {
                LOG.error("Could not add " + candidate.fileName()
                        + " to the mod class path: " + e.getMessage());
            }
        }
        return new ModClassLoader(urls.toArray(new URL[0]),
                parent == null ? ModClassLoader.class.getClassLoader() : parent);
    }

    /** Adds a jar after construction, used when the API jar is installed separately. */
    public void addArchive(java.nio.file.Path archive) {
        try {
            addURL(archive.toUri().toURL());
        } catch (MalformedURLException e) {
            LOG.error("Could not add " + archive + " to the mod class path: " + e.getMessage());
        }
    }

    @Override
    public void close() throws IOException {
        super.close();
    }
}
