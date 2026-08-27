package dev.remod.installer.install;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** What an install actually did, for the summary shown to the user. */
public final class InstallResult {

    private final String minecraftVersion;
    private final String versionId;
    private final Path versionDirectory;
    private final Path modsDirectory;
    private final Path apiJar;
    private final int librariesInstalled;
    private final boolean profileCreated;
    private final List<String> notes;

    public InstallResult(String minecraftVersion, String versionId, Path versionDirectory,
                         Path modsDirectory, Path apiJar, int librariesInstalled,
                         boolean profileCreated, List<String> notes) {
        this.minecraftVersion = minecraftVersion;
        this.versionId = versionId;
        this.versionDirectory = versionDirectory;
        this.modsDirectory = modsDirectory;
        this.apiJar = apiJar;
        this.librariesInstalled = librariesInstalled;
        this.profileCreated = profileCreated;
        this.notes = Collections.unmodifiableList(new ArrayList<>(notes));
    }

    public String minecraftVersion() {
        return minecraftVersion;
    }

    /** The launcher version id, e.g. {@code ReMod-1.21.4}. */
    public String versionId() {
        return versionId;
    }

    public Path versionDirectory() {
        return versionDirectory;
    }

    /** Where the user should put their ReMod mods. */
    public Path modsDirectory() {
        return modsDirectory;
    }

    /** The ReMod API jar installed for mod development against this version. */
    public Path apiJar() {
        return apiJar;
    }

    public int librariesInstalled() {
        return librariesInstalled;
    }

    public boolean profileCreated() {
        return profileCreated;
    }

    /** Warnings and observations worth showing, e.g. other loaders detected. */
    public List<String> notes() {
        return notes;
    }

    /** The summary text the GUI and CLI both print. */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("ReMod installed for Minecraft ").append(minecraftVersion)
                .append(System.lineSeparator());
        sb.append(System.lineSeparator());
        sb.append("Launcher installation:  ").append(versionId).append(System.lineSeparator());
        sb.append("Version files:          ").append(versionDirectory)
                .append(System.lineSeparator());
        sb.append("Put your mods in:       ").append(modsDirectory)
                .append(System.lineSeparator());
        if (apiJar != null) {
            sb.append("ReMod API for mods:     ").append(apiJar).append(System.lineSeparator());
        }
        sb.append(System.lineSeparator());
        sb.append("Open the Minecraft Launcher and pick \"ReMod ").append(minecraftVersion)
                .append("\" from the installations list.").append(System.lineSeparator());
        if (!notes.isEmpty()) {
            sb.append(System.lineSeparator()).append("Notes:").append(System.lineSeparator());
            for (String note : notes) {
                sb.append("  - ").append(note).append(System.lineSeparator());
            }
        }
        return sb.toString();
    }
}
