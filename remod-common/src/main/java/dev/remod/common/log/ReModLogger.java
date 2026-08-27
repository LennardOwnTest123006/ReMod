package dev.remod.common.log;

import java.time.Instant;
import java.util.function.Supplier;

/**
 * The logger every ReMod component and every mod writes through.
 *
 * <p>Each logger has a channel name that appears in the output. The loader uses
 * {@code ReMod}; every mod gets a logger named after its mod id, so a line in
 * the log always identifies who produced it:</p>
 *
 * <pre>
 * [12:04:11.201] [ReMod/INFO] Loaded 3 mods
 * [12:04:11.203] [simplemod/INFO] Hello from ReMod Simple Mod
 * </pre>
 */
public final class ReModLogger {

    private final String channel;
    private final LogRouter router;

    ReModLogger(String channel, LogRouter router) {
        this.channel = channel;
        this.router = router;
    }

    public String channel() {
        return channel;
    }

    public boolean isEnabled(LogLevel level) {
        return level.isEnabledUnder(router.threshold());
    }

    public void trace(String message) {
        log(LogLevel.TRACE, message, null);
    }

    public void debug(String message) {
        log(LogLevel.DEBUG, message, null);
    }

    public void info(String message) {
        log(LogLevel.INFO, message, null);
    }

    public void warn(String message) {
        log(LogLevel.WARN, message, null);
    }

    public void warn(String message, Throwable error) {
        log(LogLevel.WARN, message, error);
    }

    public void error(String message) {
        log(LogLevel.ERROR, message, null);
    }

    public void error(String message, Throwable error) {
        log(LogLevel.ERROR, message, error);
    }

    /**
     * Logs a lazily built message, so expensive formatting is skipped entirely
     * when the level is disabled.
     */
    public void debug(Supplier<String> message) {
        if (isEnabled(LogLevel.DEBUG)) {
            log(LogLevel.DEBUG, message.get(), null);
        }
    }

    public void trace(Supplier<String> message) {
        if (isEnabled(LogLevel.TRACE)) {
            log(LogLevel.TRACE, message.get(), null);
        }
    }

    public void log(LogLevel level, String message, Throwable error) {
        if (!isEnabled(level)) {
            return;
        }
        router.dispatch(new LogRecord(Instant.now(), level, channel,
                message == null ? "null" : message, error, Thread.currentThread().getName()));
    }

    /** Returns a logger for a sub-channel, e.g. {@code ReMod/Installer}. */
    public ReModLogger child(String suffix) {
        return new ReModLogger(channel + "/" + suffix, router);
    }
}
