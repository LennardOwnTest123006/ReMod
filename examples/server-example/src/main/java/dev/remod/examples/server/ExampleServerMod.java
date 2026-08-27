package dev.remod.examples.server;

import dev.remod.api.ReModContext;
import dev.remod.api.ReModMod;
import dev.remod.api.command.ArgumentType;
import dev.remod.api.command.CommandBuilder;
import dev.remod.api.command.CommandException;
import dev.remod.api.config.ConfigSpec;
import dev.remod.api.event.EventPriority;
import dev.remod.api.event.player.PlayerChatEvent;
import dev.remod.api.event.player.PlayerJoinEvent;
import dev.remod.api.event.player.PlayerQuitEvent;
import dev.remod.api.event.server.ServerStartedEvent;
import dev.remod.api.event.server.ServerStoppingEvent;
import dev.remod.api.event.tick.ServerTickEvent;
import dev.remod.api.event.tick.TickPhase;
import dev.remod.api.game.PlayerHandle;
import dev.remod.api.game.Text;
import dev.remod.api.game.TextColor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ReMod Example Server Mod -- server events, a command and player tracking.
 *
 * <p>Declared {@code "side": "server"}, so ReMod will not load it on a client.
 * It demonstrates the things a server mod actually needs: reacting to the
 * server lifecycle, tracking players across join and quit, a permission-gated
 * command, filtering chat, and persisting state to its own data directory
 * rather than anywhere in the world save.</p>
 */
public class ExampleServerMod implements ReModMod {

    public static final String MOD_ID = "remodexampleserver";

    private static final ConfigSpec CONFIG = ConfigSpec.builder()
            .comment("Announce joins and quits in chat.")
            .define("announceJoins", true)
            .comment("Words filtered out of chat. Leave empty to disable filtering.")
            .defineList("blockedWords", java.util.List.of())
            .comment("How often, in ticks, to save play-time statistics. 1200 = one minute.")
            .defineInRange("saveIntervalTicks", 1200, 200, 72000)
            .build();

    /** Join timestamps, keyed by player. Concurrent because packets arrive off-thread. */
    private final Map<UUID, Long> joinedAt = new ConcurrentHashMap<>();
    private final Map<String, Long> playTimeSeconds = new ConcurrentHashMap<>();
    private Path statsFile;

    @Override
    public void onPreInitialize(ReModContext context) {
        context.config().withSpec(CONFIG);
    }

    @Override
    public void onInitialize(ReModContext context) {
        context.logger().info("ReMod Example Server Mod starting");
        registerCommands(context);
    }

    /** SERVER_INIT never runs on a client, so server-only state is set up here. */
    @Override
    public void onServerInitialize(ReModContext context) {
        statsFile = context.dataDirectory().resolve("playtime.txt");
        loadStats(context);
        registerEvents(context);
        context.logger().info("Tracking play time for " + playTimeSeconds.size()
                + " known player(s)");
    }

    private void registerEvents(ReModContext context) {
        context.events().subscribe(ServerStartedEvent.class, event -> {
            context.logger().info("Server started with "
                    + event.server().players().size() + " player(s) online");
            event.server().broadcast(Text.literal("ReMod is running on this server.")
                    .color(TextColor.GRAY));
        });

        context.events().subscribe(ServerStoppingEvent.class, event -> {
            context.logger().info("Server stopping; saving statistics");
            saveStats(context);
        });

        context.events().subscribe(PlayerJoinEvent.class, event -> {
            PlayerHandle player = event.player();
            joinedAt.put(player.uuid(), System.currentTimeMillis());
            playTimeSeconds.putIfAbsent(player.name(), 0L);

            context.logger().info(player.name() + " joined"
                    + (event.isFirstJoin() ? " for the first time" : ""));
            if (context.config().getBoolean("announceJoins")) {
                Text message = event.isFirstJoin()
                        ? Text.literal("Welcome " + player.name() + " to the server!")
                                .color(TextColor.GOLD)
                        : Text.literal("Welcome back, " + player.name() + ".")
                                .color(TextColor.GREEN);
                event.server().broadcast(message);
            }
        });

        context.events().subscribe(PlayerQuitEvent.class, event -> {
            Long joined = joinedAt.remove(event.player().uuid());
            if (joined != null) {
                long seconds = (System.currentTimeMillis() - joined) / 1000;
                playTimeSeconds.merge(event.player().name(), seconds, Long::sum);
            }
            context.logger().info(event.player().name() + " left");
        });

        // HIGHEST priority: filter before other mods format or log the message.
        context.events().subscribe(PlayerChatEvent.class, EventPriority.HIGHEST, event -> {
            for (String blocked : context.config().getStringList("blockedWords")) {
                if (!blocked.isBlank()
                        && event.message().toLowerCase(java.util.Locale.ROOT)
                                .contains(blocked.toLowerCase(java.util.Locale.ROOT))) {
                    event.cancel();
                    event.player().sendMessage(
                            Text.literal("Your message was not sent.").color(TextColor.RED));
                    context.logger().info("Filtered a message from " + event.player().name());
                    return;
                }
            }
        });

        // Periodic saving, throttled with every() so it costs nothing per tick.
        int interval = context.config().getInt("saveIntervalTicks");
        context.events().subscribe(ServerTickEvent.class, event -> {
            if (event.phase() == TickPhase.END && event.every(interval)) {
                saveStats(context);
            }
        });
    }

    private void registerCommands(ReModContext context) {
        context.commands().register(CommandBuilder.create("playtime")
                .description("Show how long a player has been on this server")
                .permissionLevel(0)
                .optionalArgument("player", ArgumentType.PLAYER, null)
                .executes(command -> {
                    String name = command.has("player")
                            ? command.getString("player")
                            : command.source().player()
                                    .map(PlayerHandle::name)
                                    .orElseThrow(() -> new CommandException(
                                            "Run this from a player, or name one:"
                                                    + " /playtime <player>"));
                    long seconds = playTimeSeconds.getOrDefault(name, 0L);
                    // Add the current session for a player who is still online,
                    // whose time has not been folded in by PlayerQuitEvent yet.
                    Long joined = command.source().server().player(name)
                            .map(online -> joinedAt.get(online.uuid()))
                            .orElse(null);
                    if (joined != null) {
                        seconds += (System.currentTimeMillis() - joined) / 1000;
                    }
                    command.source().sendFeedback(Text.literal(name + " has played for ")
                            .append(Text.literal(format(seconds)).color(TextColor.AQUA)));
                    return 1;
                }));
    }

    private static String format(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes > 0 ? minutes + "m" : seconds + "s";
    }

    /** State lives in the mod's own data directory, never inside a world save. */
    private void loadStats(ReModContext context) {
        if (statsFile == null || !Files.isRegularFile(statsFile)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(statsFile, StandardCharsets.UTF_8)) {
                int separator = line.lastIndexOf('=');
                if (separator > 0) {
                    try {
                        playTimeSeconds.put(line.substring(0, separator),
                                Long.parseLong(line.substring(separator + 1).trim()));
                    } catch (NumberFormatException e) {
                        context.logger().warn("Ignoring malformed statistics line: " + line);
                    }
                }
            }
        } catch (IOException e) {
            context.logger().warn("Could not read " + statsFile + ": " + e.getMessage());
        }
    }

    private void saveStats(ReModContext context) {
        if (statsFile == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        playTimeSeconds.forEach((name, seconds) -> sb.append(name).append('=')
                .append(seconds).append(System.lineSeparator()));
        try {
            Files.createDirectories(statsFile.getParent());
            dev.remod.common.io.IOUtil.writeAtomically(statsFile, sb.toString());
        } catch (IOException e) {
            context.logger().warn("Could not save " + statsFile + ": " + e.getMessage());
        }
    }

    @Override
    public void onShutdown(ReModContext context) {
        saveStats(context);
        context.logger().info("ReMod Example Server Mod stopped");
    }
}
