package dev.remod.loader.discovery;

import dev.remod.api.mod.ModMetadata;
import dev.remod.api.mod.ModMetadataException;
import dev.remod.common.io.SafeZip;
import dev.remod.common.log.ReModLog;
import dev.remod.common.log.ReModLogger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Finds ReMod mods in a directory.
 *
 * <p>Scanning is deliberately forgiving. Every failure mode -- an unreadable
 * file, a jar for another loader, a manifest with a typo -- becomes a reported
 * problem rather than an exception, so one bad file never stops the rest of the
 * mods from loading.</p>
 *
 * <p>Both packaged jars and exploded directories are supported. The latter is
 * what makes {@code remod run} able to launch a mod straight from a Gradle
 * build directory without repackaging it.</p>
 */
public final class ModDiscovery {

    private static final ReModLogger LOG = ReModLog.get("ReMod/Discovery");

    /**
     * Manifest paths belonging to other loaders. Finding one of these instead of
     * {@code remod.mod.json} tells us exactly which loader the mod is for, which
     * makes for a far better message than "not a ReMod mod".
     */
    private static final Map<String, String> FOREIGN_MANIFESTS = new LinkedHashMap<>();

    static {
        FOREIGN_MANIFESTS.put("fabric.mod.json", "Fabric");
        FOREIGN_MANIFESTS.put("quilt.mod.json", "Quilt");
        FOREIGN_MANIFESTS.put("META-INF/mods.toml", "Forge");
        FOREIGN_MANIFESTS.put("META-INF/neoforge.mods.toml", "NeoForge");
        FOREIGN_MANIFESTS.put("mcmod.info", "Forge (legacy)");
        FOREIGN_MANIFESTS.put("plugin.yml", "Bukkit/Spigot/Paper");
        FOREIGN_MANIFESTS.put("paper-plugin.yml", "Paper");
        FOREIGN_MANIFESTS.put("bungee.yml", "BungeeCord");
        FOREIGN_MANIFESTS.put("velocity-plugin.json", "Velocity");
    }

    private ModDiscovery() {
    }

    /** Scans {@code modsDirectory} for mods. Never throws. */
    public static DiscoveryResult scan(Path modsDirectory) {
        List<ModCandidate> candidates = new ArrayList<>();
        List<DiscoveryProblem> problems = new ArrayList<>();
        List<DiscoveryResult.ForeignMod> foreign = new ArrayList<>();

        if (!Files.isDirectory(modsDirectory)) {
            LOG.debug(() -> "Mods directory " + modsDirectory + " does not exist yet");
            return new DiscoveryResult(candidates, problems, foreign);
        }

        List<Path> entries = new ArrayList<>();
        try (java.util.stream.Stream<Path> stream = Files.list(modsDirectory)) {
            stream.sorted().forEach(entries::add);
        } catch (IOException e) {
            problems.add(new DiscoveryProblem(modsDirectory, DiscoveryProblem.Kind.UNREADABLE,
                    "the mods directory could not be listed (" + e.getMessage() + ")",
                    "Check that ReMod has permission to read " + modsDirectory + "."));
            return new DiscoveryResult(candidates, problems, foreign);
        }

        for (Path entry : entries) {
            String name = entry.getFileName().toString();
            if (name.startsWith(".") || name.toLowerCase(Locale.ROOT).endsWith(".disabled")) {
                // A conventional way to keep a mod installed but switched off.
                LOG.debug(() -> "Skipping disabled entry " + name);
                continue;
            }
            if (Files.isDirectory(entry)) {
                scanDirectory(entry, candidates, problems);
            } else if (SafeZip.isArchiveName(name)) {
                scanArchive(entry, candidates, problems, foreign);
            } else {
                LOG.debug(() -> "Ignoring non-mod file " + name);
            }
        }
        return new DiscoveryResult(candidates, problems, foreign);
    }

    private static void scanArchive(Path archive, List<ModCandidate> candidates,
                                    List<DiscoveryProblem> problems,
                                    List<DiscoveryResult.ForeignMod> foreign) {
        if (!SafeZip.looksLikeZip(archive)) {
            problems.add(new DiscoveryProblem(archive, DiscoveryProblem.Kind.UNREADABLE,
                    "this file has a mod extension but is not a valid archive",
                    "The download may be incomplete or corrupted. Download the mod again."));
            return;
        }
        String manifest;
        try {
            manifest = SafeZip.readEntry(archive, ModMetadata.FILE_NAME);
        } catch (IOException e) {
            problems.add(new DiscoveryProblem(archive, DiscoveryProblem.Kind.UNREADABLE,
                    "the archive could not be read (" + e.getMessage() + ")",
                    "Check the file is not locked by another program, then try again."));
            return;
        }
        if (manifest == null) {
            String otherLoader = detectForeignLoader(archive);
            if (otherLoader != null) {
                foreign.add(new DiscoveryResult.ForeignMod(archive, otherLoader));
                return;
            }
            problems.add(new DiscoveryProblem(archive, DiscoveryProblem.Kind.NOT_A_REMOD_MOD,
                    "no " + ModMetadata.FILE_NAME + " at the archive root",
                    "This does not look like a ReMod mod. If it is for another loader,"
                            + " move it to that loader's mods folder."));
            return;
        }
        addCandidate(archive, ModSourceKind.JAR, manifest, candidates, problems);
    }

    private static void scanDirectory(Path directory, List<ModCandidate> candidates,
                                      List<DiscoveryProblem> problems) {
        Path manifestFile = directory.resolve(ModMetadata.FILE_NAME);
        if (!Files.isRegularFile(manifestFile)) {
            // A plain directory in the mods folder is not an error; users put
            // all sorts of things there.
            LOG.debug(() -> "Directory " + directory.getFileName() + " has no "
                    + ModMetadata.FILE_NAME + "; skipping");
            return;
        }
        String manifest;
        try {
            manifest = Files.readString(manifestFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            problems.add(new DiscoveryProblem(directory, DiscoveryProblem.Kind.UNREADABLE,
                    "could not read " + ModMetadata.FILE_NAME + " (" + e.getMessage() + ")",
                    "Check file permissions on " + manifestFile + "."));
            return;
        }
        addCandidate(directory, ModSourceKind.DIRECTORY, manifest, candidates, problems);
    }

    private static void addCandidate(Path path, ModSourceKind kind, String manifest,
                                     List<ModCandidate> candidates, List<DiscoveryProblem> problems) {
        try {
            ModMetadata metadata = ModMetadata.parse(manifest, path.getFileName().toString());
            long size = kind == ModSourceKind.JAR ? sizeOf(path) : -1;
            candidates.add(new ModCandidate(path, kind, metadata, size));
            LOG.debug(() -> "Found " + metadata.id() + " " + metadata.version().raw()
                    + " in " + path.getFileName());
        } catch (ModMetadataException e) {
            problems.add(new DiscoveryProblem(path, DiscoveryProblem.Kind.INVALID_MANIFEST,
                    e.getMessage(),
                    "Fix " + ModMetadata.FILE_NAME + " in this mod, or report the problem"
                            + " to its author."));
        }
    }

    private static long sizeOf(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return -1;
        }
    }

    /** Identifies which other loader an archive belongs to, or {@code null}. */
    public static String detectForeignLoader(Path archive) {
        for (Map.Entry<String, String> entry : FOREIGN_MANIFESTS.entrySet()) {
            if (SafeZip.hasEntry(archive, entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
}
