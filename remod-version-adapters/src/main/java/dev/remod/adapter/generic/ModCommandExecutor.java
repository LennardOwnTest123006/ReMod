package dev.remod.adapter.generic;

import dev.remod.api.command.CommandContext;
import dev.remod.api.command.CommandException;
import dev.remod.api.command.CommandSource;
import dev.remod.api.command.CommandSpec;
import dev.remod.api.game.PlayerHandle;
import dev.remod.api.game.ServerHandle;
import dev.remod.api.game.Text;
import dev.remod.api.game.Vec3;
import dev.remod.common.log.ReModLog;
import dev.remod.common.log.ReModLogger;
import dev.remod.transform.GameIntegration;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Runs a mod's command body when Minecraft dispatches the command.
 *
 * <p>Sits between two worlds. Brigadier hands over its own
 * {@code CommandContext} holding Minecraft's obfuscated command source; the mod
 * expects ReMod's {@link CommandContext} and {@link CommandSource}. This
 * adapts one to the other, reflectively, because the source's type has no
 * stable name.</p>
 *
 * <p>Failures are contained: a {@link CommandException} becomes a red message
 * to the caller, and anything else is logged against the owning mod and
 * reported as a failed command rather than propagating into the game's
 * dispatcher.</p>
 */
final class ModCommandExecutor implements GameIntegration.CommandExecutorAdapter {

    private static final ReModLogger LOG = ReModLog.get("ReMod/Commands");

    private final CommandSpec spec;
    private final String ownerModId;

    ModCommandExecutor(CommandSpec spec, String ownerModId) {
        this.spec = spec;
        this.ownerModId = ownerModId;
    }

    @Override
    public int execute(CommandSpec command, Object brigadierContext) {
        if (command.executor() == null) {
            return 0;
        }
        try {
            return command.executor().execute(new BrigadierContext(command, brigadierContext));
        } catch (CommandException e) {
            sendError(brigadierContext, e.text());
            return 0;
        } catch (RuntimeException e) {
            LOG.error("Mod '" + ownerModId + "' threw while running /" + command.name(), e);
            sendError(brigadierContext, Text.literal(
                    "/" + command.name() + " failed. See the ReMod log for details."));
            return 0;
        }
    }

    private void sendError(Object brigadierContext, Text message) {
        try {
            Object source = sourceOf(brigadierContext);
            if (source != null) {
                MinecraftText.sendFailure(source, message);
            }
        } catch (RuntimeException e) {
            LOG.debug(() -> "Could not deliver a command failure message: " + e);
        }
    }

    private static Object sourceOf(Object brigadierContext) {
        if (brigadierContext == null) {
            return null;
        }
        try {
            Method getSource = brigadierContext.getClass().getMethod("getSource");
            return getSource.invoke(brigadierContext);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** ReMod's command context, backed by Brigadier's. */
    private static final class BrigadierContext implements CommandContext {

        private final CommandSpec spec;
        private final Object brigadier;
        private final Object source;

        BrigadierContext(CommandSpec spec, Object brigadier) {
            this.spec = spec;
            this.brigadier = brigadier;
            this.source = sourceOf(brigadier);
        }

        @Override
        public CommandSource source() {
            return new BrigadierSource(source);
        }

        @Override
        public String input() {
            try {
                Method getInput = brigadier.getClass().getMethod("getInput");
                Object value = getInput.invoke(brigadier);
                return value == null ? "" : value.toString();
            } catch (ReflectiveOperationException | RuntimeException e) {
                return "";
            }
        }

        @Override
        public String getString(String name) {
            return argument(name, String.class)
                    .orElseGet(() -> defaultFor(name).orElse(""));
        }

        @Override
        public int getInt(String name) {
            return argument(name, Integer.class)
                    .orElseGet(() -> defaultFor(name).map(ModCommandExecutor::parseInt)
                            .orElse(0));
        }

        @Override
        public double getDouble(String name) {
            return argument(name, Double.class)
                    .orElseGet(() -> defaultFor(name).map(ModCommandExecutor::parseDouble)
                            .orElse(0.0));
        }

        @Override
        public boolean getBoolean(String name) {
            return argument(name, Boolean.class)
                    .orElseGet(() -> defaultFor(name).map(Boolean::parseBoolean).orElse(false));
        }

        @Override
        public Optional<PlayerHandle> getPlayer(String name) {
            // A player argument is parsed as a word and resolved by name, since
            // Minecraft's own player argument type is obfuscated.
            String playerName = getString(name);
            return playerName.isEmpty() ? Optional.empty()
                    : source().server().player(playerName);
        }

        @Override
        public Optional<String> optString(String name) {
            return argument(name, String.class).or(() -> defaultFor(name));
        }

        @Override
        public boolean has(String name) {
            return argument(name, Object.class).isPresent();
        }

        /** Reads one argument out of Brigadier's context, or empty when absent. */
        private <T> Optional<T> argument(String name, Class<T> type) {
            if (brigadier == null) {
                return Optional.empty();
            }
            try {
                Method getArgument = brigadier.getClass()
                        .getMethod("getArgument", String.class, Class.class);
                Object value = getArgument.invoke(brigadier, name, boxed(type));
                return type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
            } catch (ReflectiveOperationException | RuntimeException e) {
                // Brigadier throws when the argument was not supplied, which is
                // the normal path for an optional one.
                return Optional.empty();
            }
        }

        /** The declared default of an optional argument that was omitted. */
        private Optional<String> defaultFor(String name) {
            for (CommandSpec.Argument argument : spec.arguments()) {
                if (argument.name().equals(name)) {
                    return Optional.ofNullable(argument.defaultValue());
                }
            }
            return Optional.empty();
        }

        private static Class<?> boxed(Class<?> type) {
            return type;
        }
    }

    private static int parseInt(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double parseDouble(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /** ReMod's command source, backed by Minecraft's obfuscated one. */
    private static final class BrigadierSource implements CommandSource {

        private final Object source;

        BrigadierSource(Object source) {
            this.source = source;
        }

        @Override
        public String name() {
            return MinecraftPlayers.nameOf(source).orElse("Server");
        }

        @Override
        public Optional<PlayerHandle> player() {
            return MinecraftPlayers.playerOf(source);
        }

        @Override
        public ServerHandle server() {
            return MinecraftPlayers.serverOf(source);
        }

        @Override
        public Vec3 position() {
            return player().map(PlayerHandle::position).orElse(Vec3.ZERO);
        }

        @Override
        public int permissionLevel() {
            return MinecraftPlayers.permissionLevelOf(source);
        }

        @Override
        public void sendFeedback(Text message) {
            MinecraftText.sendSuccess(source, message);
        }

        @Override
        public void sendError(Text message) {
            MinecraftText.sendFailure(source, message);
        }
    }
}
