package dev.remod.loader.runtime;

/**
 * Thrown when an entrypoint class cannot be turned into a {@code ReModMod}.
 *
 * <p>Carries the specific problem -- missing class, wrong interface, no public
 * constructor, constructor threw -- so the loader can give the author a
 * message that says what to fix.</p>
 */
public class ModInstantiationException extends Exception {

    private static final long serialVersionUID = 1L;

    private final String suggestion;

    public ModInstantiationException(String message, String suggestion, Throwable cause) {
        super(message, cause);
        this.suggestion = suggestion;
    }

    /** What the mod's author should do about it. */
    public String suggestion() {
        return suggestion;
    }
}
