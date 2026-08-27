package dev.remod.common.log;

import java.nio.file.Path;

/**
 * The entry point to ReMod's logging system.
 *
 * <p>A process configures the sinks once (console always, a file once the ReMod
 * home directory is known) and then obtains named loggers:</p>
 *
 * <pre>{@code
 * ReModLog.addSink(new ConsoleLogSink());
 * ReModLog.addSink(new FileLogSink(remodHome.resolve("logs/remod.log")));
 * ReModLogger log = ReModLog.get("ReMod");
 * log.info("Starting ReMod");
 * }</pre>
 */
public final class ReModLog {

    private static final LogRouter ROUTER = new LogRouter();
    private static volatile boolean consoleInstalled;

    private ReModLog() {
    }

    /** Returns a logger for {@code channel}, installing a console sink on first use. */
    public static ReModLogger get(String channel) {
        ensureDefaultSink();
        return new ReModLogger(channel == null || channel.isEmpty() ? "ReMod" : channel, ROUTER);
    }

    /** The loader's own logger. */
    public static ReModLogger core() {
        return get("ReMod");
    }

    private static synchronized void ensureDefaultSink() {
        if (!consoleInstalled && ROUTER.sinks().isEmpty()) {
            ROUTER.addSink(new ConsoleLogSink());
            consoleInstalled = true;
        }
    }

    public static void addSink(LogSink sink) {
        ensureDefaultSink();
        ROUTER.addSink(sink);
    }

    public static void removeSink(LogSink sink) {
        ROUTER.removeSink(sink);
    }

    /** Adds a rotating file sink under {@code directory}, returning it. */
    public static FileLogSink addFileSink(Path directory, String fileName) {
        FileLogSink sink = new FileLogSink(directory.resolve(fileName));
        addSink(sink);
        return sink;
    }

    public static LogLevel level() {
        return ROUTER.threshold();
    }

    public static void setLevel(LogLevel level) {
        ROUTER.threshold(level);
    }

    public static void flush() {
        ROUTER.flush();
    }

    /** Closes every sink. Called from the loader's shutdown hook. */
    public static synchronized void shutdown() {
        ROUTER.closeAll();
        consoleInstalled = false;
    }

    /**
     * Drops every sink and returns the level to its default.
     *
     * <p>Distinct from {@link #shutdown()}: this leaves logging usable, and a
     * console sink is reinstalled on the next {@link #get(String)}. Used by
     * tests and by hosts that embed the loader more than once in one process.
     */
    public static synchronized void reset() {
        ROUTER.closeAll();
        consoleInstalled = false;
        ROUTER.threshold(LogLevel.INFO);
    }
}
