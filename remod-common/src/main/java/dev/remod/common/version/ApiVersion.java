package dev.remod.common.version;

import java.util.Objects;

/**
 * The version of the ReMod API a mod compiles against.
 *
 * <p>An API version comes in two forms, and the difference decides whether one
 * mod jar works across several Minecraft versions.</p>
 *
 * <h2>Portable: {@code 1.0.0}</h2>
 *
 * <p>Just an API baseline. This is what a mod should normally declare. The
 * ReMod API never references a Minecraft class -- a mod describes an item with
 * {@link dev.remod.common.version.ApiVersion} and friends rather than
 * constructing one, and the version adapter does the translating -- so the API
 * surface is identical on every Minecraft series. A mod built against baseline
 * {@code 1.0.0} therefore runs on <em>every</em> Minecraft version its
 * {@code minecraft} range covers, from one jar.</p>
 *
 * <h2>Pinned: {@code 1.21-1.0.0}</h2>
 *
 * <p>A baseline paired with a Minecraft series. Use this only when a mod
 * genuinely cannot work outside one series. ReMod then refuses to load it
 * anywhere else, and says exactly why.</p>
 *
 * <p>The installed API is always pinned -- ReMod knows which Minecraft version
 * it is running -- so the interesting question is whether the installed one
 * {@linkplain #satisfies satisfies} what a mod asked for.</p>
 */
public final class ApiVersion implements Comparable<ApiVersion> {

    /** {@code null} for a portable API version. */
    private final String minecraftSeries;
    private final SemanticVersion baseline;

    private ApiVersion(String minecraftSeries, SemanticVersion baseline) {
        this.minecraftSeries = minecraftSeries;
        this.baseline = baseline;
    }

    /**
     * Builds an API version pinned to one Minecraft series.
     *
     * @param minecraftSeries e.g. {@code 1.21}; {@code null} for a portable version
     */
    public static ApiVersion of(String minecraftSeries, String baseline) {
        SemanticVersion parsed = SemanticVersion.parse(baseline);
        if (minecraftSeries == null || minecraftSeries.trim().isEmpty()) {
            return new ApiVersion(null, parsed);
        }
        return new ApiVersion(minecraftSeries.trim(), parsed);
    }

    /** Builds a portable API version: a baseline that works on any Minecraft series. */
    public static ApiVersion portable(String baseline) {
        return new ApiVersion(null, SemanticVersion.parse(baseline));
    }

    /**
     * Parses either form: {@code 1.0.0} (portable) or {@code 1.21-1.0.0} (pinned).
     *
     * <p>The two are told apart by shape rather than by a flag: a bare
     * semantic version is portable, and anything with a series prefix is
     * pinned.</p>
     */
    public static ApiVersion parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new InvalidVersionException("API version string is empty");
        }
        String trimmed = text.trim();

        // A bare "1.0.0" is portable. Distinguishing it from "1.21-1.0.0" is a
        // matter of counting dash-separated parts: a portable version has none
        // beyond an optional SemVer pre-release, which parses as one token.
        int split = trimmed.indexOf('-');
        if (split < 0) {
            SemanticVersion baseline = tryBaseline(trimmed);
            if (baseline == null) {
                throw new InvalidVersionException(malformed(trimmed));
            }
            return new ApiVersion(null, baseline);
        }
        if (split == 0 || split == trimmed.length() - 1) {
            throw new InvalidVersionException(malformed(trimmed));
        }

        String head = trimmed.substring(0, split);
        String tail = trimmed.substring(split + 1);
        SemanticVersion pinnedBaseline = tryBaseline(tail);
        if (pinnedBaseline != null && looksLikeSeries(head)) {
            return new ApiVersion(head, pinnedBaseline);
        }
        // Not a series prefix, so this is a portable version with a SemVer
        // pre-release, e.g. "1.0.0-beta.1".
        SemanticVersion portableBaseline = tryBaseline(trimmed);
        if (portableBaseline != null) {
            return new ApiVersion(null, portableBaseline);
        }
        throw new InvalidVersionException(malformed(trimmed));
    }

    private static String malformed(String text) {
        return "API version '" + text + "' is not usable. Write either a portable"
                + " baseline such as \"1.0.0\", which works on every Minecraft version your"
                + " mod supports, or a pinned version such as \"1.21-1.0.0\" if your mod"
                + " only works on one Minecraft series.";
    }

    /** A series is a bare {@code major.minor}, e.g. {@code 1.21}. */
    private static boolean looksLikeSeries(String text) {
        return text.matches("\\d+\\.\\d+");
    }

    private static SemanticVersion tryBaseline(String text) {
        SemanticVersion version = SemanticVersion.tryParse(text);
        return version != null && version.isNumeric() ? version : null;
    }

    public static ApiVersion tryParse(String text) {
        try {
            return parse(text);
        } catch (InvalidVersionException e) {
            return null;
        }
    }

    /** The Minecraft series this is pinned to, or {@code null} when portable. */
    public String minecraftSeries() {
        return minecraftSeries;
    }

    /** True when this version works on any Minecraft series. */
    public boolean isPortable() {
        return minecraftSeries == null;
    }

    public SemanticVersion baseline() {
        return baseline;
    }

    /**
     * True when a mod declaring {@code required} can run against this API.
     *
     * <p>Two independent checks:</p>
     *
     * <ol>
     *   <li><b>Series.</b> A portable requirement matches any installed series --
     *       which is what lets one mod jar cover every Minecraft version its
     *       {@code minecraft} range allows. A pinned requirement must match the
     *       installed series exactly.</li>
     *   <li><b>Baseline.</b> Standard semantic versioning: the same major
     *       component, and an installed baseline no older than the required
     *       one.</li>
     * </ol>
     */
    public boolean satisfies(ApiVersion required) {
        if (required == null) {
            return false;
        }
        if (!required.isPortable()) {
            // The mod pinned itself to a series, so ours must match it. An
            // installed portable API (used by tooling) satisfies any series.
            if (minecraftSeries != null
                    && !minecraftSeries.equals(required.minecraftSeries)) {
                return false;
            }
        }
        if (baseline.major() != required.baseline.major()) {
            return false;
        }
        return baseline.compareTo(required.baseline) >= 0;
    }

    /** The same baseline, pinned to {@code series}. */
    public ApiVersion pinnedTo(String series) {
        return of(series, baseline.raw());
    }

    /** The same baseline, unpinned. */
    public ApiVersion asPortable() {
        return new ApiVersion(null, baseline);
    }

    @Override
    public int compareTo(ApiVersion other) {
        String left = minecraftSeries == null ? "" : minecraftSeries;
        String right = other.minecraftSeries == null ? "" : other.minecraftSeries;
        int result = left.compareTo(right);
        return result != 0 ? result : baseline.compareTo(other.baseline);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ApiVersion)) {
            return false;
        }
        ApiVersion that = (ApiVersion) other;
        return Objects.equals(minecraftSeries, that.minecraftSeries)
                && baseline.equals(that.baseline);
    }

    @Override
    public int hashCode() {
        return Objects.hash(minecraftSeries, baseline.raw());
    }

    @Override
    public String toString() {
        return minecraftSeries == null ? baseline.raw() : minecraftSeries + "-" + baseline.raw();
    }
}
