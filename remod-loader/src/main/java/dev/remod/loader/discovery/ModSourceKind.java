package dev.remod.loader.discovery;

/** Where a discovered mod's files live. */
public enum ModSourceKind {

    /** A packaged {@code .jar} in the mods directory -- how users install mods. */
    JAR,

    /**
     * An exploded directory containing {@code remod.mod.json} and classes.
     * Used during development so a mod can be re-run without repackaging.
     */
    DIRECTORY
}
