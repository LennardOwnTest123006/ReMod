package dev.remod.transform.load;

/**
 * A hook that rewrites a Minecraft class as it is loaded.
 *
 * <p>Implementations receive the class bytes exactly as they appear in the jar
 * and return replacements. Returning the input unchanged is the normal case and
 * costs nothing: {@link TransformingClassLoader} only re-defines a class when a
 * transformer actually returns different bytes.</p>
 */
public interface GameTransformer {

    /** A short name for the log. */
    String name();

    /**
     * True when this transformer wants to see {@code internalName}.
     *
     * <p>Checked before the class bytes are even read, so a transformer that
     * cares about one class does not pay for the thousands it does not.</p>
     *
     * @param internalName the JVM internal name, e.g. {@code net/minecraft/fx}
     */
    boolean handles(String internalName);

    /**
     * Rewrites the class.
     *
     * @return the new bytes, or {@code original} to leave it alone
     * @throws RuntimeException never usefully -- the loader catches anything
     *         thrown here and falls back to the untransformed class, because a
     *         failed transform must degrade rather than prevent the game from
     *         starting
     */
    byte[] transform(String internalName, byte[] original);
}
