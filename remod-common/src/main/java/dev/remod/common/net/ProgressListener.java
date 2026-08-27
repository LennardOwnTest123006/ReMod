package dev.remod.common.net;

/** Receives progress while a long-running operation proceeds. */
@FunctionalInterface
public interface ProgressListener {

    /**
     * @param what     a short description of the current step
     * @param done     units completed, or -1 when unknown
     * @param total    total units, or -1 when unknown
     */
    void progress(String what, long done, long total);

    /** A listener that discards everything. */
    ProgressListener NONE = (what, done, total) -> { };

    /** Convenience for step-style updates with no measurable total. */
    default void step(String what) {
        progress(what, -1, -1);
    }
}
