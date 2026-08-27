package dev.remod.loader.sim;

import dev.remod.api.command.ArgumentType;
import dev.remod.api.command.CommandContext;
import dev.remod.api.command.CommandException;
import dev.remod.api.command.CommandRegistry;
import dev.remod.api.command.CommandSource;
import dev.remod.api.command.CommandSpec;
import dev.remod.api.game.PlayerHandle;
import dev.remod.api.game.ServerHandle;
import dev.remod.api.game.Text;
import dev.remod.api.game.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Runs a mod's commands for real, in memory.
 *
 * <p>Given the commands a mod registered and a simulated player, this parses a
 * typed line like {@code /fly on}, walks it to the right subcommand, binds its
 * arguments, and invokes the mod's own executor. Nothing here is faked: the
 * executor that runs is the mod's real code, and its effect on the player is
 * the mod's real effect.</p>
 *
 * <p>This is deliberately a small command parser rather than a tie to
 * Brigadier -- it exists so a mod's command logic can be exercised and shown
 * to work without a running Minecraft, which is exactly what a person needs to
 * believe the mod does what it claims.</p>
 */
public final class SimulatedCommands {

    private final CommandRegistry registry;
    private final SimulatedServer server;

    public SimulatedCommands(CommandRegistry registry, SimulatedServer server) {
        this.registry = registry;
        this.server = server;
    }

    /** The result of running one command line. */
    public static final class Result {

        private final boolean found;
        private final boolean succeeded;
        private final int returnValue;
        private final List<String> feedback;
        private final List<String> errors;

        Result(boolean found, boolean succeeded, int returnValue,
               List<String> feedback, List<String> errors) {
            this.found = found;
            this.succeeded = succeeded;
            this.returnValue = returnValue;
            this.feedback = feedback;
            this.errors = errors;
        }

        /** False when no command matched -- the "Unknown command" case. */
        public boolean commandFound() {
            return found;
        }

        public boolean succeeded() {
            return succeeded;
        }

        public int returnValue() {
            return returnValue;
        }

        /** Everything the command replied to the caller. */
        public List<String> feedback() {
            return feedback;
        }

        /** Everything the command reported as an error. */
        public List<String> errors() {
            return errors;
        }
    }

    /**
     * Runs a typed command line as {@code caller}.
     *
     * @param line the line, with or without a leading slash
     */
    public Result run(String line, PlayerHandle caller) {
        List<String> feedback = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        CommandSource source = new SimulatedSource(caller, server, feedback, errors);

        List<String> tokens = tokenize(line);
        if (tokens.isEmpty()) {
            return new Result(false, false, 0, feedback, errors);
        }
        String name = tokens.get(0);
        Optional<CommandSpec> command = registry.find(name);
        if (command.isEmpty()) {
            return new Result(false, false, 0, feedback, errors);
        }

        Resolution resolution = resolve(command.get(), tokens.subList(1, tokens.size()));
        if (resolution.spec.executor() == null) {
            errors.add("Usage: " + resolution.spec.usage());
            return new Result(true, false, 0, feedback, errors);
        }
        if (caller.permissionLevel() < resolution.spec.permissionLevel()) {
            errors.add("You do not have permission to run this command.");
            return new Result(true, false, 0, feedback, errors);
        }
        try {
            CommandContext context = new SimulatedContext(resolution.spec, source, line,
                    resolution.arguments);
            int returned = resolution.spec.executor().execute(context);
            return new Result(true, true, returned, feedback, errors);
        } catch (CommandException e) {
            errors.add(e.text().plainText());
            return new Result(true, false, 0, feedback, errors);
        } catch (RuntimeException e) {
            errors.add("The command threw " + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage()));
            return new Result(true, false, 0, feedback, errors);
        }
    }

    /** Walks subcommands as far as the tokens match, then binds the rest as arguments. */
    private Resolution resolve(CommandSpec command, List<String> rest) {
        CommandSpec current = command;
        int index = 0;
        outer:
        while (index < rest.size() && !current.subcommands().isEmpty()) {
            String token = rest.get(index).toLowerCase(Locale.ROOT);
            for (CommandSpec sub : current.subcommands()) {
                if (sub.name().equals(token) || sub.aliases().contains(token)) {
                    current = sub;
                    index++;
                    continue outer;
                }
            }
            break;
        }
        Map<String, String> arguments = new LinkedHashMap<>();
        List<CommandSpec.Argument> declared = current.arguments();
        for (int i = 0; i < declared.size(); i++) {
            CommandSpec.Argument argument = declared.get(i);
            int tokenIndex = index + i;
            if (tokenIndex < rest.size()) {
                if (argument.type() == ArgumentType.GREEDY_STRING) {
                    arguments.put(argument.name(),
                            String.join(" ", rest.subList(tokenIndex, rest.size())));
                    break;
                }
                arguments.put(argument.name(), rest.get(tokenIndex));
            } else if (argument.defaultValue() != null) {
                arguments.put(argument.name(), argument.defaultValue());
            }
        }
        return new Resolution(current, arguments);
    }

    private static List<String> tokenize(String line) {
        String trimmed = line == null ? "" : line.trim();
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (Character.isWhitespace(c) && !quoted) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static final class Resolution {

        private final CommandSpec spec;
        private final Map<String, String> arguments;

        Resolution(CommandSpec spec, Map<String, String> arguments) {
            this.spec = spec;
            this.arguments = arguments;
        }
    }

    /** A command source backed by the simulated player and server. */
    private static final class SimulatedSource implements CommandSource {

        private final PlayerHandle player;
        private final ServerHandle server;
        private final List<String> feedback;
        private final List<String> errors;

        SimulatedSource(PlayerHandle player, ServerHandle server,
                        List<String> feedback, List<String> errors) {
            this.player = player;
            this.server = server;
            this.feedback = feedback;
            this.errors = errors;
        }

        @Override
        public String name() {
            return player == null ? "Server" : player.name();
        }

        @Override
        public Optional<PlayerHandle> player() {
            return Optional.ofNullable(player);
        }

        @Override
        public ServerHandle server() {
            return server;
        }

        @Override
        public Vec3 position() {
            return player == null ? Vec3.ZERO : player.position();
        }

        @Override
        public int permissionLevel() {
            return player == null ? 4 : player.permissionLevel();
        }

        @Override
        public void sendFeedback(Text message) {
            feedback.add(message.plainText());
        }

        @Override
        public void sendError(Text message) {
            errors.add(message.plainText());
        }
    }

    /** A command context backed by the parsed arguments. */
    private static final class SimulatedContext implements CommandContext {

        private final CommandSpec spec;
        private final CommandSource source;
        private final String input;
        private final Map<String, String> arguments;

        SimulatedContext(CommandSpec spec, CommandSource source, String input,
                         Map<String, String> arguments) {
            this.spec = spec;
            this.source = source;
            this.input = input;
            this.arguments = arguments;
        }

        @Override
        public CommandSource source() {
            return source;
        }

        @Override
        public String input() {
            return input;
        }

        @Override
        public String getString(String name) {
            String value = arguments.get(name);
            if (value == null) {
                throw new IllegalArgumentException("No argument '" + name + "'");
            }
            return value;
        }

        @Override
        public int getInt(String name) {
            try {
                return Integer.parseInt(getString(name).trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Argument '" + name + "' is not an integer");
            }
        }

        @Override
        public double getDouble(String name) {
            try {
                return Double.parseDouble(getString(name).trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Argument '" + name + "' is not a number");
            }
        }

        @Override
        public boolean getBoolean(String name) {
            return Boolean.parseBoolean(getString(name).trim());
        }

        @Override
        public Optional<PlayerHandle> getPlayer(String name) {
            String playerName = arguments.get(name);
            return playerName == null ? Optional.empty() : source.server().player(playerName);
        }

        @Override
        public Optional<String> optString(String name) {
            return Optional.ofNullable(arguments.get(name));
        }

        @Override
        public boolean has(String name) {
            return arguments.containsKey(name);
        }
    }
}
