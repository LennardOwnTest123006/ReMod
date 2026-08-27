package dev.remod.api.registry;

import dev.remod.api.game.Identifier;

/**
 * Thrown when two mods claim the same identifier.
 *
 * <p>Names both mods, because "which mod do I remove?" is the only question the
 * user actually has at that point.</p>
 */
public class DuplicateRegistrationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Identifier id;
    private final String existingOwner;
    private final String newOwner;

    public DuplicateRegistrationException(String registryName, Identifier id,
                                          String existingOwner, String newOwner) {
        super(registryName + " already contains " + id + ", registered by '" + existingOwner
                + "'. Mod '" + newOwner + "' tried to register it again."
                + (existingOwner.equals(newOwner)
                        ? " The same mod registered this id twice."
                        : " Remove one of the two mods, or ask their authors to use"
                          + " distinct namespaces."));
        this.id = id;
        this.existingOwner = existingOwner;
        this.newOwner = newOwner;
    }

    public Identifier id() {
        return id;
    }

    public String existingOwner() {
        return existingOwner;
    }

    public String newOwner() {
        return newOwner;
    }
}
