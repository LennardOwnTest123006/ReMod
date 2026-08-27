package dev.remod.common.json;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A JSON object with insertion-ordered keys and typed accessors.
 *
 * <p>The accessors come in two flavours. {@code getX} requires the key to be
 * present and of the right type and otherwise throws a {@link JsonException}
 * naming the key -- which is what turns a malformed mod manifest into a
 * readable error instead of a {@code NullPointerException}. {@code optX}
 * returns a caller-supplied default instead.</p>
 */
public final class JsonObject {

    private final Map<String, Object> values = new LinkedHashMap<>();

    public JsonObject() {
    }

    public JsonObject(Map<String, ?> source) {
        if (source != null) {
            source.forEach(this::put);
        }
    }

    public JsonObject put(String key, Object value) {
        values.put(key, normalize(value));
        return this;
    }

    /** Stores {@code value} only when it is non-null, keeping generated JSON tidy. */
    public JsonObject putIfPresent(String key, Object value) {
        if (value != null) {
            put(key, value);
        }
        return this;
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }

    /** True when the key is present and holds a value other than JSON {@code null}. */
    public boolean hasValue(String key) {
        return values.get(key) != null;
    }

    public Object get(String key) {
        return values.get(key);
    }

    public Set<String> keys() {
        return Collections.unmodifiableSet(values.keySet());
    }

    public Map<String, Object> toMap() {
        return Collections.unmodifiableMap(values);
    }

    public int size() {
        return values.size();
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public String getString(String key) {
        return require(key, String.class, "string");
    }

    public String optString(String key, String fallback) {
        Object value = values.get(key);
        return value instanceof String ? (String) value : fallback;
    }

    public boolean getBoolean(String key) {
        return require(key, Boolean.class, "boolean");
    }

    public boolean optBoolean(String key, boolean fallback) {
        Object value = values.get(key);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    public int getInt(String key) {
        return require(key, Number.class, "number").intValue();
    }

    public int optInt(String key, int fallback) {
        Object value = values.get(key);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    public long getLong(String key) {
        return require(key, Number.class, "number").longValue();
    }

    public long optLong(String key, long fallback) {
        Object value = values.get(key);
        return value instanceof Number ? ((Number) value).longValue() : fallback;
    }

    public double getDouble(String key) {
        return require(key, Number.class, "number").doubleValue();
    }

    public double optDouble(String key, double fallback) {
        Object value = values.get(key);
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }

    public JsonObject getObject(String key) {
        return require(key, JsonObject.class, "object");
    }

    /** Returns the child object, or an empty object when absent. Never null. */
    public JsonObject optObject(String key) {
        Object value = values.get(key);
        return value instanceof JsonObject ? (JsonObject) value : new JsonObject();
    }

    public JsonArray getArray(String key) {
        return require(key, JsonArray.class, "array");
    }

    /** Returns the child array, or an empty array when absent. Never null. */
    public JsonArray optArray(String key) {
        Object value = values.get(key);
        return value instanceof JsonArray ? (JsonArray) value : new JsonArray();
    }

    /** Convenience for the very common "array of strings" manifest shape. */
    public List<String> optStringList(String key) {
        return optArray(key).asStringList();
    }

    private <T> T require(String key, Class<T> type, String typeLabel) {
        if (!values.containsKey(key)) {
            throw new JsonException("Missing required key '" + key + "'");
        }
        Object value = values.get(key);
        if (!type.isInstance(value)) {
            throw new JsonException("Key '" + key + "' should be a " + typeLabel
                    + " but was " + Json.typeName(value));
        }
        return type.cast(value);
    }

    @SuppressWarnings("unchecked")
    static Object normalize(Object value) {
        if (value instanceof Map) {
            return new JsonObject((Map<String, ?>) value);
        }
        if (value instanceof List) {
            return new JsonArray((List<?>) value);
        }
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof JsonObject && ((JsonObject) other).values.equals(values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return Json.write(this);
    }
}
