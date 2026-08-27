package dev.remod.api.mod;

import dev.remod.api.Side;
import dev.remod.common.json.Json;
import dev.remod.common.json.JsonArray;
import dev.remod.common.json.JsonException;
import dev.remod.common.json.JsonObject;
import dev.remod.common.version.ApiVersion;
import dev.remod.common.version.InvalidVersionException;
import dev.remod.common.version.SemanticVersion;
import dev.remod.common.version.VersionRange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A parsed {@code remod.mod.json} manifest.
 *
 * <p>ReMod mods ship as ordinary {@code .jar} files -- see
 * {@code docs/mod-format.md} for why -- with this manifest at the archive root:</p>
 *
 * <pre>{@code
 * {
 *   "schema": 1,
 *   "id": "simplemod",
 *   "name": "ReMod Simple Mod",
 *   "version": "1.0.0",
 *   "author": "ReMod Developer",
 *   "description": "A simple ReMod example mod.",
 *   "minecraft": "1.21.x",
 *   "remod_api": "1.21-1.0.0",
 *   "side": "common",
 *   "entrypoints": ["dev.example.simplemod.SimpleMod"],
 *   "dependencies": [],
 *   "optional_dependencies": [],
 *   "incompatible": []
 * }
 * }</pre>
 *
 * <p>Every field is validated at parse time. A manifest that would fail later
 * -- an unparseable version range, an id with a space in it, an entrypoint that
 * is not a class name -- fails here instead, where the error can name the file
 * and the field.</p>
 */
public final class ModMetadata {

    /** The manifest file name, at the root of a mod archive. */
    public static final String FILE_NAME = "remod.mod.json";

    /** The manifest schema version this build understands. */
    public static final int SCHEMA_VERSION = 1;

    private final int schema;
    private final String id;
    private final String name;
    private final SemanticVersion version;
    private final List<String> authors;
    private final String description;
    private final VersionRange minecraft;
    private final ApiVersion apiVersion;
    private final Side side;
    private final List<String> entrypoints;
    private final List<ModDependency> dependencies;
    private final String license;
    private final String homepage;
    private final String issues;
    private final Map<String, Object> custom;

    private ModMetadata(Builder builder) {
        this.schema = builder.schema;
        this.id = builder.id;
        this.name = builder.name;
        this.version = builder.version;
        this.authors = Collections.unmodifiableList(new ArrayList<>(builder.authors));
        this.description = builder.description;
        this.minecraft = builder.minecraft;
        this.apiVersion = builder.apiVersion;
        this.side = builder.side;
        this.entrypoints = Collections.unmodifiableList(new ArrayList<>(builder.entrypoints));
        this.dependencies = Collections.unmodifiableList(new ArrayList<>(builder.dependencies));
        this.license = builder.license;
        this.homepage = builder.homepage;
        this.issues = builder.issues;
        this.custom = Collections.unmodifiableMap(new LinkedHashMap<>(builder.custom));
    }

    /**
     * Parses a manifest.
     *
     * @param source a label for error messages, usually the archive path
     */
    public static ModMetadata parse(String json, String source) {
        JsonObject root;
        try {
            root = Json.parseObject(json);
        } catch (JsonException e) {
            throw new ModMetadataException(source, "the manifest is not valid JSON -- "
                    + e.getMessage(), e);
        }
        return parse(root, source);
    }

    /** Parses an already-decoded manifest object. */
    public static ModMetadata parse(JsonObject root, String source) {
        Builder builder = new Builder(source);

        int schema = root.optInt("schema", SCHEMA_VERSION);
        if (schema > SCHEMA_VERSION) {
            throw new ModMetadataException(source, "declares manifest schema " + schema
                    + " but this ReMod build only understands up to " + SCHEMA_VERSION
                    + ". Update ReMod to load this mod.");
        }
        builder.schema = schema;

        builder.id = requireString(root, "id", source);
        validateModId(builder.id, source);
        builder.name = root.optString("name", builder.id);

        String versionText = requireString(root, "version", source);
        try {
            builder.version = SemanticVersion.parse(versionText);
        } catch (InvalidVersionException e) {
            throw new ModMetadataException(source,
                    "'version' is '" + versionText + "', which is not a usable version");
        }

        if (root.hasValue("authors")) {
            builder.authors.addAll(root.optStringList("authors"));
        } else if (root.hasValue("author")) {
            builder.authors.add(root.getString("author"));
        }
        builder.description = root.optString("description", "");

        String minecraftText = requireString(root, "minecraft", source);
        try {
            builder.minecraft = VersionRange.parse(minecraftText);
        } catch (InvalidVersionException e) {
            throw new ModMetadataException(source, "'minecraft' is '" + minecraftText
                    + "', which is not a usable version range -- " + e.getMessage());
        }

        String apiText = requireString(root, "remod_api", source);
        try {
            builder.apiVersion = ApiVersion.parse(apiText);
        } catch (InvalidVersionException e) {
            throw new ModMetadataException(source, "'remod_api' is '" + apiText + "'. "
                    + e.getMessage());
        }

        builder.side = Side.parse(root.optString("side", "common"));

        List<String> entrypoints = new ArrayList<>();
        if (root.hasValue("entrypoints")) {
            entrypoints.addAll(root.optStringList("entrypoints"));
        } else if (root.hasValue("entrypoint")) {
            entrypoints.add(root.getString("entrypoint"));
        }
        if (entrypoints.isEmpty()) {
            throw new ModMetadataException(source, "declares no entrypoints. Add"
                    + " \"entrypoints\": [\"your.package.YourMod\"] naming a class that"
                    + " implements dev.remod.api.ReModMod.");
        }
        for (String entrypoint : entrypoints) {
            validateClassName(entrypoint, source);
        }
        builder.entrypoints.addAll(entrypoints);

        addDependencies(builder, root.optArray("dependencies"),
                ModDependency.Kind.REQUIRED, source);
        addDependencies(builder, root.optArray("optional_dependencies"),
                ModDependency.Kind.OPTIONAL, source);
        addDependencies(builder, root.optArray("incompatible"),
                ModDependency.Kind.INCOMPATIBLE, source);

        builder.license = root.optString("license", null);
        builder.homepage = root.optString("homepage", null);
        builder.issues = root.optString("issues", null);
        if (root.hasValue("custom")) {
            builder.custom.putAll(root.optObject("custom").toMap());
        }
        return new ModMetadata(builder);
    }

    private static void addDependencies(Builder builder, JsonArray array,
                                        ModDependency.Kind kind, String source) {
        for (Object element : array) {
            try {
                if (element instanceof String) {
                    builder.dependencies.add(ModDependency.parse((String) element, kind));
                } else if (element instanceof JsonObject) {
                    JsonObject object = (JsonObject) element;
                    String id = requireString(object, "id", source);
                    String range = object.optString("version", "*");
                    builder.dependencies.add(
                            new ModDependency(id, VersionRange.parse(range), kind));
                } else {
                    throw new ModMetadataException(source, "a dependency entry must be a string"
                            + " like \"othermod@>=1.2\" or an object with 'id' and 'version'");
                }
            } catch (InvalidVersionException e) {
                throw new ModMetadataException(source, "bad dependency entry: " + e.getMessage());
            }
        }
    }

    private static String requireString(JsonObject root, String key, String source) {
        if (!root.hasValue(key)) {
            throw new ModMetadataException(source, "is missing the required field '" + key + "'");
        }
        Object value = root.get(key);
        if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
            throw new ModMetadataException(source,
                    "field '" + key + "' must be a non-empty string");
        }
        return ((String) value).trim();
    }

    /** Mod ids are lower-case, so that file names and namespaces stay portable. */
    static void validateModId(String id, String source) {
        if (id.length() < 2 || id.length() > 64) {
            throw new ModMetadataException(source,
                    "mod id '" + id + "' must be between 2 and 64 characters");
        }
        if (!id.equals(id.toLowerCase(Locale.ROOT))) {
            throw new ModMetadataException(source,
                    "mod id '" + id + "' must be lower-case");
        }
        char first = id.charAt(0);
        if (first < 'a' || first > 'z') {
            throw new ModMetadataException(source,
                    "mod id '" + id + "' must start with a letter");
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-')) {
                throw new ModMetadataException(source, "mod id '" + id
                        + "' contains the illegal character '" + c + "'. Allowed: a-z 0-9 _ -");
            }
        }
    }

    static void validateClassName(String className, String source) {
        if (className == null || className.trim().isEmpty()) {
            throw new ModMetadataException(source, "an entrypoint is empty");
        }
        String trimmed = className.trim();
        if (trimmed.startsWith(".") || trimmed.endsWith(".") || trimmed.contains("..")) {
            throw new ModMetadataException(source,
                    "entrypoint '" + className + "' is not a valid Java class name");
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (!(Character.isJavaIdentifierPart(c) || c == '.' || c == '$')) {
                throw new ModMetadataException(source, "entrypoint '" + className
                        + "' contains the illegal character '" + c + "'");
            }
        }
    }

    public int schema() {
        return schema;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public SemanticVersion version() {
        return version;
    }

    public List<String> authors() {
        return authors;
    }

    public String description() {
        return description;
    }

    /** The Minecraft versions this mod declares support for. */
    public VersionRange minecraft() {
        return minecraft;
    }

    /** The ReMod API version this mod was built against. */
    public ApiVersion apiVersion() {
        return apiVersion;
    }

    public Side side() {
        return side;
    }

    public List<String> entrypoints() {
        return entrypoints;
    }

    public List<ModDependency> dependencies() {
        return dependencies;
    }

    /** Only the dependencies of one kind. */
    public List<ModDependency> dependencies(ModDependency.Kind kind) {
        List<ModDependency> filtered = new ArrayList<>();
        for (ModDependency dependency : dependencies) {
            if (dependency.kind() == kind) {
                filtered.add(dependency);
            }
        }
        return filtered;
    }

    public String license() {
        return license;
    }

    public String homepage() {
        return homepage;
    }

    public String issues() {
        return issues;
    }

    /** Free-form data for tooling; ReMod itself ignores it. */
    public Map<String, Object> custom() {
        return custom;
    }

    /** Serialises back to the manifest form. */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.put("schema", (long) schema);
        json.put("id", id);
        json.put("name", name);
        json.put("version", version.raw());
        if (!authors.isEmpty()) {
            json.put("authors", new JsonArray(authors));
        }
        if (!description.isEmpty()) {
            json.put("description", description);
        }
        json.put("minecraft", minecraft.raw());
        json.put("remod_api", apiVersion.toString());
        json.put("side", side.token());
        json.put("entrypoints", new JsonArray(entrypoints));
        putDependencies(json, "dependencies", ModDependency.Kind.REQUIRED);
        putDependencies(json, "optional_dependencies", ModDependency.Kind.OPTIONAL);
        putDependencies(json, "incompatible", ModDependency.Kind.INCOMPATIBLE);
        json.putIfPresent("license", license);
        json.putIfPresent("homepage", homepage);
        json.putIfPresent("issues", issues);
        if (!custom.isEmpty()) {
            json.put("custom", new JsonObject(custom));
        }
        return json;
    }

    private void putDependencies(JsonObject json, String key, ModDependency.Kind kind) {
        List<ModDependency> filtered = dependencies(kind);
        if (filtered.isEmpty()) {
            return;
        }
        JsonArray array = new JsonArray();
        for (ModDependency dependency : filtered) {
            array.add(dependency.modId() + "@" + dependency.versionRange().raw());
        }
        json.put(key, array);
    }

    @Override
    public String toString() {
        return name + " (" + id + ") " + version.raw();
    }

    private static final class Builder {

        private final String source;
        private int schema = SCHEMA_VERSION;
        private String id;
        private String name;
        private SemanticVersion version;
        private final List<String> authors = new ArrayList<>();
        private String description = "";
        private VersionRange minecraft;
        private ApiVersion apiVersion;
        private Side side = Side.COMMON;
        private final List<String> entrypoints = new ArrayList<>();
        private final List<ModDependency> dependencies = new ArrayList<>();
        private String license;
        private String homepage;
        private String issues;
        private final Map<String, Object> custom = new LinkedHashMap<>();

        Builder(String source) {
            this.source = source;
        }
    }
}
