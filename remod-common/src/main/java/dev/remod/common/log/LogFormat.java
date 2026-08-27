package dev.remod.common.log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Renders {@link LogRecord}s in ReMod's canonical {@code [HH:mm:ss] [ReMod/INFO] message} form. */
public final class LogFormat {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private LogFormat() {
    }

    /** The one-line form, without the stack trace. */
    public static String line(LogRecord record) {
        StringBuilder sb = new StringBuilder(96);
        sb.append('[').append(TIME.format(record.timestamp())).append(']');
        sb.append(" [").append(record.channel()).append('/').append(record.level().label()).append(']');
        sb.append(' ').append(record.message());
        return sb.toString();
    }

    /** The full form, including a stack trace when the record carries an error. */
    public static String full(LogRecord record) {
        String line = line(record);
        if (record.error() == null) {
            return line;
        }
        StringWriter writer = new StringWriter();
        try (PrintWriter printer = new PrintWriter(writer)) {
            record.error().printStackTrace(printer);
        }
        return line + System.lineSeparator() + writer;
    }
}
