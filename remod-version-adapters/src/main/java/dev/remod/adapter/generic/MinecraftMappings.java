package dev.remod.adapter.generic;

import dev.remod.common.log.ReModLog;
import dev.remod.common.log.ReModLogger;
import dev.remod.common.io.Platform;
import dev.remod.transform.mapping.MappingSet;
import dev.remod.transform.mapping.ProGuardMappingParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The Mojang mappings for the running Minecraft version, when they are present.
 *
 * <p>Minecraft ships obfuscated, so reaching a field like
 * {@code Abilities.mayfly} means knowing it is called {@code c} in this
 * particular build. Mojang publishes that mapping per version, and ReMod's
 * installer downloads it to {@code remod/mappings/&lt;version&gt;.txt}.</p>
 *
 * <p>Absence is a supported state, not a failure. A development environment
 * runs deobfuscated, where the readable name <em>is</em> the runtime name and
 * an empty mapping set gives exactly the right answer by falling through. A
 * stock install without the mapping file simply loses the features that need
 * it, and says so once.</p>
 */
public final class MinecraftMappings {

    private static final ReModLogger LOG = ReModLog.get("ReMod/Mappings");

    private static volatile MappingSet loaded;
    private static volatile boolean attempted;

    private MinecraftMappings() {
    }

    /** Where the installer places the mapping file for a version. */
    public static Path fileFor(Path gameDirectory, String minecraftVersion) {
        return gameDirectory.resolve("remod/mappings").resolve(minecraftVersion + ".txt");
    }

    /**
     * The mappings for the running version, loading them on first use.
     *
     * <p>Never returns null: an empty set means "names are already readable",
     * which is the correct behaviour in a development environment.</p>
     */
    public static MappingSet get(Path gameDirectory, String minecraftVersion) {
        MappingSet current = loaded;
        if (current != null) {
            return current;
        }
        synchronized (MinecraftMappings.class) {
            if (loaded != null) {
                return loaded;
            }
            loaded = load(gameDirectory, minecraftVersion);
            return loaded;
        }
    }

    /** The mappings loaded so far, or an empty set when none have been. */
    public static MappingSet current() {
        MappingSet current = loaded;
        return current == null ? MappingSet.empty() : current;
    }

    private static MappingSet load(Path gameDirectory, String minecraftVersion) {
        attempted = true;
        if (gameDirectory == null || minecraftVersion == null) {
            return MappingSet.empty();
        }
        Path file = fileFor(gameDirectory, minecraftVersion);
        if (!Files.isRegularFile(file)) {
            LOG.warn("No Mojang mappings for Minecraft " + minecraftVersion + " at " + file
                    + ". Features that need to reach the game's own fields are unavailable."
                    + " Reinstall ReMod for this version to download them.");
            return MappingSet.empty();
        }
        try {
            MappingSet mappings = ProGuardMappingParser.parseFile(file);
            LOG.info("Loaded Mojang mappings for Minecraft " + minecraftVersion + ": "
                    + mappings.classCount() + " classes");
            return mappings;
        } catch (IOException | RuntimeException e) {
            LOG.error("Could not read the mappings at " + file
                    + "; continuing without them", e);
            return MappingSet.empty();
        }
    }

    /** True once a load has been attempted, whatever its outcome. */
    public static boolean isLoadAttempted() {
        return attempted;
    }

    /** Forgets the loaded mappings. Used by tests. */
    static void reset() {
        loaded = null;
        attempted = false;
    }

    /** The default game directory, for callers that have no better source. */
    static Path defaultGameDirectory() {
        return Platform.defaultMinecraftDirectory();
    }
}
