package dev.remod.api.event;

/**
 * Publish/subscribe for ReMod events.
 *
 * <pre>{@code
 * context.events().subscribe(PlayerJoinEvent.class, event ->
 *     event.player().sendMessage(Text.of("Welcome, " + event.player().name())));
 * }</pre>
 *
 * <p>Dispatch is by runtime class including supertypes, so subscribing to
 * {@link Event} receives everything and subscribing to an abstract base
 * receives all its subtypes.</p>
 *
 * <p>A listener that throws is logged against the mod that registered it and
 * then skipped; it never takes down the game or prevents other listeners from
 * running. A listener that throws repeatedly is muted, so one broken mod cannot
 * flood the log at 20 ticks per second.</p>
 */
public interface EventBus {

    /** Subscribes at {@link EventPriority#NORMAL}. */
    <E extends Event> Subscription subscribe(Class<E> type, EventListener<E> listener);

    /** Subscribes at an explicit priority. */
    <E extends Event> Subscription subscribe(Class<E> type, EventPriority priority,
                                             EventListener<E> listener);

    /**
     * Subscribes a listener that removes itself after handling one event.
     * Useful for one-shot setup that must wait for the world to load.
     */
    <E extends Event> Subscription subscribeOnce(Class<E> type, EventListener<E> listener);

    /**
     * Dispatches an event to every matching listener.
     *
     * @return the same event, so callers can inspect cancellation inline
     */
    <E extends Event> E post(E event);

    /** Removes every listener registered through this bus view. */
    void unsubscribeAll();

    /** The number of listeners currently registered for a type, including subtypes. */
    int listenerCount(Class<? extends Event> type);
}
