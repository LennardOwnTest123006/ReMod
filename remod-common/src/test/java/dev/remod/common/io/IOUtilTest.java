package dev.remod.common.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IOUtilTest {

    @Test
    void writesAtomicallyAndCreatesParents(@TempDir Path dir) throws IOException {
        Path target = dir.resolve("a/b/c/version.json");
        IOUtil.writeAtomically(target, "{\"id\":\"ReMod-1.21.4\"}");
        assertEquals("{\"id\":\"ReMod-1.21.4\"}", Files.readString(target, StandardCharsets.UTF_8));

        // Rewriting leaves no stray temporary files behind.
        IOUtil.writeAtomically(target, "{\"id\":\"updated\"}");
        assertEquals("{\"id\":\"updated\"}", Files.readString(target, StandardCharsets.UTF_8));
        try (var stream = Files.list(target.getParent())) {
            assertEquals(1, stream.count());
        }
    }

    @Test
    void computesTheSha1MinecraftManifestsUse(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("empty.bin");
        Files.write(file, new byte[0]);
        // The well-known SHA-1 of the empty input.
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", IOUtil.sha1(file));
    }

    @Test
    void listsFilesFilteredByExtension(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("b.jar"), "");
        Files.writeString(dir.resolve("a.jar"), "");
        Files.writeString(dir.resolve("notes.txt"), "");
        Files.createDirectory(dir.resolve("sub.jar"));

        List<Path> jars = IOUtil.listFiles(dir, ".jar");
        assertEquals(2, jars.size());
        assertEquals("a.jar", jars.get(0).getFileName().toString());
        assertEquals("b.jar", jars.get(1).getFileName().toString());

        assertEquals(3, IOUtil.listFiles(dir, null).size());
        assertTrue(IOUtil.listFiles(dir.resolve("missing"), ".jar").isEmpty());
    }

    @Test
    void deletesRecursively(@TempDir Path dir) throws IOException {
        Path root = dir.resolve("tree");
        Files.createDirectories(root.resolve("a/b"));
        Files.writeString(root.resolve("a/b/file.txt"), "x");
        IOUtil.deleteRecursively(root);
        assertFalse(Files.exists(root));
        // Deleting something absent is not an error.
        IOUtil.deleteRecursively(root);
    }

    @Test
    void formatsByteCounts() {
        assertEquals("512 B", IOUtil.humanBytes(512));
        assertEquals("1.0 KB", IOUtil.humanBytes(1024));
        assertEquals("1.5 MB", IOUtil.humanBytes(1024 * 1024 * 3 / 2));
    }
}
