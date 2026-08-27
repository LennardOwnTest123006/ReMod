package dev.remod.loader.runtime;

import dev.remod.api.config.Config;
import dev.remod.api.config.ConfigSpec;
import dev.remod.common.io.IOUtil;
import dev.remod.common.json.Json;
import dev.remod.common.json.JsonArray;
import dev.remod.common.json.JsonException;
import dev.remod.common.json.JsonObject;
import dev.remod.common.log.ReModLog;
import dev.remod.common.log.ReModLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A JSON-backed {@link Config}.
 *
 * <p>Reads never throw. A missing key, a value of the wrong type, or a number
 * outside its declared range all fall back to the spec default and log once --
 * a config typo should not take a world down. The offending value is left in
 * the file so the user can see and fix what they typed, rather than being
 * silently overwritten.</p>
 *
 * <p>Comments from the spec are written into the file as {@code _comment_<key>}
 * entries. JSON has no comment syntax, and inventing one would mean the file
 * could no longer be read by ordinary JSON tools.</p>
 */
public final class JsonConfig implements Config {

    private static final ReModLogger LOG = ReModLog.get("ReMod/Config");
    private static final String COMMENT_PREFIX = "_comment_";

    private final String modId;
    private final Path file;
    private final Map<String, Object> values = new LinkedHashMap<>();
    private final Set<String> reportedProblems = new LinkedHashSet<>();
    private ConfigSpec spec = ConfigSpec.empty();

    public JsonConfig(String modId, Path file) {
        this.modId = modId;
        this.file = file;
    }

    @Override
    public String modId() {
        return modId;
    }

    @Override
    public Path file() {
        return file;
    }

    @Override
    public synchronized Config withSpec(ConfigSpec newSpec) {
        this.spec = newSpec == null ? ConfigSpec.empty() : newSpec;
        boolean added = false;
        for (Map.Entry<String, ConfigSpec.Entry> entry : spec.entries().entrySet()) {
            if (!values.containsKey(entry.getKey())) {
                values.put(entry.getKey(), entry.getValue().defaultValue());
                added = true;
            }
        }
        if (added) {
            // A newly declared key should appear in the file so the user can find it.
            save();
        }
        return this;
    }

    @Override
    public ConfigSpec spec() {
        return spec;
    }

    @Override
    public boolean getBoolean(String key) {
        Object value = valueFor(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return fallback(key, Boolean.FALSE, value);
    }

    @Override
    public int getInt(String key) {
        Object value = valueFor(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return ((Number) fallbackObject(key, 0L, value)).intValue();
    }

    @Override
    public double getDouble(String key) {
        Object value = valueFor(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return ((Number) fallbackObject(key, 0.0d, value)).doubleValue();
    }

    @Override
    public String getString(String key) {
        Object value = valueFor(key);
        if (value instanceof String) {
            return (String) value;
        }
        return fallback(key, "", value);
    }

    @Override
    public List<String> getStringList(String key) {
        Object value = valueFor(key);
        if (value instanceof JsonArray) {
            return ((JsonArray) value).asStringList();
        }
        if (value instanceof List) {
            List<String> out = new ArrayList<>();
            for (Object element : (List<?>) value) {
                if (element instanceof String) {
                    out.add((String) element);
                }
            }
            return out;
        }
        Object defaulted = fallbackObject(key, new ArrayList<String>(), value);
        if (defaulted instanceof List) {
            List<String> out = new ArrayList<>();
            for (Object element : (List<?>) defaulted) {
                out.add(String.valueOf(element));
            }
            return out;
        }
        return new ArrayList<>();
    }

    /** Returns the stored value, validating it against the spec first. */
    private synchronized Object valueFor(String key) {
        Object value = values.get(key);
        ConfigSpec.Entry entry = spec.entry(key);
        if (entry == null) {
            return value;
        }
        if (value == null) {
            return entry.defaultValue();
        }
        if (!entry.accepts(value)) {
            reportOnce(key, "'" + value + "' is not " + entry.constraintDescription()
                    + "; using the default " + entry.defaultValue());
            return entry.defaultValue();
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private <T> T fallback(String key, T typedDefault, Object actual) {
        return (T) fallbackObject(key, typedDefault, actual);
    }

    private Object fallbackObject(String key, Object typedDefault, Object actual) {
        ConfigSpec.Entry entry = spec.entry(key);
        if (entry != null) {
            return entry.defaultValue();
        }
        if (actual != null) {
            reportOnce(key, "expected a different type but found '" + actual + "'");
        } else {
            reportOnce(key, "is not set and has no declared default");
        }
        return typedDefault;
    }

    private void reportOnce(String key, String problem) {
        if (reportedProblems.add(key)) {
            LOG.warn("Config " + file.getFileName() + ": key '" + key + "' " + problem
                    + ". Edit the file or delete it to regenerate defaults.");
        }
    }

    @Override
    public synchronized Optional<Object> getRaw(String key) {
        return Optional.ofNullable(values.get(key));
    }

    @Override
    public synchronized Config set(String key, Object value) {
        ConfigSpec.Entry entry = spec.entry(key);
        if (entry != null && !entry.accepts(normalise(value))) {
            throw new IllegalArgumentException("Config key '" + key + "' of mod '" + modId
                    + "' expects " + entry.constraintDescription() + ", but was given '"
                    + value + "'");
        }
        values.put(key, normalise(value));
        reportedProblems.remove(key);
        return this;
    }

    /** Normalises Java types to the JSON model so validation sees one shape. */
    private static Object normalise(Object value) {
        if (value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return ((Number) value).longValue();
        }
        if (value instanceof Float) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof List) {
            return new JsonArray((List<?>) value);
        }
        return value;
    }

    @Override
    public synchronized boolean contains(String key) {
        return values.containsKey(key);
    }

    @Override
    public synchronized Config load() {
        values.clear();
        reportedProblems.clear();
        if (!Files.isRegularFile(file)) {
            for (Map.Entry<String, ConfigSpec.Entry> entry : spec.entries().entrySet()) {
                values.put(entry.getKey(), entry.getValue().defaultValue());
            }
            return this;
        }
        try {
            JsonObject root = Json.parseObject(Files.readString(file, StandardCharsets.UTF_8));
            for (String key : root.keys()) {
                if (!key.startsWith(COMMENT_PREFIX)) {
                    values.put(key, root.get(key));
                }
            }
        } catch (JsonException e) {
            LOG.error("Config file " + file + " for mod '" + modId + "' is not valid JSON ("
                    + e.getMessage() + "). Using defaults for this session; your file has been"
                    + " left untouched so you can fix it.");
        } catch (IOException e) {
            LOG.error("Could not read config file " + file + " for mod '" + modId + "': "
                    + e.getMessage() + ". Using defaults for this session.");
        }
        for (Map.Entry<String, ConfigSpec.Entry> entry : spec.entries().entrySet()) {
            values.putIfAbsent(entry.getKey(), entry.getValue().defaultValue());
        }
        return this;
    }

    @Override
    public synchronized Config save() {
        JsonObject root = new JsonObject();
        // Emit spec keys first, in declaration order, each preceded by its comment.
        for (Map.Entry<String, ConfigSpec.Entry> declared : spec.entries().entrySet()) {
            ConfigSpec.Entry entry = declared.getValue();
            if (!entry.comments().isEmpty()) {
                root.put(COMMENT_PREFIX + entry.key(),
                        new JsonArray(withConstraint(entry)));
            }
            root.put(entry.key(), values.getOrDefault(entry.key(), entry.defaultValue()));
        }
        for (Map.Entry<String, Object> value : values.entrySet()) {
            if (!spec.entries().containsKey(value.getKey())) {
                root.put(value.getKey(), value.getValue());
            }
        }
        try {
            IOUtil.writeAtomically(file, Json.writePretty(root) + System.lineSeparator());
        } catch (IOException e) {
            LOG.error("Could not write config file " + file + " for mod '" + modId + "': "
                    + e.getMessage());
        }
        return this;
    }

    private static List<String> withConstraint(ConfigSpec.Entry entry) {
        List<String> lines = new ArrayList<>(entry.comments());
        lines.add("Accepts " + entry.constraintDescription()
                + ". Default: " + entry.defaultValue());
        return lines;
    }

    @Override
    public synchronized Config resetToDefaults() {
        values.clear();
        reportedProblems.clear();
        for (Map.Entry<String, ConfigSpec.Entry> entry : spec.entries().entrySet()) {
            values.put(entry.getKey(), entry.getValue().defaultValue());
        }
        return save();
    }
}
