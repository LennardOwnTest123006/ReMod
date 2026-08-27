package dev.remod.api.game;

import java.util.Locale;
import java.util.Objects;

/**
 * A namespaced id, exactly as Minecraft uses them: {@code namespace:path}.
 *
 * <p>ReMod validates the character set on construction, because an invalid id
 * registered at load time otherwise surfaces much later as an unrelated
 * crash inside the game's own registry code.</p>
 */
public final class Identifier implements Comparable<Identifier> {

    /** The namespace used when a mod does not give one. */
    public static final String DEFAULT_NAMESPACE = "minecraft";

    private final String namespace;
    private final String path;

    private Identifier(String namespace, String path) {
        this.namespace = namespace;
        this.path = path;
    }

    /** Builds an id from its two parts. */
    public static Identifier of(String namespace, String path) {
        validate("namespace", namespace, true);
        validate("path", path, false);
        return new Identifier(namespace.toLowerCase(Locale.ROOT), path.toLowerCase(Locale.ROOT));
    }

    /** Parses {@code namespace:path}, defaulting the namespace to {@code minecraft}. */
    public static Identifier parse(String text) {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("Identifier is empty");
        }
        int colon = text.indexOf(':');
        if (colon < 0) {
            return of(DEFAULT_NAMESPACE, text);
        }
        return of(text.substring(0, colon), text.substring(colon + 1));
    }

    private static void validate(String what, String value, boolean namespace) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Identifier " + what + " is empty");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toLowerCase(value.charAt(i));
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.';
            // Paths may contain slashes (e.g. "block/stone"); namespaces may not.
            if (!ok && !namespace && c == '/') {
                ok = true;
            }
            if (!ok) {
                throw new IllegalArgumentException("Identifier " + what + " '" + value
                        + "' contains the illegal character '" + value.charAt(i) + "'."
                        + " Allowed: a-z 0-9 _ - ."
                        + (namespace ? "" : " and /"));
            }
        }
    }

    public String namespace() {
        return namespace;
    }

    public String path() {
        return path;
    }

    /** A new id in the same namespace with a different path. */
    public Identifier withPath(String newPath) {
        return of(namespace, newPath);
    }

    /** A translation key of the form {@code prefix.namespace.path}, as Minecraft builds them. */
    public String translationKey(String prefix) {
        return prefix + "." + namespace + "." + path.replace('/', '.');
    }

    @Override
    public int compareTo(Identifier other) {
        int result = namespace.compareTo(other.namespace);
        return result != 0 ? result : path.compareTo(other.path);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Identifier)) {
            return false;
        }
        Identifier that = (Identifier) other;
        return namespace.equals(that.namespace) && path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, path);
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
