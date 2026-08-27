package dev.remod.loader.discovery;

import dev.remod.api.mod.ModMetadata;

import java.nio.file.Path;
import java.util.Objects;

/** A mod ReMod found on disk whose manifest parsed successfully. */
public final class ModCandidate {

    private final Path path;
    private final ModSourceKind kind;
    private final ModMetadata metadata;
    private final long fileSize;

    public ModCandidate(Path path, ModSourceKind kind, ModMetadata metadata, long fileSize) {
        this.path = Objects.requireNonNull(path, "path");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.fileSize = fileSize;
    }

    public Path path() {
        return path;
    }

    public ModSourceKind kind() {
        return kind;
    }

    public ModMetadata metadata() {
        return metadata;
    }

    public String id() {
        return metadata.id();
    }

    /** Size in bytes, or -1 for a directory. */
    public long fileSize() {
        return fileSize;
    }

    /** The file name, used throughout ReMod's user-facing messages. */
    public String fileName() {
        return path.getFileName().toString();
    }

    @Override
    public String toString() {
        return metadata.id() + " " + metadata.version().raw() + " (" + fileName() + ")";
    }
}
