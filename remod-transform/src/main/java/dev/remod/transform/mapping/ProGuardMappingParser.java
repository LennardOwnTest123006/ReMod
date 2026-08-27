package dev.remod.transform.mapping;

import dev.remod.common.log.ReModLog;
import dev.remod.common.log.ReModLogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads Mojang's official mapping files.
 *
 * <p>The format is ProGuard's, which Mojang publishes for every release:</p>
 *
 * <pre>
 * # a comment line
 * net.minecraft.commands.Commands -&gt; fx:
 *     com.mojang.brigadier.CommandDispatcher dispatcher -&gt; b
 *     1:12:void performCommand(java.lang.String) -&gt; a
 * </pre>
 *
 * <p>An unindented line declares a class; indented lines are its members. A
 * method line may carry a {@code start:end:} line-number prefix, which is
 * dropped.</p>
 *
 * <p>The direction matters and is easy to get backwards. In this file the name
 * on the <em>left</em> is the readable one and the name after the arrow is what
 * is actually in the jar -- the opposite of what "deobfuscation mapping"
 * suggests.</p>
 *
 * <p>Parsing is lenient by design. These files are hundreds of thousands of
 * lines and a single unrecognised one must not cost the whole mapping set; a
 * bad line is skipped and counted, and the count is logged once.</p>
 */
public final class ProGuardMappingParser {

    private static final ReModLogger LOG = ReModLog.get("ReMod/Mappings");

    private ProGuardMappingParser() {
    }

    /** Parses a mapping file from disk. */
    public static MappingSet parseFile(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return parse(reader);
        }
    }

    /** Parses mappings from a string. */
    public static MappingSet parse(String text) {
        try {
            return parse(new StringReader(text));
        } catch (IOException e) {
            // A StringReader cannot fail; rethrowing keeps the signature honest.
            throw new IllegalStateException("Unreachable", e);
        }
    }

    /** Parses mappings from a reader. The reader is fully consumed but not closed. */
    public static MappingSet parse(Reader source) throws IOException {
        Map<String, MappingSet.ClassMapping> classes = new LinkedHashMap<>();
        BufferedReader reader = source instanceof BufferedReader
                ? (BufferedReader) source
                : new BufferedReader(source);

        String currentDeobfuscated = null;
        String currentObfuscated = null;
        Map<String, String> fields = new LinkedHashMap<>();
        Map<String, String> methods = new LinkedHashMap<>();
        int skipped = 0;

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty() || line.charAt(0) == '#') {
                continue;
            }
            boolean isMember = Character.isWhitespace(line.charAt(0));
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (!isMember) {
                // Starting a new class: bank the one being built.
                if (currentDeobfuscated != null) {
                    classes.put(currentDeobfuscated, new MappingSet.ClassMapping(
                            currentDeobfuscated, currentObfuscated,
                            new LinkedHashMap<>(fields), new LinkedHashMap<>(methods)));
                }
                fields.clear();
                methods.clear();
                String[] parts = splitArrow(trimmed);
                if (parts == null || !parts[1].endsWith(":")) {
                    currentDeobfuscated = null;
                    skipped++;
                    continue;
                }
                currentDeobfuscated = parts[0];
                currentObfuscated = parts[1].substring(0, parts[1].length() - 1);
                continue;
            }

            if (currentDeobfuscated == null) {
                // A member before any class header; nothing to attach it to.
                skipped++;
                continue;
            }
            if (!parseMember(trimmed, fields, methods)) {
                skipped++;
            }
        }
        if (currentDeobfuscated != null) {
            classes.put(currentDeobfuscated, new MappingSet.ClassMapping(
                    currentDeobfuscated, currentObfuscated, fields, methods));
        }

        int unreadable = skipped;
        if (unreadable > 0) {
            LOG.debug(() -> "Skipped " + unreadable + " unreadable mapping line(s)");
        }
        LOG.debug(() -> "Parsed " + classes.size() + " class mapping(s)");
        return new MappingSet(classes);
    }

    /**
     * Parses one member line.
     *
     * @return false when the line could not be understood
     */
    private static boolean parseMember(String trimmed, Map<String, String> fields,
                                       Map<String, String> methods) {
        String[] parts = splitArrow(trimmed);
        if (parts == null) {
            return false;
        }
        String obfuscated = parts[1];
        String declaration = parts[0];

        // Drop a "start:end:" line-number prefix, which only methods carry.
        int lastColon = declaration.lastIndexOf(':');
        if (lastColon >= 0) {
            declaration = declaration.substring(lastColon + 1);
        }

        int space = declaration.indexOf(' ');
        if (space < 0) {
            return false;
        }
        String nameAndArguments = declaration.substring(space + 1).trim();
        int parenthesis = nameAndArguments.indexOf('(');
        if (parenthesis < 0) {
            String fieldName = nameAndArguments;
            if (fieldName.isEmpty()) {
                return false;
            }
            fields.put(fieldName, obfuscated);
            return true;
        }
        String methodName = nameAndArguments.substring(0, parenthesis).trim();
        if (methodName.isEmpty()) {
            return false;
        }
        methods.put(methodName, obfuscated);
        return true;
    }

    /** Splits {@code left -> right}, returning {@code null} when the arrow is absent. */
    private static String[] splitArrow(String line) {
        int arrow = line.indexOf("->");
        if (arrow < 0) {
            return null;
        }
        String left = line.substring(0, arrow).trim();
        String right = line.substring(arrow + 2).trim();
        if (left.isEmpty() || right.isEmpty()) {
            return null;
        }
        return new String[]{left, right};
    }
}
