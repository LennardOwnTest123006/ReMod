package dev.remod.loader;

import dev.remod.common.version.ApiVersion;
import dev.remod.common.version.MinecraftVersions;

/**
 * The three version numbers ReMod juggles, kept apart on purpose.
 *
 * <ul>
 *   <li><b>Loader version</b> -- {@code 1.0.0}. The version of ReMod itself.</li>
 *   <li><b>API baseline</b> -- {@code 1.0.0}. Describes the API surface.</li>
 *   <li><b>API version</b> -- {@code 1.21-1.0.0}. The baseline paired with a
 *       Minecraft series; this is what a mod declares.</li>
 * </ul>
 */
public final class ReModVersions {

    private ReModVersions() {
    }

    /** The ReMod loader/installer version. */
    public static String loaderVersion() {
        return BuildInfo.LOADER_VERSION;
    }

    /** The API baseline this build ships. */
    public static String apiBaseline() {
        return BuildInfo.API_BASELINE;
    }

    /**
     * The ReMod API version for a Minecraft version.
     *
     * @param minecraftVersion e.g. {@code 1.21.4}
     * @return e.g. {@code 1.21-1.0.0}, or {@code null} when the Minecraft
     *         version has no derivable release series (weekly snapshots)
     */
    public static ApiVersion apiVersionFor(String minecraftVersion) {
        String series = MinecraftVersions.series(minecraftVersion);
        return series == null ? null : ApiVersion.of(series, apiBaseline());
    }

    /** The Maven-style artifact name of the API jar for a Minecraft series. */
    public static String apiArtifactName(String minecraftSeries) {
        return "remod-api-" + minecraftSeries + "-" + apiBaseline() + ".jar";
    }

    /** The version name ReMod installs into the launcher, e.g. {@code ReMod-1.21.4}. */
    public static String launcherVersionId(String minecraftVersion) {
        return "ReMod-" + minecraftVersion;
    }
}
