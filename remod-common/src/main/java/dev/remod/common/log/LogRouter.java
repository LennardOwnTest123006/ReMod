package dev.remod.common.log;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Fans a record out to every installed sink. Package-private plumbing for {@link ReModLog}. */
final class LogRouter {

    private final List<LogSink> sinks = new CopyOnWriteArrayList<>();
    private volatile LogLevel threshold = LogLevel.INFO;

    LogLevel threshold() {
        return threshold;
    }

    void threshold(LogLevel level) {
        this.threshold = level == null ? LogLevel.INFO : level;
    }

    void addSink(LogSink sink) {
        if (sink != null) {
            sinks.add(sink);
        }
    }

    void removeSink(LogSink sink) {
        sinks.remove(sink);
    }

    List<LogSink> sinks() {
        return sinks;
    }

    void dispatch(LogRecord record) {
        for (LogSink sink : sinks) {
            try {
                sink.accept(record);
            } catch (RuntimeException e) {
                // One broken sink must not silence the others or crash the caller.
                System.err.println("[ReMod] Log sink " + sink.getClass().getName()
                        + " failed: " + e);
            }
        }
    }

    void flush() {
        for (LogSink sink : sinks) {
            try {
                sink.flush();
            } catch (RuntimeException e) {
                // ignored
            }
        }
    }

    void closeAll() {
        for (LogSink sink : sinks) {
            try {
                sink.close();
            } catch (Exception e) {
                // ignored
            }
        }
        sinks.clear();
    }
}
