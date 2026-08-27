package dev.remod.common.log;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.Instant;

/**
 * Appends log records to a file, rotating the previous run's log aside on open.
 *
 * <p>Writes are line-buffered and flushed on WARN or worse, so a crash still
 * leaves the interesting part of the log on disk without paying a flush per
 * line during normal operation.</p>
 */
public final class FileLogSink implements LogSink {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(ZoneId.systemDefault());

    private final BufferedWriter writer;
    private final Path file;

    public FileLogSink(Path file) {
        this.file = file;
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            rotate(file);
            this.writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to open ReMod log file " + file, e);
        }
    }

    /** Moves an existing log to {@code <name>-<timestamp>.log} so it is never lost. */
    private static void rotate(Path file) throws IOException {
        if (!Files.exists(file) || Files.size(file) == 0) {
            return;
        }
        String name = file.getFileName().toString();
        String base = name.endsWith(".log") ? name.substring(0, name.length() - 4) : name;
        Path archived = file.resolveSibling(base + "-" + STAMP.format(Instant.now()) + ".log");
        try {
            Files.move(file, archived, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // Rotation is a convenience; failing it must not prevent logging.
        }
    }

    public Path file() {
        return file;
    }

    @Override
    public synchronized void accept(LogRecord record) {
        try {
            writer.write(LogFormat.full(record));
            writer.newLine();
            if (record.level().ordinal() >= LogLevel.WARN.ordinal()) {
                writer.flush();
            }
        } catch (IOException e) {
            // A failing log file must never take the game down with it.
            System.err.println("[ReMod] Unable to write to log file " + file + ": " + e.getMessage());
        }
    }

    @Override
    public synchronized void flush() {
        try {
            writer.flush();
        } catch (IOException e) {
            // ignored, see accept()
        }
    }

    @Override
    public synchronized void close() {
        try {
            writer.flush();
            writer.close();
        } catch (IOException e) {
            // ignored, see accept()
        }
    }
}
