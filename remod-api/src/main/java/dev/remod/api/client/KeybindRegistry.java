package dev.remod.api.client;

import java.util.Collection;
import java.util.Optional;

/** Where client mods register their keybinds. Client side only. */
public interface KeybindRegistry {

    /**
     * Registers a keybind.
     *
     * @param id          unique id, conventionally {@code <modid>.<action>}
     * @param defaultKey  the key it starts bound to
     * @param category    the heading in the Controls screen
     * @throws IllegalStateException when the id is already registered
     */
    Keybind register(String id, Key defaultKey, String category);

    Optional<Keybind> find(String id);

    Collection<Keybind> keybinds();
}
