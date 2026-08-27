package dev.remod.common.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Zip handling that refuses to be tricked.
 *
 * <p>ReMod reads mod archives supplied by third parties, so every extraction
 * goes through here. Three classes of attack are rejected outright:</p>
 *
 * <ul>
 *   <li><b>Path traversal ("zip slip")</b> -- an entry named
 *       {@code ../../.minecraft/launcher_profiles.json} must never escape the
 *       destination directory. Entries are resolved and then checked to still
 *       be inside the target root.</li>
 *   <li><b>Zip bombs</b> -- extraction stops once the declared or actual
 *       output exceeds a caller-supplied budget.</li>
 *   <li><b>Absolute paths and drive letters</b> -- rejected before resolution.</li>
 * </ul>
 */
public final class SafeZip {

    /** Default cap on total extracted bytes: generous for a mod, fatal for a bomb. */
    public static final long DEFAULT_MAX_TOTAL_BYTES = 512L * 1024 * 1024;

    /** Default cap on the number of entries extracted from one archive. */
    public static final int DEFAULT_MAX_ENTRIES = 20_000;

    private SafeZip() {
    }

    /** Thrown when an archive is malformed or hostile. */
    public static class UnsafeArchiveException extends IOException {

        private static final long serialVersionUID = 1L;

        public UnsafeArchiveException(String message) {
            super(message);
        }
    }

    /**
     * Resolves a zip entry name against {@code root}, rejecting anything that
     * would land outside it.
     *
     * @return the resolved, normalised path inside {@code root}
     * @throws UnsafeArchiveException if the name escapes {@code root}
     */
    public static Path resolveSafely(Path root, String entryName) throws UnsafeArchiveException {
        if (entryName == null || entryName.isEmpty()) {
            throw new UnsafeArchiveException("Archive contains an entry with an empty name");
        }
        String normalisedName = entryName.replace('\\', '/');
        if (normalisedName.startsWith("/")) {
            throw new UnsafeArchiveException(
                    "Archive entry '" + entryName + "' uses an absolute path");
        }
        // Windows drive-qualified names such as "C:/windows/system32".
        if (normalisedName.length() > 1 && normalisedName.charAt(1) == ':') {
            throw new UnsafeArchiveException(
                    "Archive entry '" + entryName + "' uses a drive-qualified path");
        }
        for (String segment : normalisedName.split("/")) {
            if (segment.equals("..")) {
                throw new UnsafeArchiveException(
                        "Archive entry '" + entryName + "' tries to escape the destination directory");
            }
        }
        Path base = root.toAbsolutePath().normalize();
        Path resolved = base.resolve(normalisedName).normalize();
        if (!resolved.startsWith(base)) {
            throw new UnsafeArchiveException(
                    "Archive entry '" + entryName + "' resolves outside the destination directory");
        }
        return resolved;
    }

    /** Extracts {@code archive} into {@code destination} using the default limits. */
    public static int extract(Path archive, Path destination) throws IOException {
        return extract(archive, destination, DEFAULT_MAX_TOTAL_BYTES, DEFAULT_MAX_ENTRIES);
    }

    /**
     * Extracts {@code archive} into {@code destination}.
     *
     * @return the number of entries written
     */
    public static int extract(Path archive, Path destination, long maxTotalBytes, int maxEntries)
            throws IOException {
        Files.createDirectories(destination);
        long written = 0;
        int entries = 0;
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            java.util.Enumeration<? extends ZipEntry> enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                if (++entries > maxEntries) {
                    throw new UnsafeArchiveException("Archive " + archive.getFileName()
                            + " contains more than " + maxEntries + " entries; refusing to extract");
                }
                Path target = resolveSafely(destination, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                try (InputStream in = zip.getInputStream(entry);
                     OutputStream out = Files.newOutputStream(target)) {
                    byte[] buffer = new byte[16 * 1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        written += read;
                        if (written > maxTotalBytes) {
                            throw new UnsafeArchiveException("Archive " + archive.getFileName()
                                    + " expands beyond " + IOUtil.humanBytes(maxTotalBytes)
                                    + "; refusing to extract");
                        }
                        out.write(buffer, 0, read);
                    }
                }
            }
        }
        return entries;
    }

    /** Reads one entry from an archive as UTF-8 text, or {@code null} when absent. */
    public static String readEntry(Path archive, String entryName) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                return null;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return IOUtil.readString(in);
            }
        }
    }

    /** True when the archive contains an entry with this exact name. */
    public static boolean hasEntry(Path archive, String entryName) {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            return zip.getEntry(entryName) != null;
        } catch (IOException e) {
            return false;
        }
    }

    /** True when the file starts with the {@code PK\003\004} local-file-header magic. */
    public static boolean looksLikeZip(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] magic = new byte[4];
            int read = in.read(magic);
            return read == 4 && magic[0] == 'P' && magic[1] == 'K'
                    && magic[2] == 3 && magic[3] == 4;
        } catch (IOException e) {
            return false;
        }
    }

    /** True when {@code name} looks like a Java/mod archive we should inspect. */
    public static boolean isArchiveName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jar") || lower.endsWith(".remod") || lower.endsWith(".zip");
    }
}
