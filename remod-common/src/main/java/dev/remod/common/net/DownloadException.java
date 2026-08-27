package dev.remod.common.net;

import java.io.IOException;

/** A network or verification failure, phrased so a user can act on it. */
public class DownloadException extends IOException {

    private static final long serialVersionUID = 1L;

    private final String suggestion;

    public DownloadException(String message, String suggestion) {
        super(message);
        this.suggestion = suggestion;
    }

    public DownloadException(String message, String suggestion, Throwable cause) {
        super(message, cause);
        this.suggestion = suggestion;
    }

    /** A concrete next step for the user, e.g. "check your internet connection". */
    public String suggestion() {
        return suggestion;
    }

    @Override
    public String toString() {
        return getMessage() + (suggestion == null ? "" : " -- " + suggestion);
    }
}
