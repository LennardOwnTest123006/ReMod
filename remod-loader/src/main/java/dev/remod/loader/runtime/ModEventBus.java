package dev.remod.loader.runtime;

import dev.remod.api.event.Event;
import dev.remod.api.event.EventBus;
import dev.remod.api.event.EventListener;
import dev.remod.api.event.EventPriority;
import dev.remod.api.event.Subscription;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * One mod's view of the shared bus.
 *
 * <p>Every subscription made through this view is tagged with the owning mod
 * and remembered, so ReMod can attribute a failure to the right mod and can
 * detach a mod cleanly when it is unloaded.</p>
 */
public final class ModEventBus implements EventBus {

    private final DefaultEventBus delegate;
    private final String modId;
    private final List<Subscription> owned = new CopyOnWriteArrayList<>();

    public ModEventBus(DefaultEventBus delegate, String modId) {
        this.delegate = delegate;
        this.modId = modId;
    }

    @Override
    public <E extends Event> Subscription subscribe(Class<E> type, EventListener<E> listener) {
        return subscribe(type, EventPriority.NORMAL, listener);
    }

    @Override
    public <E extends Event> Subscription subscribe(Class<E> type, EventPriority priority,
                                                    EventListener<E> listener) {
        Subscription subscription = delegate.subscribe(type, priority, listener, modId, false);
        owned.add(subscription);
        return subscription;
    }

    @Override
    public <E extends Event> Subscription subscribeOnce(Class<E> type, EventListener<E> listener) {
        Subscription subscription =
                delegate.subscribe(type, EventPriority.NORMAL, listener, modId, true);
        owned.add(subscription);
        return subscription;
    }

    @Override
    public <E extends Event> E post(E event) {
        return delegate.post(event);
    }

    /** Removes only this mod's listeners, never anyone else's. */
    @Override
    public void unsubscribeAll() {
        for (Subscription subscription : owned) {
            subscription.unsubscribe();
        }
        owned.clear();
    }

    @Override
    public int listenerCount(Class<? extends Event> type) {
        return delegate.listenerCount(type);
    }

    /** The number of listeners this mod currently has registered. */
    public int ownedCount() {
        int count = 0;
        for (Subscription subscription : owned) {
            if (subscription.isActive()) {
                count++;
            }
        }
        return count;
    }
}
