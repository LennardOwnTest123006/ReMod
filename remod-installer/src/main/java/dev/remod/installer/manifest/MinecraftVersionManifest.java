package dev.remod.installer.manifest;

import dev.remod.common.json.Json;
import dev.remod.common.json.JsonException;
import dev.remod.common.json.JsonObject;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Mojang's version manifest, parsed.
 *
 * <p>ReMod does not hard-code a list of Minecraft versions. It reads the same
 * manifest the official launcher does, so a version released tomorrow appears
 * in ReMod's list with no ReMod update -- {@link
 * dev.remod.adapter.VersionSupportTable} then decides whether it is installable.</p>
 */
public final class MinecraftVersionManifest {

    /** Mojang's published manifest. The v2 endpoint adds SHA-1s and compliance data. */
    public static final String MANIFEST_URL =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    private final String latestRelease;
    private final String latestSnapshot;
    private final List<MinecraftVersionEntry> versions;
    private final Map<String, MinecraftVersionEntry> byId;

    private MinecraftVersionManifest(String latestRelease, String latestSnapshot,
                                     List<MinecraftVersionEntry> versions) {
        this.latestRelease = latestRelease;
        this.latestSnapshot = latestSnapshot;
        this.versions = Collections.unmodifiableList(new ArrayList<>(versions));
        Map<String, MinecraftVersionEntry> index = new LinkedHashMap<>();
        for (MinecraftVersionEntry entry : versions) {
            index.put(entry.id(), entry);
        }
        this.byId = Collections.unmodifiableMap(index);
    }

    /**
     * Parses the manifest JSON.
     *
     * @throws ManifestException when the document is not a usable manifest
     */
    public static MinecraftVersionManifest parse(String json) {
        JsonObject root;
        try {
            root = Json.parseObject(json);
        } catch (JsonException e) {
            throw new ManifestException("The Minecraft version manifest could not be parsed: "
                    + e.getMessage(),
                    "This usually means the download was truncated or intercepted by a proxy."
                            + " Try again, or check your network.", e);
        }
        JsonObject latest = root.optObject("latest");
        String latestRelease = latest.optString("release", null);
        String latestSnapshot = latest.optString("snapshot", null);

        List<MinecraftVersionEntry> versions = new ArrayList<>();
        for (JsonObject entry : root.optArray("versions").objects()) {
            String id = entry.optString("id", null);
            if (id == null || id.isEmpty()) {
                // A malformed entry is skipped rather than failing the whole list.
                continue;
            }
            versions.add(new MinecraftVersionEntry(
                    id,
                    MinecraftVersionEntry.Type.parse(entry.optString("type", null)),
                    entry.optString("url", null),
                    entry.optString("sha1", null),
                    parseTime(entry.optString("releaseTime", null))));
        }
        if (versions.isEmpty()) {
            throw new ManifestException("The Minecraft version manifest contained no versions.",
                    "Mojang's service may be having problems. Try again shortly.", null);
        }
        return new MinecraftVersionManifest(latestRelease, latestSnapshot, versions);
    }

    private static Instant parseTime(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return java.time.OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** The id Mojang marks as the latest release. */
    public Optional<String> latestRelease() {
        return Optional.ofNullable(latestRelease);
    }

    /** The id Mojang marks as the latest snapshot. */
    public Optional<String> latestSnapshot() {
        return Optional.ofNullable(latestSnapshot);
    }

    /** Every version, in the manifest's own order: newest first. */
    public List<MinecraftVersionEntry> versions() {
        return versions;
    }

    public Optional<MinecraftVersionEntry> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** Versions of one kind only. */
    public List<MinecraftVersionEntry> ofType(MinecraftVersionEntry.Type type) {
        List<MinecraftVersionEntry> filtered = new ArrayList<>();
        for (MinecraftVersionEntry entry : versions) {
            if (entry.type() == type) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    /**
     * Versions whose id or series contains {@code query}, case-insensitively.
     * An empty query matches everything.
     */
    public List<MinecraftVersionEntry> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return versions;
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<MinecraftVersionEntry> matches = new ArrayList<>();
        for (MinecraftVersionEntry entry : versions) {
            if (entry.id().toLowerCase(Locale.ROOT).contains(needle)) {
                matches.add(entry);
            }
        }
        return matches;
    }

    public int size() {
        return versions.size();
    }
}
