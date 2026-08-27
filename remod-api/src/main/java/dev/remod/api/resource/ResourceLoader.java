package dev.remod.api.resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Optional;

/**
 * Reads files packaged inside a mod's own archive.
 *
 * <p>Scoped to the calling mod: a path is resolved against that mod's archive
 * only, so one mod cannot read another's files by guessing a path, and a mod
 * loaded from a directory during development behaves the same as one loaded
 * from a jar.</p>
 */
public interface ResourceLoader {

    /**
     * Opens a resource.
     *
     * @param path a path relative to the archive root, e.g.
     *             {@code assets/simplemod/lang/en_us.json}
     * @return the stream, which the caller must close, or empty when absent
     */
    Optional<InputStream> open(String path) throws IOException;

    /** Reads a resource as UTF-8 text. */
    Optional<String> readText(String path) throws IOException;

    /** Reads a resource as raw bytes. */
    Optional<byte[]> readBytes(String path) throws IOException;

    /** True when the resource exists. */
    boolean exists(String path);

    /**
     * Lists resource paths directly under {@code directory}.
     *
     * @param directory a path relative to the archive root, without a trailing slash
     */
    Collection<String> list(String directory) throws IOException;
}
