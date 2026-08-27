package dev.remod.loader.runtime;

import dev.remod.api.event.Event;
import dev.remod.api.event.EventBus;
import dev.remod.api.event.EventListener;
import dev.remod.api.event.EventPriority;
import dev.remod.api.event.Subscription;
import dev.remod.common.log.ReModLog;
import dev.remod.common.log.ReModLogger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The shared event bus.
 *
 * <p>Dispatch walks the event's class hierarchy so subscribing to a supertype
 * receives subtypes. The per-type listener list is built once and cached; the
 * cache is invalidated on subscribe/unsubscribe, which are rare, so the hot
 * path (posting a tick event twenty times a second) is a single map lookup and
 * an array walk with no allocation.</p>
 *
 * <p>Listeners are held in a {@link CopyOnWriteArrayList}, so a listener that
 * subscribes or unsubscribes during dispatch cannot corrupt the iteration.</p>
 */
public final class DefaultEventBus implements EventBus {

    private static final ReModLogger LOG = ReModLog.get("ReMod/Events");

    /**
     * How many times one listener may throw before ReMod stops calling it.
     * Without this, a broken tick listener writes twenty stack traces a second.
     */
    private static final int FAILURE_LIMIT = 5;

    private final Map<Class<?>, CopyOnWriteArrayList<Registration<?>>> byType =
            new ConcurrentHashMap<>();
    private final Map<Class<?>, List<Registration<?>>> dispatchCache = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public <E extends Event> Subscription subscribe(Class<E> type, EventListener<E> listener) {
        return subscribe(type, EventPriority.NORMAL, listener);
    }

    @Override
    public <E extends Event> Subscription subscribe(Class<E> type, EventPriority priority,
                                                    EventListener<E> listener) {
        return subscribe(type, priority, listener, "ReMod", false);
    }

    @Override
    public <E extends Event> Subscription subscribeOnce(Class<E> type, EventListener<E> listener) {
        return subscribe(type, EventPriority.NORMAL, listener, "ReMod", true);
    }

    /** Subscribes with an owner recorded, so failures name the responsible mod. */
    public <E extends Event> Subscription subscribe(Class<E> type, EventPriority priority,
                                                    EventListener<E> listener, String ownerModId,
                                                    boolean once) {
        if (type == null || listener == null) {
            throw new IllegalArgumentException("Event type and listener must both be given");
        }
        Registration<E> registration = new Registration<>(type,
                priority == null ? EventPriority.NORMAL : priority,
                listener, ownerModId, once, sequence.incrementAndGet(), this);
        byType.computeIfAbsent(type, key -> new CopyOnWriteArrayList<>()).add(registration);
        dispatchCache.clear();
        return registration;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E extends Event> E post(E event) {
        if (event == null) {
            return null;
        }
        List<Registration<?>> listeners = listenersFor(event.getClass());
        for (Registration<?> registration : listeners) {
            if (!registration.active.get()) {
                continue;
            }
            try {
                ((Registration<E>) registration).listener.handle(event);
                if (registration.once) {
                    registration.unsubscribe();
                }
            } catch (Throwable failure) {
                handleFailure(registration, event, failure);
            }
        }
        return event;
    }

    private void handleFailure(Registration<?> registration, Event event, Throwable failure) {
        int failures = ++registration.failureCount;
        if (failures <= FAILURE_LIMIT) {
            LOG.error("Mod '" + registration.ownerModId + "' threw while handling "
                    + event.getClass().getSimpleName()
                    + (failures == FAILURE_LIMIT
                            ? " (this listener has now failed " + FAILURE_LIMIT
                              + " times and will be disabled)"
                            : ""),
                    failure);
        }
        if (failures >= FAILURE_LIMIT) {
            // Disable rather than rethrow: one broken mod must not stop the game.
            registration.unsubscribe();
            LOG.warn("Disabled a " + event.getClass().getSimpleName() + " listener from mod '"
                    + registration.ownerModId + "' after repeated failures. The mod is still"
                    + " loaded; its other listeners continue to run.");
        }
    }

    /** Resolves and caches the ordered listener list for one concrete event class. */
    private List<Registration<?>> listenersFor(Class<?> eventClass) {
        List<Registration<?>> cached = dispatchCache.get(eventClass);
        if (cached != null) {
            return cached;
        }
        List<Registration<?>> matching = new ArrayList<>();
        for (Map.Entry<Class<?>, CopyOnWriteArrayList<Registration<?>>> entry : byType.entrySet()) {
            if (entry.getKey().isAssignableFrom(eventClass)) {
                matching.addAll(entry.getValue());
            }
        }
        matching.sort(Comparator
                .comparingInt((Registration<?> r) -> -r.priority.weight())
                .thenComparingLong(r -> r.order));
        List<Registration<?>> immutable = List.copyOf(matching);
        dispatchCache.put(eventClass, immutable);
        return immutable;
    }

    @Override
    public void unsubscribeAll() {
        byType.clear();
        dispatchCache.clear();
    }

    /** Removes every listener registered by one mod. Used when a mod fails to load. */
    public void unsubscribeAllOf(String ownerModId) {
        for (CopyOnWriteArrayList<Registration<?>> list : byType.values()) {
            list.removeIf(registration -> registration.ownerModId.equals(ownerModId));
        }
        dispatchCache.clear();
    }

    @Override
    public int listenerCount(Class<? extends Event> type) {
        int count = 0;
        for (Map.Entry<Class<?>, CopyOnWriteArrayList<Registration<?>>> entry : byType.entrySet()) {
            if (type.isAssignableFrom(entry.getKey())) {
                for (Registration<?> registration : entry.getValue()) {
                    if (registration.active.get()) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private void remove(Registration<?> registration) {
        CopyOnWriteArrayList<Registration<?>> list = byType.get(registration.type);
        if (list != null) {
            list.remove(registration);
        }
        dispatchCache.clear();
    }

    private static final class Registration<E extends Event> implements Subscription {

        private final Class<E> type;
        private final EventPriority priority;
        private final EventListener<E> listener;
        private final String ownerModId;
        private final boolean once;
        private final long order;
        private final DefaultEventBus bus;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private volatile int failureCount;

        Registration(Class<E> type, EventPriority priority, EventListener<E> listener,
                     String ownerModId, boolean once, long order, DefaultEventBus bus) {
            this.type = type;
            this.priority = priority;
            this.listener = listener;
            this.ownerModId = ownerModId;
            this.once = once;
            this.order = order;
            this.bus = bus;
        }

        @Override
        public void unsubscribe() {
            if (active.compareAndSet(true, false)) {
                bus.remove(this);
            }
        }

        @Override
        public boolean isActive() {
            return active.get();
        }
    }
}
