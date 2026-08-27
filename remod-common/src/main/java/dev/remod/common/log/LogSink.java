package dev.remod.common.log;

/** A destination for log records: the console, a file, a GUI panel, a test buffer. */
public interface LogSink extends AutoCloseable {

    void accept(LogRecord record);

    /** Flushes any buffered output. The default is a no-op. */
    default void flush() {
    }

    @Override
    default void close() {
        flush();
    }
}
