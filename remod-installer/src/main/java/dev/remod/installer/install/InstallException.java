package dev.remod.installer.install;

/**
 * An install could not proceed.
 *
 * <p>Always carries a suggestion, because an installer that says only "install
 * failed" leaves the user with nothing to do.</p>
 */
public class InstallException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String suggestion;

    public InstallException(String message, String suggestion) {
        super(message);
        this.suggestion = suggestion;
    }

    public InstallException(String message, String suggestion, Throwable cause) {
        super(message, cause);
        this.suggestion = suggestion;
    }

    public String suggestion() {
        return suggestion;
    }

    @Override
    public String toString() {
        return getMessage() + (suggestion == null ? "" : System.lineSeparator() + suggestion);
    }
}
