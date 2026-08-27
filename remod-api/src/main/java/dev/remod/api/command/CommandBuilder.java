package dev.remod.api.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Fluent builder for a command.
 *
 * <pre>{@code
 * context.commands().register(
 *         CommandBuilder.create("simplemod")
 *                 .description("ReMod Simple Mod utilities")
 *                 .permissionLevel(0)
 *                 .subcommand(CommandBuilder.create("greet")
 *                         .argument("target", ArgumentType.PLAYER)
 *                         .executes(ctx -> { ... return 1; }))
 *                 .build());
 * }</pre>
 *
 * <p>Validation happens at {@link #build()}, not at registration: a greedy
 * argument in a non-final position or two arguments with the same name are
 * mistakes the author should hear about immediately.</p>
 */
public final class CommandBuilder {

    private final String name;
    private final List<String> aliases = new ArrayList<>();
    private String description = "";
    private String usage;
    private int permissionLevel;
    private final List<CommandSpec.Argument> arguments = new ArrayList<>();
    private CommandExecutor executor;
    private final List<CommandSpec> subcommands = new ArrayList<>();

    private CommandBuilder(String name) {
        this.name = validateName(name);
    }

    public static CommandBuilder create(String name) {
        return new CommandBuilder(name);
    }

    private static String validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("A command needs a name");
        }
        String trimmed = name.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-')) {
                throw new IllegalArgumentException("Command name '" + name
                        + "' contains the illegal character '" + c + "'. Allowed: a-z 0-9 _ -");
            }
        }
        return trimmed;
    }

    public CommandBuilder aliases(String... values) {
        for (String alias : values) {
            aliases.add(validateName(alias));
        }
        return this;
    }

    public CommandBuilder description(String value) {
        this.description = value == null ? "" : value;
        return this;
    }

    /** Overrides the generated usage line. */
    public CommandBuilder usage(String value) {
        this.usage = value;
        return this;
    }

    /** Minecraft's 0-4 scale. 0 is everyone; 2 is the usual operator threshold. */
    public CommandBuilder permissionLevel(int value) {
        if (value < 0 || value > 4) {
            throw new IllegalArgumentException(
                    "permissionLevel for /" + name + " must be between 0 and 4, was " + value);
        }
        this.permissionLevel = value;
        return this;
    }

    /** Adds a required argument. */
    public CommandBuilder argument(String argumentName, ArgumentType type) {
        return addArgument(argumentName, type, true, null);
    }

    /** Adds an optional argument with a default value used when it is omitted. */
    public CommandBuilder optionalArgument(String argumentName, ArgumentType type,
                                           String defaultValue) {
        return addArgument(argumentName, type, false, defaultValue);
    }

    private CommandBuilder addArgument(String argumentName, ArgumentType type,
                                       boolean required, String defaultValue) {
        if (argumentName == null || argumentName.trim().isEmpty()) {
            throw new IllegalArgumentException("An argument of /" + name + " needs a name");
        }
        if (type == null) {
            throw new IllegalArgumentException(
                    "Argument '" + argumentName + "' of /" + name + " needs a type");
        }
        String trimmed = argumentName.trim();
        for (CommandSpec.Argument existing : arguments) {
            if (existing.name().equals(trimmed)) {
                throw new IllegalArgumentException(
                        "/" + name + " already has an argument called '" + trimmed + "'");
            }
        }
        arguments.add(new CommandSpec.Argument(trimmed, type, required, defaultValue));
        return this;
    }

    public CommandBuilder executes(CommandExecutor value) {
        this.executor = value;
        return this;
    }

    public CommandBuilder subcommand(CommandBuilder child) {
        subcommands.add(child.build());
        return this;
    }

    public CommandBuilder subcommand(CommandSpec child) {
        subcommands.add(child);
        return this;
    }

    public CommandSpec build() {
        validate();
        return new CommandSpec(name, aliases, description,
                usage != null ? usage : generateUsage(),
                permissionLevel, arguments, executor, subcommands);
    }

    private void validate() {
        if (executor == null && subcommands.isEmpty()) {
            throw new IllegalStateException("/" + name
                    + " has neither an executes(...) body nor any subcommands, so running it"
                    + " could not do anything. Add one or the other.");
        }
        boolean seenOptional = false;
        for (int i = 0; i < arguments.size(); i++) {
            CommandSpec.Argument argument = arguments.get(i);
            if (argument.type() == ArgumentType.GREEDY_STRING && i != arguments.size() - 1) {
                throw new IllegalStateException("Argument '" + argument.name() + "' of /" + name
                        + " is a GREEDY_STRING, which consumes the rest of the line, so it must"
                        + " be the last argument.");
            }
            if (!argument.isRequired()) {
                seenOptional = true;
            } else if (seenOptional) {
                throw new IllegalStateException("Required argument '" + argument.name()
                        + "' of /" + name + " follows an optional one, which can never parse"
                        + " unambiguously. Move required arguments first.");
            }
        }
        if (!arguments.isEmpty() && !subcommands.isEmpty()) {
            throw new IllegalStateException("/" + name + " declares both arguments and"
                    + " subcommands. Put the arguments on the subcommands instead.");
        }
        // A body alongside subcommands IS allowed: it is how "/fly" toggles
        // while "/fly on" also works, and Minecraft's own command tree supports
        // exactly that shape.
    }

    private String generateUsage() {
        StringBuilder sb = new StringBuilder("/").append(name);
        if (!subcommands.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (CommandSpec sub : subcommands) {
                names.add(sub.name());
            }
            sb.append(" <").append(String.join("|", names)).append('>');
        }
        for (CommandSpec.Argument argument : arguments) {
            sb.append(' ').append(argument);
        }
        return sb.toString();
    }

    /** Convenience for the common "one literal, a few aliases" case. */
    public CommandBuilder alias(String value) {
        return aliases(new String[]{value});
    }

    /** The aliases declared so far, for inspection in tests. */
    public List<String> declaredAliases() {
        return Arrays.asList(aliases.toArray(new String[0]));
    }
}
