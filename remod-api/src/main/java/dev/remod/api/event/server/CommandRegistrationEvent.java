package dev.remod.api.event.server;

import dev.remod.api.command.CommandRegistry;
import dev.remod.api.event.Event;

/**
 * Fired when the server (re)builds its command tree.
 *
 * <p>Commands registered during {@code INIT} are added automatically; this
 * event exists for commands whose shape depends on world state, and it fires
 * again after {@code /reload}.</p>
 */
public final class CommandRegistrationEvent implements Event {

    private final CommandRegistry registry;
    private final boolean dedicated;

    public CommandRegistrationEvent(CommandRegistry registry, boolean dedicated) {
        this.registry = registry;
        this.dedicated = dedicated;
    }

    public CommandRegistry registry() {
        return registry;
    }

    /** True on a dedicated server, false for the integrated single-player one. */
    public boolean isDedicated() {
        return dedicated;
    }
}
