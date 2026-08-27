package dev.remod.common.version;

import java.util.regex.Pattern;

/**
 * Helpers for the shapes Minecraft version ids actually take.
 *
 * <p>Mojang ships four distinct id styles: releases ({@code 1.21.4}), release
 * candidates and pre-releases ({@code 1.21-rc1}, {@code 1.20.2-pre3}),
 * week-stamped snapshots ({@code 24w14a}) and a handful of historic oddities
 * ({@code 1.14.4 Combat Test}, {@code rd-132211}). ReMod keys everything off
 * the release <em>series</em>, so it needs to answer "which series is this?"
 * for all of them.</p>
 */
public final class MinecraftVersions {

    private static final Pattern SNAPSHOT = Pattern.compile("^\\d{2}w\\d{2}[a-z~]$");
    private static final Pattern RELEASE = Pattern.compile("^\\d+\\.\\d+(\\.\\d+)?$");
    private static final Pattern PRE_RELEASE =
            Pattern.compile("^(\\d+\\.\\d+(?:\\.\\d+)?)[-\\s]?(?:pre|rc|Pre-Release|Release Candidate).*$");

    private MinecraftVersions() {
    }

    /** True for ids like {@code 24w14a}. */
    public static boolean isWeeklySnapshot(String id) {
        return id != null && SNAPSHOT.matcher(id.trim()).matches();
    }

    /** True for ids like {@code 1.21} or {@code 1.21.4}. */
    public static boolean isStableRelease(String id) {
        return id != null && RELEASE.matcher(id.trim()).matches();
    }

    /** True for ids like {@code 1.21-pre1} or {@code 1.20.2-rc2}. */
    public static boolean isPreRelease(String id) {
        return id != null && PRE_RELEASE.matcher(id.trim()).matches();
    }

    /**
     * The {@code major.minor} release series for an id, or {@code null} when the
     * id carries no series information (weekly snapshots and historic builds).
     *
     * <p>Weekly snapshots deliberately return {@code null}: the target release
     * of {@code 24w14a} is not derivable from its name, and guessing would let
     * ReMod claim support it cannot honour.</p>
     */
    public static String series(String id) {
        if (id == null) {
            return null;
        }
        String trimmed = id.trim();
        if (isWeeklySnapshot(trimmed)) {
            return null;
        }
        java.util.regex.Matcher pre = PRE_RELEASE.matcher(trimmed);
        String numeric = pre.matches() ? pre.group(1) : trimmed;
        if (!RELEASE.matcher(numeric).matches()) {
            return null;
        }
        SemanticVersion version = SemanticVersion.tryParse(numeric);
        return version == null || !version.isNumeric() ? null : version.series();
    }

    /**
     * The version an id sorts as for comparison purposes, or {@code null} when
     * it cannot be ordered against releases.
     */
    public static SemanticVersion comparable(String id) {
        String series = series(id);
        if (series == null) {
            return null;
        }
        java.util.regex.Matcher pre = PRE_RELEASE.matcher(id.trim());
        return SemanticVersion.tryParse(pre.matches() ? pre.group(1) : id.trim());
    }
}
