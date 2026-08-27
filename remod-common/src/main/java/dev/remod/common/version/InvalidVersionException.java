package dev.remod.common.version;

/** Thrown when a version or version range cannot be understood. */
public class InvalidVersionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidVersionException(String message) {
        super(message);
    }
}
