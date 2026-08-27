package dev.remod.transform.mapping;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A resolved set of Minecraft obfuscation mappings.
 *
 * <p>Minecraft ships obfuscated: the class a mod knows as
 * {@code net.minecraft.world.entity.player.Abilities} is called something like
 * {@code cwx} in the jar the launcher runs, and the name changes every release.
 * Mojang publishes the mapping for each version, and the URL is in the version's
 * own JSON under {@code downloads.client_mappings} -- which ReMod already
 * downloads. Using it is how ReMod reaches game internals without guessing.</p>
 *
 * <p>Lookups go in the direction a mod needs: give a readable name, get the
 * obfuscated one. The reverse direction is kept too, because a stack trace or a
 * loaded class arrives obfuscated and has to be recognised.</p>
 */
public final class MappingSet {

    private final Map<String, ClassMapping> byDeobfuscatedName;
    private final Map<String, ClassMapping> byObfuscatedName;

    MappingSet(Map<String, ClassMapping> byDeobfuscatedName) {
        this.byDeobfuscatedName = Collections.unmodifiableMap(byDeobfuscatedName);
        Map<String, ClassMapping> reverse = new LinkedHashMap<>();
        byDeobfuscatedName.values().forEach(mapping ->
                reverse.put(mapping.obfuscatedName(), mapping));
        this.byObfuscatedName = Collections.unmodifiableMap(reverse);
    }

    /** A mapping set that maps nothing, for a deobfuscated (development) game. */
    public static MappingSet empty() {
        return new MappingSet(new LinkedHashMap<>());
    }

    public boolean isEmpty() {
        return byDeobfuscatedName.isEmpty();
    }

    public int classCount() {
        return byDeobfuscatedName.size();
    }

    /** Looks a class up by its readable name, e.g. {@code net.minecraft.commands.Commands}. */
    public Optional<ClassMapping> classByName(String deobfuscatedName) {
        return Optional.ofNullable(byDeobfuscatedName.get(deobfuscatedName));
    }

    /** Looks a class up by the name it actually has in the jar. */
    public Optional<ClassMapping> classByObfuscatedName(String obfuscatedName) {
        return Optional.ofNullable(byObfuscatedName.get(obfuscatedName));
    }

    /**
     * The runtime name of a class.
     *
     * <p>Falls back to the readable name when nothing maps it, which is the
     * right answer twice over: in a deobfuscated development environment the
     * readable name <em>is</em> the runtime name, and for a class that is not
     * Minecraft's the mapping was never going to contain it.</p>
     */
    public String runtimeClassName(String deobfuscatedName) {
        return classByName(deobfuscatedName)
                .map(ClassMapping::obfuscatedName)
                .orElse(deobfuscatedName);
    }

    /** The runtime name of a field, falling back to the readable name. */
    public String runtimeFieldName(String deobfuscatedClass, String deobfuscatedField) {
        return classByName(deobfuscatedClass)
                .flatMap(mapping -> mapping.field(deobfuscatedField))
                .orElse(deobfuscatedField);
    }

    /** The runtime name of a method, falling back to the readable name. */
    public String runtimeMethodName(String deobfuscatedClass, String deobfuscatedMethod) {
        return classByName(deobfuscatedClass)
                .flatMap(mapping -> mapping.method(deobfuscatedMethod))
                .orElse(deobfuscatedMethod);
    }

    /** One class, with its fields and methods. */
    public static final class ClassMapping {

        private final String deobfuscatedName;
        private final String obfuscatedName;
        private final Map<String, String> fields;
        private final Map<String, String> methods;

        ClassMapping(String deobfuscatedName, String obfuscatedName,
                     Map<String, String> fields, Map<String, String> methods) {
            this.deobfuscatedName = deobfuscatedName;
            this.obfuscatedName = obfuscatedName;
            this.fields = Collections.unmodifiableMap(fields);
            this.methods = Collections.unmodifiableMap(methods);
        }

        /** The readable name, e.g. {@code net.minecraft.commands.Commands}. */
        public String deobfuscatedName() {
            return deobfuscatedName;
        }

        /** The name in the shipped jar, e.g. {@code fx}. */
        public String obfuscatedName() {
            return obfuscatedName;
        }

        /** The runtime name of one field. */
        public Optional<String> field(String deobfuscatedField) {
            return Optional.ofNullable(fields.get(deobfuscatedField));
        }

        /**
         * The runtime name of one method.
         *
         * <p>Keyed by name alone rather than by signature. Overloads therefore
         * collapse to whichever mapping was read last, which is a real
         * limitation -- but every member ReMod looks up is a distinctly named
         * one, and carrying full descriptors would multiply the parse cost of a
         * file with hundreds of thousands of lines.</p>
         */
        public Optional<String> method(String deobfuscatedMethod) {
            return Optional.ofNullable(methods.get(deobfuscatedMethod));
        }

        public Map<String, String> fields() {
            return fields;
        }

        public Map<String, String> methods() {
            return methods;
        }

        @Override
        public String toString() {
            return deobfuscatedName + " -> " + obfuscatedName;
        }
    }
}
