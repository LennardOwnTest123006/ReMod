package dev.remod.installer.install;

import dev.remod.adapter.VersionSupportTable;
import dev.remod.common.io.IOUtil;
import dev.remod.common.json.Json;
import dev.remod.common.log.ReModLog;
import dev.remod.common.log.ReModLogger;
import dev.remod.common.net.ProgressListener;
import dev.remod.common.version.ApiVersion;
import dev.remod.compat.CompatibilityRegistry;
import dev.remod.compat.LoaderBridge;
import dev.remod.compat.LoaderPlatform;
import dev.remod.loader.ReModPaths;
import dev.remod.loader.ReModVersions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Installs ReMod into a Minecraft installation.
 *
 * <p>The whole operation is confined to files ReMod owns:</p>
 *
 * <pre>
 * .minecraft/
 *   versions/ReMod-1.21.4/ReMod-1.21.4.json   &lt;- created by ReMod
 *   libraries/dev/remod/...                   &lt;- created by ReMod
 *   remod/                                    &lt;- created by ReMod
 *   launcher_profiles.json                    &lt;- one entry added, backed up first
 * </pre>
 *
 * <p>Nothing else is touched. Vanilla version directories, other loaders'
 * profiles, {@code saves/}, {@code mods/}, {@code resourcepacks/} and
 * {@code options.txt} are never read for writing, let alone modified. That is
 * checked before anything is written, by {@link #validate}.</p>
 */
public final class ReModInstaller {

    private static final ReModLogger LOG = ReModLog.get("ReMod/Install");

    private final BundledLibraries libraries;
    private final CompatibilityRegistry compatibility;

    public ReModInstaller() {
        this(BundledLibraries.load(), CompatibilityRegistry.standard());
    }

    public ReModInstaller(BundledLibraries libraries, CompatibilityRegistry compatibility) {
        this.libraries = libraries;
        this.compatibility = compatibility;
    }

    /**
     * Checks a request without changing anything.
     *
     * @throws InstallException when the install cannot safely proceed
     */
    public void validate(InstallRequest request) {
        String version = request.minecraftVersion();
        if (version == null || version.trim().isEmpty()) {
            throw new InstallException("No Minecraft version was selected.",
                    "Choose a version from the list, then click Install ReMod.");
        }
        if (!VersionSupportTable.isInstallable(version)) {
            throw new InstallException(
                    "ReMod does not support Minecraft " + version + ".",
                    VersionSupportTable.describe(version));
        }
        ReModPaths paths = new ReModPaths(request.minecraftDirectory());
        if (!Files.isDirectory(paths.gameDirectory())) {
            throw new InstallException(
                    "The folder " + paths.gameDirectory() + " does not exist.",
                    "Point ReMod at your .minecraft folder. If Minecraft has never been run on"
                            + " this computer, start the official launcher once first.");
        }
        if (!paths.looksLikeMinecraftDirectory()) {
            throw new InstallException(
                    paths.gameDirectory() + " does not look like a Minecraft installation.",
                    "Choose the folder that contains 'versions' and 'launcher_profiles.json' --"
                            + " usually .minecraft.");
        }
        if (!Files.isWritable(paths.gameDirectory())) {
            throw new InstallException(
                    "ReMod cannot write to " + paths.gameDirectory() + ".",
                    "Check the folder's permissions, and close the Minecraft Launcher if it is"
                            + " running.");
        }
        Path vanilla = paths.versionsDirectory().resolve(version);
        if (!Files.isDirectory(vanilla)) {
            LOG.warn("Minecraft " + version + " is not downloaded yet. ReMod's installation"
                    + " inherits from it, so the launcher will download it on first launch.");
        }
    }

    /**
     * Performs the install.
     *
     * @param progress receives step updates for the GUI; may be {@code null}
     */
    public InstallResult install(InstallRequest request, ProgressListener progress) {
        ProgressListener listener = progress == null ? ProgressListener.NONE : progress;
        validate(request);

        String version = request.minecraftVersion();
        ReModPaths paths = new ReModPaths(request.minecraftDirectory());
        String versionId = ReModVersions.launcherVersionId(version);
        List<String> notes = new ArrayList<>();

        try {
            listener.step("Preparing ReMod directories");
            paths.createDirectories();

            listener.step("Installing ReMod libraries");
            int installed = libraries.installInto(paths.librariesDirectory());

            listener.step("Writing the launcher version files");
            Path versionDirectory = paths.versionsDirectory().resolve(versionId);
            Files.createDirectories(versionDirectory);
            Path versionJson = versionDirectory.resolve(versionId + ".json");
            IOUtil.writeAtomically(versionJson,
                    Json.writePretty(VersionJsonGenerator.generate(version,
                            libraries.libraries())) + System.lineSeparator());

            listener.step("Installing the ReMod API for mod development");
            Path apiJar = installApi(paths, version);

            boolean profileCreated = false;
            if (request.createLauncherProfile()) {
                listener.step("Adding the launcher installation");
                profileCreated = new LauncherProfileWriter(paths)
                        .addProfile(version, request.gameDirectoryOverride());
            }

            listener.step("Checking for other mod loaders");
            addCompatibilityNotes(paths, notes);
            addSupportNote(version, notes);

            listener.step("Done");
            InstallResult result = new InstallResult(version, versionId, versionDirectory,
                    paths.modsDirectory(), apiJar, installed, profileCreated, notes);
            LOG.info("Installed ReMod " + ReModVersions.loaderVersion()
                    + " for Minecraft " + version);
            return result;
        } catch (IOException e) {
            throw new InstallException(
                    "The install failed while writing files: " + e.getMessage(),
                    "Check there is free disk space and that the Minecraft Launcher is closed,"
                            + " then try again.", e);
        }
    }

    /**
     * Copies the API jar to {@code remod/api/} under a name carrying the
     * Minecraft series, so a developer can compile against exactly the API their
     * chosen version uses.
     */
    private Path installApi(ReModPaths paths, String minecraftVersion) throws IOException {
        ApiVersion apiVersion = ReModVersions.apiVersionFor(minecraftVersion);
        if (apiVersion == null) {
            return null;
        }
        Path target = paths.apiDirectory()
                .resolve(ReModVersions.apiArtifactName(apiVersion.minecraftSeries()));
        if (libraries.installDeveloperApi(target)) {
            LOG.info("Installed ReMod API " + apiVersion + " to " + target);
            return target;
        }
        LOG.warn("This ReMod build carries no developer API jar, so nothing was written to "
                + paths.apiDirectory() + ". Mod projects will need the API jar supplied"
                + " another way.");
        return null;
    }

    private void addCompatibilityNotes(ReModPaths paths, List<String> notes) {
        Map<LoaderPlatform, LoaderBridge.Detection> detected = compatibility.detectAll(paths);
        for (Map.Entry<LoaderPlatform, LoaderBridge.Detection> entry : detected.entrySet()) {
            LoaderBridge bridge = compatibility.bridgeFor(entry.getKey()).orElse(null);
            if (bridge == null) {
                continue;
            }
            notes.add(entry.getKey().displayName() + " was detected in this installation."
                    + " It keeps its own launcher profile and mods folder, so both can stay"
                    + " installed -- but " + entry.getKey().displayName()
                    + " mods do not work with ReMod. See docs/compatibility.md.");
        }
    }

    private void addSupportNote(String version, List<String> notes) {
        switch (VersionSupportTable.supportFor(version)) {
            case PARTIAL:
                notes.add(VersionSupportTable.describe(version));
                break;
            default:
                notes.add("Mods load and receive the full ReMod lifecycle and event stream."
                        + " Binding items, blocks and commands into the running game is not"
                        + " active in ReMod " + ReModVersions.loaderVersion()
                        + " -- see docs/version-support.md.");
                break;
        }
    }

    /** The libraries this installer will place. */
    public BundledLibraries libraries() {
        return libraries;
    }
}
