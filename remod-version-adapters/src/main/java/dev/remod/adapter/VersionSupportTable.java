package dev.remod.adapter;

import dev.remod.common.version.MinecraftVersions;
import dev.remod.common.version.SemanticVersion;
import dev.remod.loader.adapter.MinecraftVersionAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The authoritative statement of which Minecraft versions ReMod supports, and
 * how well.
 *
 * <p>This table is deliberately explicit rather than optimistic. ReMod's
 * installer reads it to decide what to show in the version list, and it is the
 * single place where "we support this" is asserted -- so it is also the single
 * place that must be told the truth.</p>
 *
 * <p>The support levels mean:</p>
 *
 * <ul>
 *   <li><b>FULL</b> -- the launch wrapper installs and starts the game, mods
 *       load, and the adapter binds registrations, commands and events through
 *       reflection against Mojang-mapped classes. Requires the game to be
 *       running against official mappings; see the caveat in
 *       {@code docs/version-support.md}.</li>
 *   <li><b>PARTIAL</b> -- the wrapper installs and starts the game and mods
 *       load and receive lifecycle and ReMod-level events, but some game
 *       bindings are unavailable on that version.</li>
 *   <li><b>UNSUPPORTED</b> -- ReMod refuses to install rather than producing a
 *       profile that would not start.</li>
 * </ul>
 */
public final class VersionSupportTable {

    /**
     * The oldest Minecraft release ReMod targets.
     *
     * <p>1.17 is the boundary where Minecraft moved to Java 17 and to the
     * modern {@code net.minecraft.client.main.Main} layout with a bundled
     * library list. Below it, the launcher's version JSON schema, the Java
     * version and the class layout all differ enough that claiming support
     * would be dishonest.</p>
     */
    public static final String OLDEST_SUPPORTED = "1.17";

    private static final Map<String, MinecraftVersionAdapter.Support> BY_SERIES =
            new LinkedHashMap<>();

    static {
        // Series that the generic modern adapter is written against and tested
        // for at the launcher-integration level.
        BY_SERIES.put("1.21", MinecraftVersionAdapter.Support.FULL);
        BY_SERIES.put("1.20", MinecraftVersionAdapter.Support.FULL);
        BY_SERIES.put("1.19", MinecraftVersionAdapter.Support.FULL);
        BY_SERIES.put("1.18", MinecraftVersionAdapter.Support.PARTIAL);
        BY_SERIES.put("1.17", MinecraftVersionAdapter.Support.PARTIAL);
    }

    private VersionSupportTable() {
    }

    /** The support level for a Minecraft version id. */
    public static MinecraftVersionAdapter.Support supportFor(String minecraftVersion) {
        if (minecraftVersion == null || minecraftVersion.isEmpty()) {
            return MinecraftVersionAdapter.Support.UNSUPPORTED;
        }
        String series = MinecraftVersions.series(minecraftVersion);
        if (series == null) {
            // Weekly snapshots carry no release series, so ReMod cannot know
            // which adapter would apply. Refusing is the honest answer.
            return MinecraftVersionAdapter.Support.UNSUPPORTED;
        }
        MinecraftVersionAdapter.Support known = BY_SERIES.get(series);
        if (known != null) {
            return known;
        }
        // A series newer than anything in the table: the modern layout has been
        // stable since 1.17, so a future release is treated as PARTIAL rather
        // than either refused outright or claimed as fully working.
        SemanticVersion parsed = SemanticVersion.tryParse(series);
        SemanticVersion newest = SemanticVersion.parse(newestKnownSeries());
        if (parsed != null && parsed.isNumeric() && parsed.compareTo(newest) > 0) {
            return MinecraftVersionAdapter.Support.PARTIAL;
        }
        return MinecraftVersionAdapter.Support.UNSUPPORTED;
    }

    /** True when ReMod is willing to install for this version. */
    public static boolean isInstallable(String minecraftVersion) {
        return supportFor(minecraftVersion).isUsable();
    }

    /** A user-facing explanation of the support level. */
    public static String describe(String minecraftVersion) {
        MinecraftVersionAdapter.Support support = supportFor(minecraftVersion);
        String series = MinecraftVersions.series(minecraftVersion);
        switch (support) {
            case EXACT:
            case FULL:
                return "Supported. ReMod installs a launcher profile and loads mods with the"
                        + " full lifecycle and event stream. Binding content into the running"
                        + " game is experimental and needs Mojang-mapped classes.";
            case PARTIAL:
                if (series != null && BY_SERIES.containsKey(series)) {
                    return "Partially supported. ReMod installs and mods load and receive"
                            + " lifecycle and ReMod events, but no content binding layer has"
                            + " been written for this series.";
                }
                return "Newer than any Minecraft version this ReMod build was tested against."
                        + " ReMod will install and mods will load, but game bindings may not"
                        + " work. Update ReMod when support is confirmed.";
            default:
                if (MinecraftVersions.isWeeklySnapshot(minecraftVersion)) {
                    return "Weekly snapshots are not supported: their internals change without"
                            + " notice and ReMod cannot tell which release they target.";
                }
                return "Not supported. ReMod requires Minecraft " + OLDEST_SUPPORTED
                        + " or newer, where the launcher's version format and Java version"
                        + " match what ReMod installs.";
        }
    }

    /** The series this build knows about, newest first. */
    public static List<String> knownSeries() {
        return Collections.unmodifiableList(new ArrayList<>(BY_SERIES.keySet()));
    }

    private static String newestKnownSeries() {
        String newest = OLDEST_SUPPORTED;
        for (String series : BY_SERIES.keySet()) {
            SemanticVersion candidate = SemanticVersion.tryParse(series);
            SemanticVersion current = SemanticVersion.tryParse(newest);
            if (candidate != null && current != null && candidate.compareTo(current) > 0) {
                newest = series;
            }
        }
        return newest;
    }
}
