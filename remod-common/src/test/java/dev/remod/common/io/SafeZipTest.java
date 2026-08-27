package dev.remod.common.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeZipTest {

    private static Path zipWith(Path dir, String name, String... entries) throws IOException {
        Path archive = dir.resolve(name);
        try (OutputStream out = Files.newOutputStream(archive);
             ZipOutputStream zip = new ZipOutputStream(out)) {
            for (int i = 0; i < entries.length; i += 2) {
                zip.putNextEntry(new ZipEntry(entries[i]));
                zip.write(entries[i + 1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return archive;
    }

    @Test
    void extractsAWellFormedArchive(@TempDir Path dir) throws IOException {
        Path archive = zipWith(dir, "mod.jar",
                "remod.mod.json", "{\"id\":\"test\"}",
                "dev/example/Main.class", "not really bytecode");
        Path out = dir.resolve("out");

        assertEquals(2, SafeZip.extract(archive, out));
        assertEquals("{\"id\":\"test\"}",
                Files.readString(out.resolve("remod.mod.json"), StandardCharsets.UTF_8));
        assertTrue(Files.isRegularFile(out.resolve("dev/example/Main.class")));
    }

    @Test
    void rejectsZipSlipEntries(@TempDir Path dir) throws IOException {
        Path archive = zipWith(dir, "evil.jar",
                "../../launcher_profiles.json", "pwned");
        Path out = dir.resolve("out");

        SafeZip.UnsafeArchiveException error = assertThrows(SafeZip.UnsafeArchiveException.class,
                () -> SafeZip.extract(archive, out));
        assertTrue(error.getMessage().contains("escape"), error.getMessage());
        assertFalse(Files.exists(dir.resolve("launcher_profiles.json")));
    }

    @Test
    void rejectsAbsoluteAndDriveQualifiedEntryNames(@TempDir Path dir) {
        assertThrows(SafeZip.UnsafeArchiveException.class,
                () -> SafeZip.resolveSafely(dir, "/etc/passwd"));
        assertThrows(SafeZip.UnsafeArchiveException.class,
                () -> SafeZip.resolveSafely(dir, "C:/Windows/System32/evil.dll"));
        assertThrows(SafeZip.UnsafeArchiveException.class,
                () -> SafeZip.resolveSafely(dir, ""));
    }

    @Test
    void rejectsBackslashTraversalUsedByWindowsBuiltArchives(@TempDir Path dir) {
        assertThrows(SafeZip.UnsafeArchiveException.class,
                () -> SafeZip.resolveSafely(dir, "..\\..\\evil.txt"));
    }

    @Test
    void resolvesLegitimateNestedEntriesInsideTheRoot(@TempDir Path dir) throws IOException {
        Path resolved = SafeZip.resolveSafely(dir, "assets/remod/icon.png");
        assertTrue(resolved.startsWith(dir.toAbsolutePath().normalize()));
        assertTrue(resolved.endsWith("assets/remod/icon.png"));
    }

    @Test
    void stopsExtractingWhenTheOutputExceedsTheBudget(@TempDir Path dir) throws IOException {
        StringBuilder payload = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            payload.append("0123456789");
        }
        Path archive = zipWith(dir, "bomb.jar", "big.bin", payload.toString());

        SafeZip.UnsafeArchiveException error = assertThrows(SafeZip.UnsafeArchiveException.class,
                () -> SafeZip.extract(archive, dir.resolve("out"), 1024, 100));
        assertTrue(error.getMessage().contains("expands beyond"), error.getMessage());
    }

    @Test
    void stopsExtractingWhenThereAreTooManyEntries(@TempDir Path dir) throws IOException {
        String[] entries = new String[20];
        for (int i = 0; i < 10; i++) {
            entries[i * 2] = "file" + i + ".txt";
            entries[i * 2 + 1] = "x";
        }
        Path archive = zipWith(dir, "many.jar", entries);

        assertThrows(SafeZip.UnsafeArchiveException.class,
                () -> SafeZip.extract(archive, dir.resolve("out"), 1 << 20, 3));
    }

    @Test
    void readsAndDetectsSingleEntries(@TempDir Path dir) throws IOException {
        Path archive = zipWith(dir, "mod.jar", "remod.mod.json", "{\"id\":\"a\"}");
        assertEquals("{\"id\":\"a\"}", SafeZip.readEntry(archive, "remod.mod.json"));
        org.junit.jupiter.api.Assertions.assertNull(SafeZip.readEntry(archive, "absent.json"));
        assertTrue(SafeZip.hasEntry(archive, "remod.mod.json"));
        assertFalse(SafeZip.hasEntry(archive, "absent.json"));
        assertTrue(SafeZip.looksLikeZip(archive));
    }

    @Test
    void detectsNonArchives(@TempDir Path dir) throws IOException {
        Path text = dir.resolve("readme.txt");
        Files.writeString(text, "definitely not a jar");
        assertFalse(SafeZip.looksLikeZip(text));
        assertTrue(SafeZip.isArchiveName("Thing.JAR"));
        assertTrue(SafeZip.isArchiveName("thing.remod"));
        assertFalse(SafeZip.isArchiveName("thing.txt"));
    }
}
