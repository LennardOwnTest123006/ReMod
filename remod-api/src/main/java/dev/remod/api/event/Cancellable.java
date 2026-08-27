package dev.remod.api.event;

/**
 * An event a listener can veto.
 *
 * <p>Cancelling does not stop later listeners from running -- they can see
 * that the event was cancelled and un-cancel it. What cancelling does is tell
 * ReMod (and through it, the game) not to perform the default action.</p>
 */
public interface Cancellable extends Event {

    boolean isCancelled();

    void setCancelled(boolean cancelled);

    default void cancel() {
        setCancelled(true);
    }
}
