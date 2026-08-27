package dev.remod.api.event.lifecycle;

import dev.remod.api.event.Event;

/**
 * Fired immediately before control passes to Minecraft's own entry point.
 *
 * <p>The last chance to touch anything before the game itself starts.</p>
 */
public final class GameStartingEvent implements Event {

    private final String minecraftVersion;

    public GameStartingEvent(String minecraftVersion) {
        this.minecraftVersion = minecraftVersion;
    }

    public String minecraftVersion() {
        return minecraftVersion;
    }
}
