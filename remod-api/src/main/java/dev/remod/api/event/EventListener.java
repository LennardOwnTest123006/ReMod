package dev.remod.api.event;

/**
 * A listener for one event type.
 *
 * <p>Declared as its own interface rather than reusing {@code Consumer} so that
 * listeners may throw checked exceptions -- ReMod catches them, attributes the
 * failure to the owning mod and keeps the game running.</p>
 */
@FunctionalInterface
public interface EventListener<E extends Event> {

    void handle(E event) throws Exception;
}
