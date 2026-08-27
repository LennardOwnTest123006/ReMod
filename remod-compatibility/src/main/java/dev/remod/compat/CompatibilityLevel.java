package dev.remod.compat;

/**
 * How far ReMod can go with another loader or platform.
 *
 * <p>These levels exist so that ReMod never has to be vague. Every bridge
 * declares one, the installer shows it, and {@code docs/compatibility.md} is
 * generated from the same source -- so the documentation cannot drift away from
 * what the code actually does.</p>
 */
public enum CompatibilityLevel {

    /**
     * ReMod fully interoperates: it detects the other loader, avoids colliding
     * with it, and can load its mods.
     */
    SUPPORTED("Supported"),

    /**
     * ReMod detects the other loader and coexists safely with it -- separate
     * launcher profiles, separate mod folders, no shared state -- but cannot
     * load its mods.
     */
    COEXISTENCE("Coexistence only"),

    /**
     * A bridge exists but is unfinished or unverified. Enabled explicitly by
     * the user, never by default.
     */
    EXPERIMENTAL("Experimental"),

    /**
     * Not achievable without changes to the other project. ReMod says so
     * plainly instead of shipping something that appears to work.
     */
    NOT_POSSIBLE("Not possible");

    private final String label;

    CompatibilityLevel(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
