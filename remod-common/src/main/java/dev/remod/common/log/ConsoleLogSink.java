package dev.remod.common.log;

import java.io.PrintStream;

/** Writes log records to {@code System.out}, routing WARN and ERROR to {@code System.err}. */
public final class ConsoleLogSink implements LogSink {

    private final PrintStream out;
    private final PrintStream err;

    public ConsoleLogSink() {
        this(System.out, System.err);
    }

    public ConsoleLogSink(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    @Override
    public void accept(LogRecord record) {
        PrintStream stream = record.level().ordinal() >= LogLevel.WARN.ordinal() ? err : out;
        stream.println(LogFormat.full(record));
    }

    @Override
    public void flush() {
        out.flush();
        err.flush();
    }

    @Override
    public void close() {
        // Never close System.out/System.err; only flush them.
        flush();
    }
}
