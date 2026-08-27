package dev.remod.api.command;

/** Thrown when two mods register the same command name or alias. */
public class DuplicateCommandException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DuplicateCommandException(String name, String existingOwner, String newOwner) {
        super("/" + name + " is already registered by mod '" + existingOwner + "'. Mod '"
                + newOwner + "' tried to register it as well. Rename one of the two commands,"
                + " or prefix them with their mod ids.");
    }
}
