package dev.remod.common.log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Keeps the most recent records in memory.
 *
 * <p>Used by the installer GUI to render its status pane, and by tests to
 * assert on what was logged. The buffer is bounded so a long-running session
 * cannot leak memory.</p>
 */
public final class MemoryLogSink implements LogSink {

    private final int capacity;
    private final List<LogRecord> records = new ArrayList<>();
    private final List<Consumer<LogRecord>> listeners = new ArrayList<>();

    public MemoryLogSink() {
        this(2000);
    }

    public MemoryLogSink(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    /** Registers a listener notified for every subsequent record. */
    public synchronized void addListener(Consumer<LogRecord> listener) {
        listeners.add(listener);
    }

    @Override
    public void accept(LogRecord record) {
        List<Consumer<LogRecord>> snapshot;
        synchronized (this) {
            records.add(record);
            while (records.size() > capacity) {
                records.remove(0);
            }
            snapshot = new ArrayList<>(listeners);
        }
        for (Consumer<LogRecord> listener : snapshot) {
            listener.accept(record);
        }
    }

    public synchronized List<LogRecord> records() {
        return Collections.unmodifiableList(new ArrayList<>(records));
    }

    public synchronized List<String> lines() {
        List<String> out = new ArrayList<>(records.size());
        for (LogRecord record : records) {
            out.add(LogFormat.line(record));
        }
        return out;
    }

    public synchronized void clear() {
        records.clear();
    }
}
