package dev.remod.loader.discovery;

import java.nio.file.Path;

/**
 * A file in the mods directory that could not be turned into a candidate.
 *
 * <p>Discovery never throws. A single unreadable or mislabelled file must not
 * stop the other twenty mods from loading, so problems are collected and
 * reported together.</p>
 */
public final class DiscoveryProblem {

    private final Path path;
    private final Kind kind;
    private final String detail;
    private final String suggestion;

    public DiscoveryProblem(Path path, Kind kind, String detail, String suggestion) {
        this.path = path;
        this.kind = kind;
        this.detail = detail;
        this.suggestion = suggestion;
    }

    public Path path() {
        return path;
    }

    public Kind kind() {
        return kind;
    }

    public String detail() {
        return detail;
    }

    /** A concrete next step for the user. */
    public String suggestion() {
        return suggestion;
    }

    public String fileName() {
        return path.getFileName().toString();
    }

    /** Why a file was skipped. */
    public enum Kind {

        /** A jar with no {@code remod.mod.json}: probably for another loader. */
        NOT_A_REMOD_MOD,

        /** The manifest exists but is malformed. */
        INVALID_MANIFEST,

        /** The file could not be read or is not a valid archive. */
        UNREADABLE
    }

    /** The one-line form used in the log. */
    public String summary() {
        return fileName() + ": " + detail;
    }

    @Override
    public String toString() {
        return summary();
    }
}
