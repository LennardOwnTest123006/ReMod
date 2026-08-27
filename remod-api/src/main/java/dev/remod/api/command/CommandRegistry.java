package dev.remod.api.command;

import java.util.Collection;
import java.util.Optional;

/** Where mods register their commands. */
public interface CommandRegistry {

    /**
     * Registers a command.
     *
     * @throws DuplicateCommandException when the name or an alias is taken
     */
    void register(CommandSpec command);

    /** Convenience overload that builds the spec for you. */
    default void register(CommandBuilder builder) {
        register(builder.build());
    }

    /** Looks a command up by name or alias. */
    Optional<CommandSpec> find(String nameOrAlias);

    /** Every registered command. */
    Collection<CommandSpec> commands();

    /** The mod that registered a command, or empty when it is not registered. */
    Optional<String> ownerOf(String name);
}
