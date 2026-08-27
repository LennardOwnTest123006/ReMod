package dev.remod.api.registry;

import dev.remod.api.game.Identifier;

import java.util.Collection;
import java.util.Optional;

/**
 * One kind of content registry.
 *
 * <p>Registration is namespaced per mod: a mod may only register identifiers in
 * its own namespace unless it declares otherwise, and a duplicate id is a hard
 * error naming both mods rather than a silent overwrite.</p>
 *
 * @param <D> the definition type accepted by this registry
 */
public interface Registry<D> {

    /** A human-readable name for this registry, used in error messages. */
    String name();

    /**
     * Registers a definition.
     *
     * @throws DuplicateRegistrationException if the id is already registered
     * @throws IllegalStateException          if called outside a registration phase
     */
    RegistryEntry<D> register(D definition);

    Optional<RegistryEntry<D>> get(Identifier id);

    boolean contains(Identifier id);

    /** Every entry in this registry, from every mod. */
    Collection<RegistryEntry<D>> entries();

    /** Every entry registered by one mod. */
    Collection<RegistryEntry<D>> entriesOf(String modId);

    int size();
}
