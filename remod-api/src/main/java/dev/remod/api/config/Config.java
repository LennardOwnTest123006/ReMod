package dev.remod.api.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * A mod's configuration.
 *
 * <p>Backed by a JSON file at {@code <game>/remod/config/<modid>.json}. Reads
 * are in-memory and cheap; the file is touched only on {@link #load()} and
 * {@link #save()}.</p>
 *
 * <p>Getters never throw for a missing or malformed value. If a key is absent
 * or fails its spec, the declared default is returned and the problem is logged
 * once with the key name -- a mod should not crash a world because someone put
 * a letter in a number field.</p>
 */
public interface Config {

    /** The mod this configuration belongs to. */
    String modId();

    /** The backing file. It may not exist until {@link #save()} is called. */
    Path file();

    /**
     * Attaches a schema, filling in any missing keys from its defaults.
     *
     * @return this config, for chaining
     */
    Config withSpec(ConfigSpec spec);

    ConfigSpec spec();

    boolean getBoolean(String key);

    int getInt(String key);

    double getDouble(String key);

    String getString(String key);

    List<String> getStringList(String key);

    /** The raw value, or empty when the key is absent. */
    Optional<Object> getRaw(String key);

    /**
     * Sets a value in memory.
     *
     * @throws IllegalArgumentException when the value violates the attached spec
     */
    Config set(String key, Object value);

    boolean contains(String key);

    /** Reloads from disk, discarding unsaved in-memory changes. */
    Config load();

    /** Writes to disk, creating the file and its comment header if needed. */
    Config save();

    /** Resets every key to its spec default and saves. */
    Config resetToDefaults();
}
