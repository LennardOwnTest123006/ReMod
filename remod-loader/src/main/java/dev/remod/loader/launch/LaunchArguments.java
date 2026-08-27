package dev.remod.loader.launch;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The command line the Minecraft launcher hands to the game.
 *
 * <p>ReMod is launched in Minecraft's place, so it must read the launcher's
 * arguments to learn the game directory and version, and then pass them
 * through untouched. Arguments it does not recognise are preserved exactly:
 * the launcher adds new ones between releases, and dropping one would break
 * authentication or quick-play.</p>
 */
public final class LaunchArguments {

    private final String[] raw;
    private final Map<String, String> named = new LinkedHashMap<>();
    private final List<String> positional = new ArrayList<>();

    private LaunchArguments(String[] raw) {
        this.raw = raw.clone();
        parse();
    }

    public static LaunchArguments parse(String[] args) {
        return new LaunchArguments(args == null ? new String[0] : args);
    }

    private void parse() {
        for (int i = 0; i < raw.length; i++) {
            String argument = raw[i];
            if (argument.startsWith("--") && argument.length() > 2) {
                String key = argument.substring(2);
                int equals = key.indexOf('=');
                if (equals > 0) {
                    named.put(key.substring(0, equals), key.substring(equals + 1));
                } else if (i + 1 < raw.length && !raw[i + 1].startsWith("--")) {
                    named.put(key, raw[++i]);
                } else {
                    // A flag with no value, e.g. --demo
                    named.put(key, "true");
                }
            } else {
                positional.add(argument);
            }
        }
    }

    /** The original arguments, to be forwarded to Minecraft unchanged. */
    public String[] raw() {
        return raw.clone();
    }

    public String get(String key, String fallback) {
        String value = named.get(key);
        return value == null ? fallback : value;
    }

    public boolean has(String key) {
        return named.containsKey(key);
    }

    public List<String> positional() {
        return List.copyOf(positional);
    }

    /**
     * The game directory, from {@code --gameDir}, falling back to the working
     * directory -- which is what a dedicated server uses.
     */
    public Path gameDirectory() {
        String value = named.get("gameDir");
        return value == null ? Paths.get(".").toAbsolutePath().normalize()
                : Paths.get(value).toAbsolutePath().normalize();
    }

    /**
     * The Minecraft version.
     *
     * <p>The launcher passes {@code --version <profile name>}, which for a ReMod
     * installation is {@code ReMod-1.21.4}. The real version is recovered from
     * that prefix, and {@code remod.minecraftVersion} overrides both -- ReMod's
     * generated version JSON sets that property so the value never has to be
     * guessed.</p>
     */
    public String minecraftVersion() {
        String property = System.getProperty("remod.minecraftVersion");
        if (property != null && !property.isEmpty()) {
            return property;
        }
        String version = named.get("version");
        if (version == null || version.isEmpty()) {
            return "unknown";
        }
        if (version.startsWith("ReMod-")) {
            return version.substring("ReMod-".length());
        }
        return version;
    }

    /** Replaces or adds one named argument, returning a new argument array. */
    public String[] withNamed(String key, String value) {
        List<String> out = new ArrayList<>();
        boolean replaced = false;
        for (int i = 0; i < raw.length; i++) {
            if (raw[i].equals("--" + key) && i + 1 < raw.length) {
                out.add(raw[i]);
                out.add(value);
                i++;
                replaced = true;
            } else {
                out.add(raw[i]);
            }
        }
        if (!replaced) {
            out.add("--" + key);
            out.add(value);
        }
        return out.toArray(new String[0]);
    }
}
