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
     * The installed ReMod API version for a Minecraft version.
     *
     * <p>Pinned to the Minecraft series, because the loader always knows which
     * version it is running. Mods normally declare the
     * {@linkplain ApiVersion#isPortable() portable} form instead, and this is
     * what their declaration is checked against.</p>
     *
     * @param minecraftVersion e.g. {@code 1.21.4}
     * @return e.g. {@code 1.21-1.0.0}, or {@code null} when the Minecraft
     *         version has no derivable release series (weekly snapshots)
     */
    public static ApiVersion apiVersionFor(String minecraftVersion) {
        String series = MinecraftVersions.series(minecraftVersion);
        return series == null ? null : ApiVersion.of(series, apiBaseline());
    }

    /**
     * The file name of the API jar mods compile against.
     *
     * <p>Named after the baseline alone, with no Minecraft series: the jar's
     * contents are identical on every series, because the API never references
     * a Minecraft class. One name means a mod project's build file keeps
     * working when the developer installs ReMod for another Minecraft version.</p>
     */
    public static String apiArtifactName() {
        return "remod-api-" + apiBaseline() + ".jar";
    }

    /** The version name ReMod installs into the launcher, e.g. {@code ReMod-1.21.4}. */
    public static String launcherVersionId(String minecraftVersion) {
        return "ReMod-" + minecraftVersion;
    }
}
