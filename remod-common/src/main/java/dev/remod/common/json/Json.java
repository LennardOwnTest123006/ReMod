package dev.remod.common.json;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * A small, dependency-free JSON reader/writer.
 *
 * <p>ReMod deliberately avoids third-party JSON libraries so that
 * {@code ReMod.jar} stays a single self-contained download and so that the
 * loader never fights a mod over a shaded Gson/Jackson version at runtime.</p>
 *
 * <p>The document model is intentionally plain: objects become
 * {@link JsonObject}, arrays become {@link JsonArray}, numbers become
 * {@link Double} (or {@link Long} when the literal has no fraction or
 * exponent), and the remaining literals map to {@link String},
 * {@link Boolean} and {@code null}.</p>
 */
public final class Json {

    private Json() {
    }

    /** Parses any JSON value from a string. */
    public static Object parse(String text) {
        return new JsonParser(text).parseDocument();
    }

    /** Parses a JSON object from a string, failing if the root is not an object. */
    public static JsonObject parseObject(String text) {
        Object value = parse(text);
        if (!(value instanceof JsonObject)) {
            throw new JsonException("Expected a JSON object at the document root but found "
                    + typeName(value));
        }
        return (JsonObject) value;
    }

    /** Parses a JSON array from a string, failing if the root is not an array. */
    public static JsonArray parseArray(String text) {
        Object value = parse(text);
        if (!(value instanceof JsonArray)) {
            throw new JsonException("Expected a JSON array at the document root but found "
                    + typeName(value));
        }
        return (JsonArray) value;
    }

    /** Reads and parses a UTF-8 JSON object from disk. */
    public static JsonObject readObject(Path file) {
        try {
            return parseObject(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read JSON file " + file, e);
        } catch (JsonException e) {
            throw new JsonException("Malformed JSON in " + file + ": " + e.getMessage(), e);
        }
    }

    /** Reads and parses a JSON object from a reader. The reader is fully consumed. */
    public static JsonObject readObject(Reader reader) {
        try {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
            }
            return parseObject(sb.toString());
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read JSON", e);
        }
    }

    /** Writes a JSON value to a compact single-line string. */
    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        JsonWriter.write(value, out, false, 0);
        return out.toString();
    }

    /** Writes a JSON value to an indented, human-editable string. */
    public static String writePretty(Object value) {
        StringBuilder out = new StringBuilder();
        JsonWriter.write(value, out, true, 0);
        return out.toString();
    }

    /** Writes a JSON value to disk as pretty-printed UTF-8, creating parent directories. */
    public static void writePretty(Path file, Object value) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, writePretty(value) + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to write JSON file " + file, e);
        }
    }

    static String typeName(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof JsonObject || value instanceof Map) {
            return "object";
        }
        if (value instanceof JsonArray || value instanceof List) {
            return "array";
        }
        if (value instanceof String) {
            return "string";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof Number) {
            return "number";
        }
        return value.getClass().getSimpleName();
    }
}
