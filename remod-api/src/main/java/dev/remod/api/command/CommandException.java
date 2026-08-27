package dev.remod.api.command;

import dev.remod.api.game.Text;

/**
 * A user-facing command failure.
 *
 * <p>Throwing this prints its message to the caller in red. Any other
 * exception is treated as a mod bug: the caller sees a generic failure and the
 * stack trace goes to the log with the mod's id attached.</p>
 */
public class CommandException extends Exception {

    private static final long serialVersionUID = 1L;

    private final Text text;

    public CommandException(String message) {
        super(message);
        this.text = Text.literal(message);
    }

    public CommandException(Text message) {
        super(message.plainText());
        this.text = message;
    }

    /** The formatted message shown to the caller. */
    public Text text() {
        return text;
    }
}
