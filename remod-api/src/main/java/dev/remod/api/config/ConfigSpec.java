package dev.remod.api.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The schema for a mod's configuration file.
 *
 * <p>Declaring a spec buys three things a raw JSON file does not give you: the
 * file is generated with sensible defaults on first run, every key carries a
 * comment explaining it, and a value edited to something invalid is reported
 * with the key name and replaced with the default rather than crashing the
 * mod.</p>
 *
 * <pre>{@code
 * ConfigSpec spec = ConfigSpec.builder()
 *         .comment("Printed when a player joins.")
 *         .define("greeting", "Welcome to a ReMod server!")
 *         .comment("How many blocks the scanner reaches.")
 *         .defineInRange("scanRadius", 16, 1, 128)
 *         .define("enabled", true)
 *         .build();
 * Config config = context.config().withSpec(spec);
 * }</pre>
 */
public final class ConfigSpec {

    private final Map<String, Entry> entries;

    private ConfigSpec(Map<String, Entry> entries) {
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }

    public static Builder builder() {
        return new Builder();
    }

    /** An empty spec: every value is accepted and nothing is generated. */
    public static ConfigSpec empty() {
        return new ConfigSpec(new LinkedHashMap<>());
    }

    public Map<String, Entry> entries() {
        return entries;
    }

    public Entry entry(String key) {
        return entries.get(key);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** One declared configuration key. */
    public static final class Entry {

        private final String key;
        private final Object defaultValue;
        private final Kind kind;
        private final List<String> comments;
        private final Double min;
        private final Double max;
        private final List<String> allowedValues;

        Entry(String key, Object defaultValue, Kind kind, List<String> comments,
              Double min, Double max, List<String> allowedValues) {
            this.key = key;
            this.defaultValue = defaultValue;
            this.kind = kind;
            this.comments = Collections.unmodifiableList(new ArrayList<>(comments));
            this.min = min;
            this.max = max;
            this.allowedValues = allowedValues == null ? null
                    : Collections.unmodifiableList(new ArrayList<>(allowedValues));
        }

        public String key() {
            return key;
        }

        public Object defaultValue() {
            return defaultValue;
        }

        public Kind kind() {
            return kind;
        }

        public List<String> comments() {
            return comments;
        }

        public Double min() {
            return min;
        }

        public Double max() {
            return max;
        }

        /** The permitted values for a string key, or {@code null} when unrestricted. */
        public List<String> allowedValues() {
            return allowedValues;
        }

        /** True when {@code value} satisfies this entry's type and constraints. */
        public boolean accepts(Object value) {
            if (value == null) {
                return false;
            }
            switch (kind) {
                case BOOLEAN:
                    return value instanceof Boolean;
                case INTEGER:
                case DOUBLE:
                    if (!(value instanceof Number)) {
                        return false;
                    }
                    double number = ((Number) value).doubleValue();
                    if (kind == Kind.INTEGER && number != Math.rint(number)) {
                        return false;
                    }
                    return (min == null || number >= min) && (max == null || number <= max);
                case STRING:
                    return value instanceof String
                            && (allowedValues == null || allowedValues.contains(value));
                case STRING_LIST:
                    if (!(value instanceof List)) {
                        return false;
                    }
                    for (Object element : (List<?>) value) {
                        if (!(element instanceof String)) {
                            return false;
                        }
                    }
                    return true;
                default:
                    return true;
            }
        }

        /** A human-readable description of what this entry accepts. */
        public String constraintDescription() {
            switch (kind) {
                case INTEGER:
                case DOUBLE:
                    if (min != null && max != null) {
                        return kind == Kind.INTEGER
                                ? "an integer between " + min.longValue() + " and " + max.longValue()
                                : "a number between " + min + " and " + max;
                    }
                    return kind == Kind.INTEGER ? "an integer" : "a number";
                case BOOLEAN:
                    return "true or false";
                case STRING:
                    return allowedValues == null ? "a string"
                            : "one of " + String.join(", ", allowedValues);
                case STRING_LIST:
                    return "a list of strings";
                default:
                    return "any value";
            }
        }
    }

    /** The value kinds a config entry may hold. */
    public enum Kind {
        BOOLEAN, INTEGER, DOUBLE, STRING, STRING_LIST
    }

    /** Fluent builder for {@link ConfigSpec}. */
    public static final class Builder {

        private final Map<String, Entry> entries = new LinkedHashMap<>();
        private final List<String> pendingComments = new ArrayList<>();

        /** Attaches a comment to the next {@code define} call. */
        public Builder comment(String... lines) {
            Collections.addAll(pendingComments, lines);
            return this;
        }

        public Builder define(String key, boolean defaultValue) {
            return add(key, defaultValue, Kind.BOOLEAN, null, null, null);
        }

        public Builder define(String key, String defaultValue) {
            return add(key, defaultValue, Kind.STRING, null, null, null);
        }

        public Builder define(String key, int defaultValue) {
            return add(key, (long) defaultValue, Kind.INTEGER, null, null, null);
        }

        public Builder define(String key, double defaultValue) {
            return add(key, defaultValue, Kind.DOUBLE, null, null, null);
        }

        public Builder defineList(String key, List<String> defaultValue) {
            return add(key, new ArrayList<>(defaultValue), Kind.STRING_LIST, null, null, null);
        }

        public Builder defineInRange(String key, int defaultValue, int min, int max) {
            checkRange(key, defaultValue, min, max);
            return add(key, (long) defaultValue, Kind.INTEGER, (double) min, (double) max, null);
        }

        public Builder defineInRange(String key, double defaultValue, double min, double max) {
            checkRange(key, defaultValue, min, max);
            return add(key, defaultValue, Kind.DOUBLE, min, max, null);
        }

        /** A string key restricted to a fixed set of values. */
        public Builder defineEnum(String key, String defaultValue, List<String> allowed) {
            if (!allowed.contains(defaultValue)) {
                throw new IllegalArgumentException("Default '" + defaultValue + "' for config key '"
                        + key + "' is not one of the allowed values " + allowed);
            }
            return add(key, defaultValue, Kind.STRING, null, null, allowed);
        }

        private static void checkRange(String key, double defaultValue, double min, double max) {
            if (min > max) {
                throw new IllegalArgumentException("Config key '" + key + "' has min > max");
            }
            if (defaultValue < min || defaultValue > max) {
                throw new IllegalArgumentException("Default " + defaultValue + " for config key '"
                        + key + "' is outside the declared range " + min + ".." + max);
            }
        }

        private Builder add(String key, Object defaultValue, Kind kind,
                            Double min, Double max, List<String> allowed) {
            if (key == null || key.trim().isEmpty()) {
                throw new IllegalArgumentException("A config key cannot be empty");
            }
            if (entries.containsKey(key)) {
                throw new IllegalArgumentException("Config key '" + key + "' is declared twice");
            }
            entries.put(key, new Entry(key, defaultValue, kind,
                    new ArrayList<>(pendingComments), min, max, allowed));
            pendingComments.clear();
            return this;
        }

        public ConfigSpec build() {
            return new ConfigSpec(entries);
        }
    }
}
