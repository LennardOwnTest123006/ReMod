package dev.remod.api.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A fully described command, produced by {@link CommandBuilder}.
 *
 * <p>ReMod keeps commands as data so the version adapter can translate them
 * into whatever the running Minecraft uses -- Brigadier on modern versions,
 * and the older command handler on pre-1.13 builds -- without the mod caring.</p>
 */
public final class CommandSpec {

    private final String name;
    private final List<String> aliases;
    private final String description;
    private final String usage;
    private final int permissionLevel;
    private final List<Argument> arguments;
    private final CommandExecutor executor;
    private final List<CommandSpec> subcommands;

    CommandSpec(String name, List<String> aliases, String description, String usage,
                int permissionLevel, List<Argument> arguments, CommandExecutor executor,
                List<CommandSpec> subcommands) {
        this.name = name;
        this.aliases = Collections.unmodifiableList(new ArrayList<>(aliases));
        this.description = description;
        this.usage = usage;
        this.permissionLevel = permissionLevel;
        this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
        this.executor = executor;
        this.subcommands = Collections.unmodifiableList(new ArrayList<>(subcommands));
    }

    public String name() {
        return name;
    }

    public List<String> aliases() {
        return aliases;
    }

    public String description() {
        return description;
    }

    /** The usage line shown on a syntax error, generated when not set explicitly. */
    public String usage() {
        return usage;
    }

    public int permissionLevel() {
        return permissionLevel;
    }

    public List<Argument> arguments() {
        return arguments;
    }

    /** The body, or {@code null} for a command that only groups subcommands. */
    public CommandExecutor executor() {
        return executor;
    }

    public List<CommandSpec> subcommands() {
        return subcommands;
    }

    @Override
    public String toString() {
        return "/" + name;
    }

    /** One declared argument. */
    public static final class Argument {

        private final String name;
        private final ArgumentType type;
        private final boolean required;
        private final String defaultValue;

        Argument(String name, ArgumentType type, boolean required, String defaultValue) {
            this.name = name;
            this.type = type;
            this.required = required;
            this.defaultValue = defaultValue;
        }

        public String name() {
            return name;
        }

        public ArgumentType type() {
            return type;
        }

        public boolean isRequired() {
            return required;
        }

        /** The value used when an optional argument is omitted, or {@code null}. */
        public String defaultValue() {
            return defaultValue;
        }

        @Override
        public String toString() {
            return (required ? "<" : "[") + name + (required ? ">" : "]");
        }
    }
}
