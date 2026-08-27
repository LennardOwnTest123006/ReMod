package dev.remod.installer.manifest;

/** A failure to obtain or understand the Minecraft version manifest. */
public class ManifestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String suggestion;

    public ManifestException(String message, String suggestion, Throwable cause) {
        super(message, cause);
        this.suggestion = suggestion;
    }

    /** A concrete next step for the user. */
    public String suggestion() {
        return suggestion;
    }
}
