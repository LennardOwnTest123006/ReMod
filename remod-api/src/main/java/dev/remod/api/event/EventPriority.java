package dev.remod.api.event;

/**
 * Controls the order listeners for the same event run in.
 *
 * <p>Higher priorities run first, so a listener that wants to cancel an event
 * before others see it registers at {@link #HIGHEST}, and one that wants the
 * last word registers at {@link #LOWEST}. Within a priority, registration
 * order decides.</p>
 */
public enum EventPriority {

    HIGHEST(2),
    HIGH(1),
    NORMAL(0),
    LOW(-1),
    LOWEST(-2);

    private final int weight;

    EventPriority(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }
}
