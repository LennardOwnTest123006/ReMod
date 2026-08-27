package dev.remod.common.json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/** A JSON array with typed element accessors. */
public final class JsonArray implements Iterable<Object> {

    private final List<Object> values = new ArrayList<>();

    public JsonArray() {
    }

    public JsonArray(List<?> source) {
        if (source != null) {
            source.forEach(this::add);
        }
    }

    public JsonArray add(Object value) {
        values.add(JsonObject.normalize(value));
        return this;
    }

    public int size() {
        return values.size();
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public Object get(int index) {
        return values.get(index);
    }

    public String getString(int index) {
        Object value = values.get(index);
        if (!(value instanceof String)) {
            throw new JsonException("Element " + index + " should be a string but was "
                    + Json.typeName(value));
        }
        return (String) value;
    }

    public JsonObject getObject(int index) {
        Object value = values.get(index);
        if (!(value instanceof JsonObject)) {
            throw new JsonException("Element " + index + " should be an object but was "
                    + Json.typeName(value));
        }
        return (JsonObject) value;
    }

    /** Returns every element that is a JSON object, skipping anything else. */
    public List<JsonObject> objects() {
        List<JsonObject> out = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof JsonObject) {
                out.add((JsonObject) value);
            }
        }
        return out;
    }

    /** Returns every element that is a string, skipping anything else. */
    public List<String> asStringList() {
        List<String> out = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof String) {
                out.add((String) value);
            }
        }
        return out;
    }

    public List<Object> toList() {
        return Collections.unmodifiableList(values);
    }

    @Override
    public Iterator<Object> iterator() {
        return Collections.unmodifiableList(values).iterator();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof JsonArray && ((JsonArray) other).values.equals(values);
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
