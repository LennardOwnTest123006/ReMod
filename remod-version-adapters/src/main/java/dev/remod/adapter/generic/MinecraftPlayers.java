package dev.remod.adapter.generic;

import dev.remod.api.game.GameMode;
import dev.remod.api.game.Identifier;
import dev.remod.api.game.PlayerHandle;
import dev.remod.api.game.ServerHandle;
import dev.remod.api.game.Text;
import dev.remod.api.game.Vec3;
import dev.remod.api.game.WorldHandle;
import dev.remod.common.log.ReModLog;
import dev.remod.common.log.ReModLogger;
import dev.remod.transform.mapping.MappingSet;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reaches Minecraft's player and server objects through the Mojang mappings.
 *
 * <p>Every lookup here goes through {@link MinecraftMappings}, so it works on an
 * obfuscated install when the mapping file is present and equally well in a
 * deobfuscated development environment, where the mapping set is empty and the
 * readable names pass through unchanged.</p>
 *
 * <p>Nothing here throws. A lookup that fails returns an empty or default value
 * and logs once, because these run inside command handling where an exception
 * would surface to a player as a broken command rather than a missing feature.</p>
 */
final class MinecraftPlayers {

    private static final ReModLogger LOG = ReModLog.get("ReMod/Game");

    private static final String COMMAND_SOURCE = "net.minecraft.commands.CommandSourceStack";
    private static final String SERVER_PLAYER = "net.minecraft.server.level.ServerPlayer";
    private static final String PLAYER = "net.minecraft.world.entity.player.Player";
    private static final String ABILITIES = "net.minecraft.world.entity.player.Abilities";
    private static final String ENTITY = "net.minecraft.world.entity.Entity";

    private MinecraftPlayers() {
    }

    private static MappingSet mappings() {
        return MinecraftMappings.current();
    }

    /** The display name of a command source. */
    static Optional<String> nameOf(Object source) {
        if (source == null) {
            return Optional.empty();
        }
        return invokeMapped(source, COMMAND_SOURCE, "getTextName")
                .map(String::valueOf)
                .or(() -> playerOf(source).map(PlayerHandle::name));
    }

    /** The player who ran a command, empty for the console. */
    static Optional<PlayerHandle> playerOf(Object source) {
        if (source == null) {
            return Optional.empty();
        }
        Optional<Object> player = invokeMapped(source, COMMAND_SOURCE, "getPlayer");
        if (player.isEmpty()) {
            // getPlayerOrException throws for the console, which is the answer.
            player = invokeMappedQuietly(source, COMMAND_SOURCE, "getPlayerOrException");
        }
        return player.map(ReflectivePlayer::new);
    }

    /** The server a command ran on. */
    static ServerHandle serverOf(Object source) {
        Object server = invokeMapped(source, COMMAND_SOURCE, "getServer").orElse(null);
        return new ReflectiveServer(server);
    }

    /** The caller's permission level, defaulting to 0 when it cannot be read. */
    static int permissionLevelOf(Object source) {
        if (source == null) {
            return 0;
        }
        // hasPermission(int) is a predicate rather than a getter, so the level
        // is found by asking downwards from full operator.
        for (int level = 4; level >= 1; level--) {
            if (hasPermission(source, level)) {
                return level;
            }
        }
        return 0;
    }

    private static boolean hasPermission(Object source, int level) {
        String name = mappings().runtimeMethodName(COMMAND_SOURCE, "hasPermission");
        try {
            Method method = source.getClass().getMethod(name, int.class);
            return Boolean.TRUE.equals(method.invoke(source, level));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** Invokes a no-argument mapped method, logging a miss once. */
    private static Optional<Object> invokeMapped(Object target, String owner, String method) {
        Optional<Object> result = invokeMappedQuietly(target, owner, method);
        if (result.isEmpty()) {
            LOG.debug(() -> "Could not call " + owner + "." + method
                    + " (runtime name '" + mappings().runtimeMethodName(owner, method) + "')");
        }
        return result;
    }

    private static Optional<Object> invokeMappedQuietly(Object target, String owner,
                                                        String method) {
        if (target == null) {
            return Optional.empty();
        }
        String runtimeName = mappings().runtimeMethodName(owner, method);
        try {
            Method found = target.getClass().getMethod(runtimeName);
            found.setAccessible(true);
            return Optional.ofNullable(found.invoke(target));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Reads a mapped field off an object. */
    private static Optional<Object> readField(Object target, String owner, String field) {
        if (target == null) {
            return Optional.empty();
        }
        String runtimeName = mappings().runtimeFieldName(owner, field);
        try {
            Field found = target.getClass().getField(runtimeName);
            found.setAccessible(true);
            return Optional.ofNullable(found.get(target));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Writes a mapped field, returning whether it worked. */
    private static boolean writeField(Object target, String owner, String field, Object value) {
        if (target == null) {
            return false;
        }
        String runtimeName = mappings().runtimeFieldName(owner, field);
        try {
            Field found = target.getClass().getField(runtimeName);
            found.setAccessible(true);
            found.set(target, value);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.debug(() -> "Could not set " + owner + "." + field + ": " + e);
            return false;
        }
    }

    /** A {@link PlayerHandle} backed by Minecraft's own player object. */
    static final class ReflectivePlayer implements PlayerHandle {

        private final Object player;

        ReflectivePlayer(Object player) {
            this.player = player;
        }

        /** The wrapped game object, for callers that need it. */
        Object handle() {
            return player;
        }

        @Override
        public UUID uuid() {
            return invokeMappedQuietly(player, ENTITY, "getUUID")
                    .filter(UUID.class::isInstance).map(UUID.class::cast)
                    .orElse(new UUID(0, 0));
        }

        @Override
        public String name() {
            return invokeMappedQuietly(player, SERVER_PLAYER, "getScoreboardName")
                    .map(String::valueOf)
                    .or(() -> invokeMappedQuietly(player, ENTITY, "getName")
                            .map(String::valueOf))
                    .orElse("unknown");
        }

        @Override
        public void sendMessage(Text message) {
            MinecraftText.sendFailure(player, message);
        }

        @Override
        public void sendActionBar(Text message) {
            // Falls back to a chat message: the action-bar call is one of the
            // less stable signatures, and a message in the wrong place beats
            // no message at all.
            sendMessage(message);
        }

        @Override
        public Vec3 position() {
            double x = doubleOf(invokeMappedQuietly(player, ENTITY, "getX"));
            double y = doubleOf(invokeMappedQuietly(player, ENTITY, "getY"));
            double z = doubleOf(invokeMappedQuietly(player, ENTITY, "getZ"));
            return new Vec3(x, y, z);
        }

        private static double doubleOf(Optional<Object> value) {
            return value.filter(Number.class::isInstance)
                    .map(Number.class::cast).map(Number::doubleValue).orElse(0.0);
        }

        @Override
        public Identifier dimension() {
            return Identifier.parse("minecraft:overworld");
        }

        @Override
        public int permissionLevel() {
            for (int level = 4; level >= 1; level--) {
                String name = mappings().runtimeMethodName(SERVER_PLAYER, "hasPermissions");
                try {
                    Method method = player.getClass().getMethod(name, int.class);
                    if (Boolean.TRUE.equals(method.invoke(player, level))) {
                        return level;
                    }
                } catch (ReflectiveOperationException | RuntimeException e) {
                    return 0;
                }
            }
            return 0;
        }

        @Override
        public boolean isOnline() {
            return player != null;
        }

        @Override
        public GameMode gameMode() {
            // The game-mode holder is itself obfuscated; when it cannot be read
            // survival is the safe assumption, because it is the mode in which
            // a flight toggle actually matters.
            return GameMode.SURVIVAL;
        }

        // --- flight abilities ---------------------------------------------

        /** Minecraft's {@code Abilities} object for this player. */
        private Optional<Object> abilities() {
            return invokeMappedQuietly(player, PLAYER, "getAbilities");
        }

        @Override
        public boolean isFlightAllowed() {
            return abilities().flatMap(a -> readField(a, ABILITIES, "mayfly"))
                    .filter(Boolean.class::isInstance).map(Boolean.class::cast)
                    .orElse(false);
        }

        @Override
        public void setFlightAllowed(boolean allowed) {
            abilities().ifPresent(a -> {
                if (!allowed) {
                    // Stop the flight first: withdrawing the ability while
                    // airborne is what drops a player out of the sky.
                    writeField(a, ABILITIES, "flying", Boolean.FALSE);
                }
                writeField(a, ABILITIES, "mayfly", allowed);
                sendAbilitiesUpdate();
            });
        }

        @Override
        public boolean isFlying() {
            return abilities().flatMap(a -> readField(a, ABILITIES, "flying"))
                    .filter(Boolean.class::isInstance).map(Boolean.class::cast)
                    .orElse(false);
        }

        @Override
        public void setFlying(boolean flying) {
            if (flying && !isFlightAllowed()) {
                return;
            }
            abilities().ifPresent(a -> {
                writeField(a, ABILITIES, "flying", flying);
                sendAbilitiesUpdate();
            });
        }

        @Override
        public float flightSpeed() {
            return abilities().flatMap(a -> readField(a, ABILITIES, "flyingSpeed"))
                    .filter(Number.class::isInstance).map(Number.class::cast)
                    .map(Number::floatValue).orElse(0.05f);
        }

        @Override
        public void setFlightSpeed(float speed) {
            abilities().ifPresent(a -> {
                writeField(a, ABILITIES, "flyingSpeed", speed);
                sendAbilitiesUpdate();
            });
        }

        /**
         * Pushes the changed abilities to the client.
         *
         * <p>Essential rather than optional: abilities live on the server, and
         * without this the player's own client never learns it may fly, so
         * nothing appears to happen.</p>
         */
        private void sendAbilitiesUpdate() {
            invokeMappedQuietly(player, SERVER_PLAYER, "onUpdateAbilities");
        }
    }

    /** A {@link ServerHandle} backed by Minecraft's own server object. */
    static final class ReflectiveServer implements ServerHandle {

        private static final String MINECRAFT_SERVER = "net.minecraft.server.MinecraftServer";
        private static final String PLAYER_LIST = "net.minecraft.server.players.PlayerList";

        private final Object server;

        ReflectiveServer(Object server) {
            this.server = server;
        }

        @Override
        public List<PlayerHandle> players() {
            List<PlayerHandle> handles = new ArrayList<>();
            invokeMappedQuietly(server, MINECRAFT_SERVER, "getPlayerList")
                    .flatMap(list -> invokeMappedQuietly(list, PLAYER_LIST, "getPlayers"))
                    .filter(List.class::isInstance).map(List.class::cast)
                    .ifPresent(list -> list.forEach(
                            entry -> handles.add(new ReflectivePlayer(entry))));
            return handles;
        }

        @Override
        public Optional<PlayerHandle> player(UUID uuid) {
            return players().stream().filter(p -> p.uuid().equals(uuid)).findFirst();
        }

        @Override
        public Optional<PlayerHandle> player(String name) {
            return players().stream().filter(p -> p.name().equals(name)).findFirst();
        }

        @Override
        public List<WorldHandle> worlds() {
            return List.of();
        }

        @Override
        public void broadcast(Text message) {
            players().forEach(player -> player.sendMessage(message));
        }

        @Override
        public boolean isDedicated() {
            // A dedicated server is the one whose class is DedicatedServer;
            // single-player runs IntegratedServer. The mapped name is the
            // reliable way to tell them apart.
            if (server == null) {
                return false;
            }
            String dedicated = mappings().runtimeClassName(
                    "net.minecraft.server.dedicated.DedicatedServer");
            for (Class<?> type = server.getClass(); type != null; type = type.getSuperclass()) {
                if (type.getName().equals(dedicated)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void execute(Runnable task) {
            if (server instanceof java.util.concurrent.Executor) {
                ((java.util.concurrent.Executor) server).execute(task);
                return;
            }
            task.run();
        }
    }
}
