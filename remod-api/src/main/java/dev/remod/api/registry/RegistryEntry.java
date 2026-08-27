package dev.remod.api.registry;

import dev.remod.api.game.Identifier;

/**
 * A handle on something a mod registered.
 *
 * <p>Returned by every {@code register} call so a mod can refer to its own
 * content later (in a recipe, a creative tab, a command) without repeating the
 * identifier.</p>
 *
 * @param <D> the definition type that produced this entry
 */
public interface RegistryEntry<D> {

    Identifier id();

    /** The definition originally passed to {@code register}. */
    D definition();

    /** The mod that registered it. */
    String ownerModId();

    /**
     * True once the version adapter has bound this entry to a real Minecraft
     * object. False when ReMod is running without a game attached, or before
     * the game's registries are open.
     */
    boolean isBound();
}
