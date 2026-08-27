package dev.remod.installer.install;

import dev.remod.common.io.IOUtil;
import dev.remod.common.json.Json;
import dev.remod.common.json.JsonException;
import dev.remod.common.json.JsonObject;
import dev.remod.common.log.ReModLog;
import dev.remod.common.log.ReModLogger;
import dev.remod.loader.ReModPaths;
import dev.remod.loader.ReModVersions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Adds and removes ReMod entries in {@code launcher_profiles.json}.
 *
 * <p>This file belongs to the user, not to ReMod: it holds every installation
 * they have, and in some launcher versions their account list. Three rules
 * follow, and are enforced here:</p>
 *
 * <ol>
 *   <li><b>Never rewrite what we did not create.</b> Existing keys are read and
 *       written back untouched; only ReMod's own profile entry is added or
 *       replaced.</li>
 *   <li><b>Back up before the first change.</b> A copy is kept as
 *       {@code launcher_profiles.json.remod-backup} so a mistake is
 *       recoverable.</li>
 *   <li><b>Refuse rather than guess.</b> If the file is missing or unparseable,
 *       the install stops with an explanation instead of writing a fresh file
 *       that would discard the user's installations.</li>
 * </ol>
 */
public final class LauncherProfileWriter {

    private static final ReModLogger LOG = ReModLog.get("ReMod/Install");
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    /** The suffix used for the one-time backup. */
    public static final String BACKUP_SUFFIX = ".remod-backup";

    private final ReModPaths paths;

    public LauncherProfileWriter(ReModPaths paths) {
        this.paths = paths;
    }

    /** The profile key ReMod uses for one Minecraft version. */
    public static String profileKey(String minecraftVersion) {
        return "remod-" + minecraftVersion;
    }

    /**
     * Adds or updates the ReMod profile for one Minecraft version.
     *
     * @param gameDirectoryOverride a dedicated game directory, or {@code null}
     *                              to use the default {@code .minecraft}
     * @return true when the file was changed
     */
    public boolean addProfile(String minecraftVersion, Path gameDirectoryOverride) {
        Path file = paths.launcherProfilesFile();
        JsonObject root = readOrRefuse(file);
        backupOnce(file);

        JsonObject profiles = root.optObject("profiles");
        String key = profileKey(minecraftVersion);
        String now = TIMESTAMP.format(Instant.now());

        JsonObject existing = profiles.optObject(key);
        JsonObject profile = new JsonObject();
        profile.put("name", "ReMod " + minecraftVersion);
        profile.put("type", "custom");
        profile.put("lastVersionId", ReModVersions.launcherVersionId(minecraftVersion));
        profile.put("created", existing.optString("created", now));
        profile.put("lastUsed", existing.optString("lastUsed", now));
        profile.putIfPresent("gameDir",
                gameDirectoryOverride == null ? null : gameDirectoryOverride.toString());
        // Preserve anything the user customised on a previous ReMod profile.
        profile.putIfPresent("javaArgs", existing.optString("javaArgs", null));
        profile.putIfPresent("icon", existing.optString("icon", null));
        profile.putIfPresent("javaDir", existing.optString("javaDir", null));

        profiles.put(key, profile);
        root.put("profiles", profiles);
        write(file, root);
        LOG.info("Added the launcher profile 'ReMod " + minecraftVersion + "'");
        return true;
    }

    /**
     * Removes ReMod's profile for one Minecraft version.
     *
     * @return true when a profile was removed
     */
    public boolean removeProfile(String minecraftVersion) {
        Path file = paths.launcherProfilesFile();
        if (!Files.isRegularFile(file)) {
            return false;
        }
        JsonObject root = readOrRefuse(file);
        JsonObject profiles = root.optObject("profiles");
        String key = profileKey(minecraftVersion);
        if (!profiles.has(key)) {
            return false;
        }
        backupOnce(file);
        JsonObject remaining = new JsonObject();
        for (String existingKey : profiles.keys()) {
            if (!existingKey.equals(key)) {
                remaining.put(existingKey, profiles.get(existingKey));
            }
        }
        root.put("profiles", remaining);
        write(file, root);
        LOG.info("Removed the launcher profile 'ReMod " + minecraftVersion + "'");
        return true;
    }

    /** The ReMod profile for a version, when one is installed. */
    public Optional<JsonObject> findProfile(String minecraftVersion) {
        Path file = paths.launcherProfilesFile();
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            JsonObject root = Json.parseObject(Files.readString(file, StandardCharsets.UTF_8));
            JsonObject profiles = root.optObject("profiles");
            String key = profileKey(minecraftVersion);
            return profiles.has(key) ? Optional.of(profiles.getObject(key)) : Optional.empty();
        } catch (IOException | JsonException e) {
            return Optional.empty();
        }
    }

    /**
     * Reads the launcher profile file, refusing to continue when it is absent
     * or damaged rather than replacing the user's installations.
     */
    private JsonObject readOrRefuse(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new InstallException(
                    "No launcher_profiles.json was found at " + file + ".",
                    "Open the official Minecraft Launcher once and let it start, then run the"
                            + " ReMod installer again. ReMod will not create this file itself,"
                            + " because doing so could discard installations the launcher"
                            + " has not written yet.");
        }
        try {
            return Json.parseObject(Files.readString(file, StandardCharsets.UTF_8));
        } catch (JsonException e) {
            throw new InstallException(
                    "launcher_profiles.json at " + file + " is not valid JSON: " + e.getMessage(),
                    "ReMod will not overwrite a file it cannot read, because your existing"
                            + " installations are in it. Fix or delete the file and let the"
                            + " Minecraft Launcher regenerate it, then try again.", e);
        } catch (IOException e) {
            throw new InstallException(
                    "Could not read " + file + ": " + e.getMessage(),
                    "Close the Minecraft Launcher, check the file is not read-only, and try"
                            + " again.", e);
        }
    }

    /** Copies the file aside once, so the user's original is always recoverable. */
    private void backupOnce(Path file) {
        Path backup = file.resolveSibling(file.getFileName() + BACKUP_SUFFIX);
        if (Files.exists(backup)) {
            return;
        }
        try {
            Files.copy(file, backup, StandardCopyOption.COPY_ATTRIBUTES);
            LOG.info("Backed up launcher_profiles.json to " + backup.getFileName());
        } catch (IOException e) {
            LOG.warn("Could not back up " + file + " (" + e.getMessage()
                    + "); continuing without a backup");
        }
    }

    private void write(Path file, JsonObject root) {
        try {
            IOUtil.writeAtomically(file, Json.writePretty(root) + System.lineSeparator());
        } catch (IOException e) {
            throw new InstallException(
                    "Could not write " + file + ": " + e.getMessage(),
                    "Close the Minecraft Launcher (it locks this file while running) and try"
                            + " again.", e);
        }
    }
}
