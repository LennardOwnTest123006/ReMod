package dev.remod.installer.install;

import dev.remod.common.json.JsonArray;
import dev.remod.common.json.JsonObject;
import dev.remod.loader.ReModVersions;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Builds the Minecraft launcher version JSON for a ReMod installation.
 *
 * <p>This is the piece that makes the official launcher show "ReMod 1.21.4" in
 * its installation list. It uses the launcher's real, documented mechanism:</p>
 *
 * <ul>
 *   <li><b>{@code inheritsFrom}</b> points at the vanilla version. The launcher
 *       merges the parent's libraries, assets, downloads and arguments with the
 *       ones here, so ReMod never has to copy or redistribute anything of
 *       Mojang's -- the vanilla version stays untouched on disk and is
 *       downloaded by the launcher as usual.</li>
 *   <li><b>{@code mainClass}</b> is ReMod's launch wrapper, which loads mods
 *       and then calls Minecraft's own main class.</li>
 *   <li><b>{@code libraries}</b> lists the ReMod jars, which the installer has
 *       already placed in {@code libraries/} in Maven layout.</li>
 *   <li><b>{@code arguments.jvm}</b> adds a single system property carrying the
 *       real Minecraft version, so the wrapper never has to guess it from the
 *       profile name.</li>
 * </ul>
 *
 * <p>This is the same approach Forge and Fabric use, and it is what the
 * launcher supports; nothing here relies on undocumented behaviour.</p>
 */
public final class VersionJsonGenerator {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX").withZone(ZoneOffset.UTC);

    /** The launch wrapper the launcher starts instead of Minecraft. */
    public static final String MAIN_CLASS = "dev.remod.loader.launch.ReModLaunch";

    private VersionJsonGenerator() {
    }

    /**
     * Builds the version JSON.
     *
     * @param minecraftVersion the vanilla version to inherit from, e.g. {@code 1.21.4}
     * @param libraries        the ReMod jars to put on the classpath
     */
    public static JsonObject generate(String minecraftVersion,
                                      List<BundledLibraries.Library> libraries) {
        String versionId = ReModVersions.launcherVersionId(minecraftVersion);
        String now = TIMESTAMP.format(Instant.now());

        JsonObject root = new JsonObject();
        root.put("id", versionId);
        // Merges with the vanilla version rather than replacing it, so the
        // original installation is never modified.
        root.put("inheritsFrom", minecraftVersion);
        root.put("type", "release");
        root.put("time", now);
        root.put("releaseTime", now);
        root.put("mainClass", MAIN_CLASS);

        JsonArray libraryArray = new JsonArray();
        for (BundledLibraries.Library library : libraries) {
            libraryArray.add(new JsonObject().put("name", library.coordinate()));
        }
        root.put("libraries", libraryArray);

        JsonObject arguments = new JsonObject();
        JsonArray jvm = new JsonArray();
        // Telling the wrapper the real version explicitly is more robust than
        // parsing it back out of the profile name.
        jvm.add("-Dremod.minecraftVersion=" + minecraftVersion);
        jvm.add("-Dremod.loaderVersion=" + ReModVersions.loaderVersion());
        arguments.put("jvm", jvm);
        // An empty game-argument list still merges with the parent's, which is
        // where the launcher's authentication and asset arguments come from.
        arguments.put("game", new JsonArray());
        root.put("arguments", arguments);

        JsonObject remod = new JsonObject();
        remod.put("loaderVersion", ReModVersions.loaderVersion());
        remod.put("apiBaseline", ReModVersions.apiBaseline());
        remod.put("minecraftVersion", minecraftVersion);
        java.util.Optional.ofNullable(ReModVersions.apiVersionFor(minecraftVersion))
                .ifPresent(api -> remod.put("apiVersion", api.toString()));
        // Custom keys are ignored by the launcher, and let ReMod recognise and
        // safely manage its own installations later.
        root.put("remod", remod);

        return root;
    }

    /** True when a version JSON was produced by ReMod. */
    public static boolean isReModVersion(JsonObject versionJson) {
        return versionJson != null
                && (versionJson.hasValue("remod")
                    || MAIN_CLASS.equals(versionJson.optString("mainClass", null)));
    }

    /** The Minecraft version a ReMod version JSON was installed for. */
    public static String minecraftVersionOf(JsonObject versionJson) {
        String fromRemod = versionJson.optObject("remod")
                .optString("minecraftVersion", null);
        return fromRemod != null ? fromRemod : versionJson.optString("inheritsFrom", null);
    }
}
