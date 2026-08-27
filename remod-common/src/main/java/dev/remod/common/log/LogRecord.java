package dev.remod.common.log;

import java.time.Instant;

/** One immutable log event handed to every {@link LogSink}. */
public final class LogRecord {

    private final Instant timestamp;
    private final LogLevel level;
    private final String channel;
    private final String message;
    private final Throwable error;
    private final String threadName;

    public LogRecord(Instant timestamp, LogLevel level, String channel,
                     String message, Throwable error, String threadName) {
        this.timestamp = timestamp;
        this.level = level;
        this.channel = channel;
        this.message = message;
        this.error = error;
        this.threadName = threadName;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public LogLevel level() {
        return level;
    }

    /** The logger name, e.g. {@code ReMod} or a mod id. */
    public String channel() {
        return channel;
    }

    public String message() {
        return message;
    }

    public Throwable error() {
        return error;
    }

    public String threadName() {
        return threadName;
    }
}
