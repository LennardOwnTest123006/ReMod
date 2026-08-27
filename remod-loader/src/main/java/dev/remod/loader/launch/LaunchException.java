package dev.remod.loader.launch;

/** A failure that stopped ReMod from starting the game, with a suggested fix. */
public class LaunchException extends Exception {

    private static final long serialVersionUID = 1L;

    private final String suggestion;

    public LaunchException(String message, String suggestion, Throwable cause) {
        super(message, cause);
        this.suggestion = suggestion;
    }

    public String suggestion() {
        return suggestion;
    }
}
