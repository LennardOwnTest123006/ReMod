package dev.remod.loader;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Builds mod jars and manifests for tests. */
public final class ModTestFixtures {

    private ModTestFixtures() {
    }

    /** A manifest with sensible defaults that individual tests override. */
    public static Manifest manifest(String id) {
        return new Manifest(id);
    }

    /** Writes a mod jar containing only a manifest. */
    public static Path writeModJar(Path modsDirectory, String fileName, String manifestJson)
            throws IOException {
        return writeModJar(modsDirectory, fileName, manifestJson, Map.of());
    }

    /** Writes a mod jar containing a manifest and extra entries. */
    public static Path writeModJar(Path modsDirectory, String fileName, String manifestJson,
                                   Map<String, String> extraEntries) throws IOException {
        Files.createDirectories(modsDirectory);
        Path jar = modsDirectory.resolve(fileName);
        try (OutputStream out = Files.newOutputStream(jar);
             ZipOutputStream zip = new ZipOutputStream(out)) {
            if (manifestJson != null) {
                zip.putNextEntry(new ZipEntry("remod.mod.json"));
                zip.write(manifestJson.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            for (Map.Entry<String, String> entry : extraEntries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return jar;
    }

    /** Writes an exploded mod directory, as used during development. */
    public static Path writeModDirectory(Path modsDirectory, String name, String manifestJson)
            throws IOException {
        Path directory = modsDirectory.resolve(name);
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("remod.mod.json"), manifestJson,
                StandardCharsets.UTF_8);
        return directory;
    }

    /** Fluent manifest builder producing raw JSON. */
    public static final class Manifest {

        private final Map<String, String> fields = new LinkedHashMap<>();

        private Manifest(String id) {
            fields.put("id", quote(id));
            fields.put("name", quote(id));
            fields.put("version", quote("1.0.0"));
            fields.put("minecraft", quote("1.21.x"));
            fields.put("remod_api", quote("1.21-1.0.0"));
            fields.put("entrypoints", "[\"dev.example.Main\"]");
        }

        public Manifest name(String value) {
            fields.put("name", quote(value));
            return this;
        }

        public Manifest version(String value) {
            fields.put("version", quote(value));
            return this;
        }

        public Manifest minecraft(String value) {
            fields.put("minecraft", quote(value));
            return this;
        }

        public Manifest api(String value) {
            fields.put("remod_api", quote(value));
            return this;
        }

        public Manifest side(String value) {
            fields.put("side", quote(value));
            return this;
        }

        public Manifest entrypoints(String... classNames) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < classNames.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(quote(classNames[i]));
            }
            fields.put("entrypoints", sb.append(']').toString());
            return this;
        }

        public Manifest dependencies(String... entries) {
            return list("dependencies", entries);
        }

        public Manifest optionalDependencies(String... entries) {
            return list("optional_dependencies", entries);
        }

        public Manifest incompatible(String... entries) {
            return list("incompatible", entries);
        }

        private Manifest list(String key, String... entries) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < entries.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(quote(entries[i]));
            }
            fields.put(key, sb.append(']').toString());
            return this;
        }

        /** Sets a raw JSON value, for testing malformed input. */
        public Manifest raw(String key, String jsonValue) {
            fields.put(key, jsonValue);
            return this;
        }

        public Manifest remove(String key) {
            fields.remove(key);
            return this;
        }

        public String build() {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(quote(entry.getKey())).append(':').append(entry.getValue());
            }
            return sb.append('}').toString();
        }

        private static String quote(String value) {
            return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
    }
}
