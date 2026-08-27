package dev.remod.loader.runtime;

import dev.remod.api.resource.ResourceLoader;
import dev.remod.common.io.IOUtil;
import dev.remod.common.io.SafeZip;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads resources from one mod's own archive or exploded directory.
 *
 * <p>Scoped deliberately: a path is resolved inside that mod's files only. The
 * same {@link SafeZip#resolveSafely} check used during installation applies
 * here too, so a mod cannot read {@code ../../launcher_profiles.json} by
 * asking for it.</p>
 */
public final class ArchiveResourceLoader implements ResourceLoader {

    private final Path root;
    private final boolean directory;

    public ArchiveResourceLoader(Path root, boolean directory) {
        this.root = root;
        this.directory = directory;
    }

    @Override
    public Optional<InputStream> open(String path) throws IOException {
        Optional<byte[]> bytes = readBytes(path);
        return bytes.map(ByteArrayInputStream::new);
    }

    @Override
    public Optional<String> readText(String path) throws IOException {
        return readBytes(path).map(bytes -> new String(bytes, StandardCharsets.UTF_8));
    }

    @Override
    public Optional<byte[]> readBytes(String path) throws IOException {
        String normalised = normalise(path);
        if (normalised == null) {
            return Optional.empty();
        }
        if (directory) {
            Path file = SafeZip.resolveSafely(root, normalised);
            if (!Files.isRegularFile(file)) {
                return Optional.empty();
            }
            return Optional.of(Files.readAllBytes(file));
        }
        try (ZipFile zip = new ZipFile(root.toFile())) {
            ZipEntry entry = zip.getEntry(normalised);
            if (entry == null || entry.isDirectory()) {
                return Optional.empty();
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return Optional.of(IOUtil.readAll(in));
            }
        }
    }

    @Override
    public boolean exists(String path) {
        try {
            return readBytes(path).isPresent();
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public Collection<String> list(String directoryPath) throws IOException {
        String prefix = normalise(directoryPath);
        if (prefix == null) {
            return java.util.Collections.emptyList();
        }
        if (!prefix.isEmpty() && !prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        if (directory) {
            Path target = SafeZip.resolveSafely(root, prefix);
            if (!Files.isDirectory(target)) {
                return java.util.Collections.emptyList();
            }
            List<String> names = new ArrayList<>();
            String directoryPrefix = prefix;
            try (java.util.stream.Stream<Path> stream = Files.list(target)) {
                stream.sorted().forEach(child ->
                        names.add(directoryPrefix + child.getFileName()
                                + (Files.isDirectory(child) ? "/" : "")));
            }
            return names;
        }
        Set<String> names = new LinkedHashSet<>();
        try (ZipFile zip = new ZipFile(root.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (!name.startsWith(prefix) || name.equals(prefix)) {
                    continue;
                }
                String remainder = name.substring(prefix.length());
                int slash = remainder.indexOf('/');
                // Only direct children: everything deeper collapses to its top segment.
                names.add(slash < 0 ? name : prefix + remainder.substring(0, slash + 1));
            }
        }
        return new ArrayList<>(names);
    }

    /** Rejects absolute and traversing paths before they reach the filesystem. */
    private static String normalise(String path) {
        if (path == null) {
            return null;
        }
        String trimmed = path.trim().replace('\\', '/');
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.contains("..")) {
            return null;
        }
        return trimmed;
    }
}
