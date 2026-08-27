package dev.remod.common.log;

/** Severity levels used by {@link ReModLogger}, ordered least to most severe. */
public enum LogLevel {

    TRACE("TRACE"),
    DEBUG("DEBUG"),
    INFO("INFO"),
    WARN("WARN"),
    ERROR("ERROR");

    private final String label;

    LogLevel(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** True when a message at this level should be emitted under {@code threshold}. */
    public boolean isEnabledUnder(LogLevel threshold) {
        return ordinal() >= threshold.ordinal();
    }

    /** Parses a level name case-insensitively, falling back to {@code fallback}. */
    public static LogLevel parse(String text, LogLevel fallback) {
        if (text == null) {
            return fallback;
        }
        for (LogLevel level : values()) {
            if (level.label.equalsIgnoreCase(text.trim())) {
                return level;
            }
        }
        return fallback;
    }
}
