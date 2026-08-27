package dev.remod.api.event;

/**
 * The marker every ReMod event implements.
 *
 * <p>Events are plain immutable-ish data objects. They are dispatched by their
 * runtime class, so subscribing to a type also receives every subtype.</p>
 */
public interface Event {
}
