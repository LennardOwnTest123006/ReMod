package dev.remod.api.event;

/** A handle for undoing one {@code subscribe} call. */
public interface Subscription extends AutoCloseable {

    /** Removes the listener. Calling this more than once is harmless. */
    void unsubscribe();

    /** True until {@link #unsubscribe()} is called. */
    boolean isActive();

    @Override
    default void close() {
        unsubscribe();
    }
}
