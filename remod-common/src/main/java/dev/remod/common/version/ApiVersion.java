package dev.remod.common.version;

import java.util.Objects;

/**
 * The version of the ReMod API a mod compiles against.
 *
 * <p>An API version is always a pair: the Minecraft <em>series</em> it targets
 * and the ReMod API <em>baseline</em> that describes the API surface, joined
 * with a dash -- for example {@code 1.21-1.0.0}. Keeping the two halves
 * explicit is what lets ReMod say "this mod needs API 1.20-1.0.0 but 1.21-1.0.0
 * is installed" instead of a meaningless number mismatch.</p>
 */
public final class ApiVersion implements Comparable<ApiVersion> {

    private final String minecraftSeries;
    private final SemanticVersion baseline;

    private ApiVersion(String minecraftSeries, SemanticVersion baseline) {
        this.minecraftSeries = minecraftSeries;
        this.baseline = baseline;
    }

    /** Builds the API version for a Minecraft series and an API baseline. */
    public static ApiVersion of(String minecraftSeries, String baseline) {
        if (minecraftSeries == null || minecraftSeries.trim().isEmpty()) {
            throw new InvalidVersionException("API version needs a Minecraft series");
        }
        return new ApiVersion(minecraftSeries.trim(), SemanticVersion.parse(baseline));
    }

    /** Parses {@code <series>-<baseline>}, e.g. {@code 1.21-1.0.0}. */
    public static ApiVersion parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new InvalidVersionException("API version string is empty");
        }
        String trimmed = text.trim();
        int split = trimmed.indexOf('-');
        if (split <= 0 || split == trimmed.length() - 1) {
            throw new InvalidVersionException(
                    "API version '" + trimmed + "' must look like <minecraft-series>-<api-baseline>,"
                            + " for example 1.21-1.0.0");
        }
        return of(trimmed.substring(0, split), trimmed.substring(split + 1));
    }

    public static ApiVersion tryParse(String text) {
        try {
            return parse(text);
        } catch (InvalidVersionException e) {
            return null;
        }
    }

    public String minecraftSeries() {
        return minecraftSeries;
    }

    public SemanticVersion baseline() {
        return baseline;
    }

    /**
     * True when a mod built against {@code required} can run on this API.
     *
     * <p>The Minecraft series must match exactly -- Minecraft's own internals
     * change between series, so an API built for another series is not
     * substitutable. Within a series the API follows SemVer: a mod runs on any
     * later baseline with the same major component.</p>
     */
    public boolean satisfies(ApiVersion required) {
        if (required == null) {
            return false;
        }
        if (!minecraftSeries.equals(required.minecraftSeries)) {
            return false;
        }
        if (baseline.major() != required.baseline.major()) {
            return false;
        }
        return baseline.compareTo(required.baseline) >= 0;
    }

    @Override
    public int compareTo(ApiVersion other) {
        int result = minecraftSeries.compareTo(other.minecraftSeries);
        return result != 0 ? result : baseline.compareTo(other.baseline);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ApiVersion)) {
            return false;
        }
        ApiVersion that = (ApiVersion) other;
        return minecraftSeries.equals(that.minecraftSeries) && baseline.equals(that.baseline);
    }

    @Override
    public int hashCode() {
        return Objects.hash(minecraftSeries, baseline.raw());
    }

    @Override
    public String toString() {
        return minecraftSeries + "-" + baseline.raw();
    }
}
