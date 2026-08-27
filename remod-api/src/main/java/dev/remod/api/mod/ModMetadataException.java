package dev.remod.api.mod;

/**
 * Thrown when a mod manifest is missing, malformed or self-contradictory.
 *
 * <p>Always names the file and the offending field, because the person reading
 * this message is usually the mod's author trying to work out what they typed
 * wrong.</p>
 */
public class ModMetadataException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String source;

    public ModMetadataException(String source, String message) {
        super(source + ": " + message);
        this.source = source;
    }

    public ModMetadataException(String source, String message, Throwable cause) {
        super(source + ": " + message, cause);
        this.source = source;
    }

    /** The file or archive the bad manifest came from. */
    public String source() {
        return source;
    }
}
