package dev.remod.cli;

import java.io.PrintStream;

/**
 * The CLI's output.
 *
 * <p>An interface rather than direct {@code System.out} use, so tests can
 * capture what a command printed and assert on it.</p>
 */
public class Console {

    private final PrintStream out;
    private final PrintStream err;

    public Console(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    public static Console standard() {
        return new Console(System.out, System.err);
    }

    public void print(String line) {
        out.println(line);
    }

    public void blank() {
        out.println();
    }

    /** A heading, underlined so long output stays readable in a terminal. */
    public void heading(String text) {
        out.println();
        out.println(text);
        out.println("-".repeat(Math.max(3, text.length())));
    }

    public void bullet(String text) {
        out.println("  - " + text);
    }

    /** A key/value line, aligned into a column. */
    public void field(String key, String value) {
        out.printf("  %-22s %s%n", key + ":", value);
    }

    public void success(String text) {
        out.println(text);
    }

    /** An error with a suggested fix, both to standard error. */
    public void error(String text, String suggestion) {
        err.println();
        err.println("Error: " + text);
        if (suggestion != null && !suggestion.isEmpty()) {
            err.println(suggestion);
        }
    }

    public PrintStream out() {
        return out;
    }

    public PrintStream err() {
        return err;
    }
}
