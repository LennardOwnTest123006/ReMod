package dev.remod.api.mod;

import dev.remod.common.version.InvalidVersionException;
import dev.remod.common.version.VersionRange;

import java.util.Objects;

/** One entry from a mod manifest's {@code dependencies} list. */
public final class ModDependency {

    private final String modId;
    private final VersionRange versionRange;
    private final Kind kind;

    public ModDependency(String modId, VersionRange versionRange, Kind kind) {
        this.modId = Objects.requireNonNull(modId, "modId");
        this.versionRange = versionRange == null ? VersionRange.any() : versionRange;
        this.kind = kind == null ? Kind.REQUIRED : kind;
    }

    /** Parses {@code "othermod"} or {@code "othermod@>=1.2"}. */
    public static ModDependency parse(String text, Kind kind) {
        if (text == null || text.trim().isEmpty()) {
            throw new InvalidVersionException("A dependency entry is empty");
        }
        String trimmed = text.trim();
        int at = trimmed.indexOf('@');
        if (at < 0) {
            return new ModDependency(trimmed, VersionRange.any(), kind);
        }
        String id = trimmed.substring(0, at).trim();
        String range = trimmed.substring(at + 1).trim();
        if (id.isEmpty()) {
            throw new InvalidVersionException("Dependency '" + text + "' has no mod id");
        }
        return new ModDependency(id, VersionRange.parse(range), kind);
    }

    public String modId() {
        return modId;
    }

    public VersionRange versionRange() {
        return versionRange;
    }

    public Kind kind() {
        return kind;
    }

    public boolean isRequired() {
        return kind == Kind.REQUIRED;
    }

    /** How a dependency affects loading. */
    public enum Kind {

        /** Must be present and satisfy the range, or the mod does not load. */
        REQUIRED,

        /** Loaded before this mod if present, ignored if absent. */
        OPTIONAL,

        /** Must NOT be present, or this mod refuses to load. */
        INCOMPATIBLE
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ModDependency)) {
            return false;
        }
        ModDependency that = (ModDependency) other;
        return modId.equals(that.modId)
                && versionRange.raw().equals(that.versionRange.raw())
                && kind == that.kind;
    }

    @Override
    public int hashCode() {
        return Objects.hash(modId, versionRange.raw(), kind);
    }

    @Override
    public String toString() {
        return modId + "@" + versionRange.raw() + " (" + kind.name().toLowerCase(java.util.Locale.ROOT) + ")";
    }
}
