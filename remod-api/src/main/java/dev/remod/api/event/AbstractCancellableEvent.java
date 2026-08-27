package dev.remod.api.event;

/** Convenience base class implementing {@link Cancellable}. */
public abstract class AbstractCancellableEvent implements Cancellable {

    private boolean cancelled;

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
