package dev.remod.common.version;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A tolerant semantic-version implementation.
 *
 * <p>Strict SemVer ({@code 1.2.3-beta.1+build}) parses exactly as specified.
 * Minecraft-shaped versions that are not strict SemVer -- {@code 1.21},
 * {@code 1.21.4}, {@code 24w14a}, {@code 1.20.1-rc1} -- also parse, because
 * ReMod has to compare them constantly. Missing numeric components default to
 * zero, so {@code 1.21} and {@code 1.21.0} compare equal.</p>
 */
public final class SemanticVersion implements Comparable<SemanticVersion> {

    private final int major;
    private final int minor;
    private final int patch;
    private final String preRelease;
    private final String build;
    private final String raw;
    /** True when the input did not look like a dotted numeric version at all. */
    private final boolean numeric;

    private SemanticVersion(int major, int minor, int patch, String preRelease,
                            String build, String raw, boolean numeric) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.preRelease = preRelease;
        this.build = build;
        this.raw = raw;
        this.numeric = numeric;
    }

    /**
     * Parses a version string.
     *
     * @throws InvalidVersionException if {@code text} is null or blank
     */
    public static SemanticVersion parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new InvalidVersionException("Version string is empty");
        }
        String raw = text.trim();

        String remainder = raw;
        String build = null;
        int plus = remainder.indexOf('+');
        if (plus >= 0) {
            build = remainder.substring(plus + 1);
            remainder = remainder.substring(0, plus);
        }

        String preRelease = null;
        int dash = remainder.indexOf('-');
        if (dash >= 0) {
            preRelease = remainder.substring(dash + 1);
            remainder = remainder.substring(0, dash);
        }

        String[] parts = remainder.split("\\.", -1);
        int[] numbers = new int[3];
        boolean numeric = true;
        for (int i = 0; i < parts.length && i < 3; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) {
                numeric = false;
                break;
            }
            try {
                numbers[i] = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                numeric = false;
                break;
            }
        }
        if (parts.length == 0) {
            numeric = false;
        }
        if (!numeric) {
            // Snapshot identifiers such as "24w14a" have no numeric ordering we
            // can trust; keep them as opaque tokens that only compare equal to
            // themselves.
            return new SemanticVersion(0, 0, 0, preRelease, build, raw, false);
        }
        return new SemanticVersion(numbers[0], numbers[1], numbers[2],
                preRelease, build, raw, true);
    }

    /** Parses {@code text}, returning {@code null} instead of throwing. */
    public static SemanticVersion tryParse(String text) {
        try {
            return parse(text);
        } catch (InvalidVersionException e) {
            return null;
        }
    }

    public int major() {
        return major;
    }

    public int minor() {
        return minor;
    }

    public int patch() {
        return patch;
    }

    public String preRelease() {
        return preRelease;
    }

    public String build() {
        return build;
    }

    /** The original text this version was parsed from. */
    public String raw() {
        return raw;
    }

    /** False for opaque identifiers such as Minecraft snapshot names. */
    public boolean isNumeric() {
        return numeric;
    }

    public boolean isPreRelease() {
        return preRelease != null && !preRelease.isEmpty();
    }

    /**
     * The {@code major.minor} series this version belongs to, e.g. {@code 1.21}
     * for {@code 1.21.4}. ReMod keys adapters and API artifacts off the series.
     */
    public String series() {
        return numeric ? major + "." + minor : raw;
    }

    @Override
    public int compareTo(SemanticVersion other) {
        if (!numeric || !other.numeric) {
            if (numeric != other.numeric) {
                // Treat opaque versions as "newer than nothing" but never order
                // them against real numbers; fall back to a stable text compare.
                return numeric ? -1 : 1;
            }
            return raw.compareTo(other.raw);
        }
        int result = Integer.compare(major, other.major);
        if (result != 0) {
            return result;
        }
        result = Integer.compare(minor, other.minor);
        if (result != 0) {
            return result;
        }
        result = Integer.compare(patch, other.patch);
        if (result != 0) {
            return result;
        }
        return comparePreRelease(preRelease, other.preRelease);
    }

    /** SemVer rule: a release outranks any pre-release of the same numbers. */
    private static int comparePreRelease(String a, String b) {
        boolean aEmpty = a == null || a.isEmpty();
        boolean bEmpty = b == null || b.isEmpty();
        if (aEmpty && bEmpty) {
            return 0;
        }
        if (aEmpty) {
            return 1;
        }
        if (bEmpty) {
            return -1;
        }
        List<String> aParts = splitDots(a);
        List<String> bParts = splitDots(b);
        int size = Math.max(aParts.size(), bParts.size());
        for (int i = 0; i < size; i++) {
            if (i >= aParts.size()) {
                return -1;
            }
            if (i >= bParts.size()) {
                return 1;
            }
            String ap = aParts.get(i);
            String bp = bParts.get(i);
            Integer an = asInt(ap);
            Integer bn = asInt(bp);
            int result;
            if (an != null && bn != null) {
                result = Integer.compare(an, bn);
            } else if (an != null) {
                result = -1;
            } else if (bn != null) {
                result = 1;
            } else {
                result = ap.compareTo(bp);
            }
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    private static List<String> splitDots(String text) {
        List<String> out = new ArrayList<>();
        for (String part : text.split("\\.")) {
            out.add(part);
        }
        return out;
    }

    private static Integer asInt(String text) {
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SemanticVersion)) {
            return false;
        }
        return compareTo((SemanticVersion) other) == 0;
    }

    @Override
    public int hashCode() {
        return numeric
                ? Objects.hash(major, minor, patch, preRelease == null ? "" : preRelease)
                : raw.hashCode();
    }

    @Override
    public String toString() {
        return raw;
    }
}
