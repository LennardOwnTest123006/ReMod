package dev.remod.installer.manifest;

import dev.remod.common.version.MinecraftVersions;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/** One Minecraft version as listed in Mojang's official version manifest. */
public final class MinecraftVersionEntry {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    private final String id;
    private final Type type;
    private final String url;
    private final String sha1;
    private final Instant releaseTime;

    public MinecraftVersionEntry(String id, Type type, String url, String sha1,
                                 Instant releaseTime) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = type == null ? Type.OTHER : type;
        this.url = url;
        this.sha1 = sha1;
        this.releaseTime = releaseTime;
    }

    /** The version id, e.g. {@code 1.21.4}. */
    public String id() {
        return id;
    }

    public Type type() {
        return type;
    }

    /** The URL of this version's own JSON, as given by the manifest. */
    public String url() {
        return url;
    }

    /** The SHA-1 of that JSON, used to verify the download. */
    public String sha1() {
        return sha1;
    }

    public Instant releaseTime() {
        return releaseTime;
    }

    /** The release date in {@code yyyy-MM-dd} form, for the version list. */
    public String releaseDate() {
        return releaseTime == null ? "" : DATE.format(releaseTime);
    }

    /** The {@code major.minor} series, or {@code null} for a weekly snapshot. */
    public String series() {
        return MinecraftVersions.series(id);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MinecraftVersionEntry
                && ((MinecraftVersionEntry) other).id.equals(id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id + " (" + type.label() + ", " + releaseDate() + ")";
    }

    /** The version kinds Mojang publishes. */
    public enum Type {

        RELEASE("release", "Release"),
        SNAPSHOT("snapshot", "Snapshot"),
        OLD_BETA("old_beta", "Beta"),
        OLD_ALPHA("old_alpha", "Alpha"),
        OTHER("other", "Other");

        private final String token;
        private final String label;

        Type(String token, String label) {
            this.token = token;
            this.label = label;
        }

        public String token() {
            return token;
        }

        public String label() {
            return label;
        }

        public static Type parse(String value) {
            if (value != null) {
                for (Type type : values()) {
                    if (type.token.equalsIgnoreCase(value.trim())) {
                        return type;
                    }
                }
            }
            return OTHER;
        }
    }
}
