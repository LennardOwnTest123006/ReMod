package dev.remod.examples.fly;

import dev.remod.api.ReModContext;
import dev.remod.api.ReModMod;
import dev.remod.api.command.ArgumentType;
import dev.remod.api.command.CommandBuilder;
import dev.remod.api.command.CommandContext;
import dev.remod.api.command.CommandException;
import dev.remod.api.config.ConfigSpec;
import dev.remod.api.event.player.PlayerJoinEvent;
import dev.remod.api.event.player.PlayerQuitEvent;
import dev.remod.api.game.PlayerHandle;
import dev.remod.api.game.Text;
import dev.remod.api.game.TextColor;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ReMod Fly Mod -- {@code /fly} in your own world.
 *
 * <p>Two rules shape the whole mod, and both are deliberate:</p>
 *
 * <ul>
 *   <li><b>Only you.</b> There is no {@code /fly &lt;player&gt;}. The command
 *       toggles the flight of whoever ran it and nobody else, so it cannot be
 *       used to lift another player.</li>
 *   <li><b>Only your own world.</b> "Your own world" is the integrated server
 *       that single-player runs. A dedicated server belongs to somebody else,
 *       so {@code /fly} is off there unless its owner enables it in the config
 *       and sets a permission level.</li>
 * </ul>
 *
 * <pre>
 *   /fly                toggle your flight
 *   /fly on             enable it
 *   /fly off            disable it
 *   /fly speed &lt;value&gt;  set your flying speed (vanilla is 0.05)
 *   /fly status         show what the mod thinks your state is
 * </pre>
 *
 * <p>Flight state is remembered for the session and reapplied when you rejoin,
 * so relogging does not silently drop you out of the sky.</p>
 */
public class FlyMod implements ReModMod {

    /** This mod's id, and the namespace for everything it registers. */
    public static final String MOD_ID = "flymod";

    private static final ConfigSpec CONFIG = ConfigSpec.builder()
            .comment("Flying speed used when /fly is switched on.",
                    "Minecraft's own default is 0.05; 0.1 is about twice that.")
            .defineInRange("speed", FlyRules.VANILLA_SPEED,
                    0.0, FlyRules.MAX_SPEED)
            .comment("Re-enable flight automatically when you rejoin a world",
                    "you were flying in.")
            .define("rememberBetweenSessions", true)
            .comment("Allow /fly on dedicated (multiplayer) servers too.",
                    "Off by default: this mod is meant for your own world.")
            .define("allowOnDedicatedServers", false)
            .comment("When the above is on, the permission level a player needs.",
                    "Minecraft's scale: 0 everyone, 2 the usual operator threshold, 4 full.")
            .defineInRange("dedicatedServerPermissionLevel", 2, 0, 4)
            .build();

    /**
     * Who the mod has flight switched on for.
     *
     * <p>Concurrent because join and quit events arrive on the server thread
     * while a command may be running on another.</p>
     */
    private final Map<UUID, Boolean> flightEnabled = new ConcurrentHashMap<>();

    private FlyRules rules;

    @Override
    public void onPreInitialize(ReModContext context) {
        context.config().withSpec(CONFIG);
        rules = new FlyRules(context.config());
    }

    @Override
    public void onInitialize(ReModContext context) {
        context.logger().info("ReMod Fly Mod " + context.modVersion()
                + " ready -- type /fly in your world");
        registerCommand(context);
        registerEvents(context);
    }

    private void registerCommand(ReModContext context) {
        context.commands().register(CommandBuilder.create("fly")
                .description("Toggle flight for yourself in your own world")
                // Level 0: in your own single-player world you are already the
                // owner, and the dedicated-server case is gated separately.
                .permissionLevel(0)
                .subcommand(CommandBuilder.create("on")
                        .description("Enable flight")
                        .executes(command -> set(context, command, true)))
                .subcommand(CommandBuilder.create("off")
                        .description("Disable flight")
                        .executes(command -> set(context, command, false)))
                .subcommand(CommandBuilder.create("toggle")
                        .description("Toggle flight")
                        .executes(command -> toggle(context, command)))
                .subcommand(CommandBuilder.create("speed")
                        .description("Set your flying speed")
                        .argument("value", ArgumentType.DOUBLE)
                        .executes(command -> speed(context, command)))
                .subcommand(CommandBuilder.create("status")
                        .description("Show your current flight state")
                        .executes(command -> status(context, command))));

        // A bare "/fly" has to land somewhere. CommandBuilder requires a
        // command to have either a body or subcommands, not both, so the
        // toggle is registered as its own short alias.
        context.commands().register(CommandBuilder.create("flytoggle")
                .aliases("f")
                .description("Toggle flight for yourself (same as /fly toggle)")
                .permissionLevel(0)
                .executes(command -> toggle(context, command)));
    }

    private void registerEvents(ReModContext context) {
        context.events().subscribe(PlayerJoinEvent.class, event -> {
            if (!context.config().getBoolean("rememberBetweenSessions")) {
                return;
            }
            PlayerHandle player = event.player();
            if (Boolean.TRUE.equals(flightEnabled.get(player.uuid()))
                    && rules.check(Optional.of(player), event.server())
                            == FlyRules.Verdict.ALLOWED) {
                rules.apply(player, true);
                player.sendActionBar(Text.literal("Flight restored.")
                        .color(TextColor.GREEN));
                context.logger().debug(() -> "Restored flight for " + player.name());
            }
        });

        // Forget a player who has left, so the map cannot grow without bound
        // on a long-running server.
        context.events().subscribe(PlayerQuitEvent.class, event -> {
            if (!context.config().getBoolean("rememberBetweenSessions")) {
                flightEnabled.remove(event.player().uuid());
            }
        });
    }

    // --- command bodies ---------------------------------------------------

    private int toggle(ReModContext context, CommandContext command) throws CommandException {
        PlayerHandle player = requireAllowedPlayer(command);
        boolean enabled = !Boolean.TRUE.equals(flightEnabled.get(player.uuid()));
        return applyAndReport(context, command, player, enabled);
    }

    private int set(ReModContext context, CommandContext command, boolean enabled)
            throws CommandException {
        PlayerHandle player = requireAllowedPlayer(command);
        return applyAndReport(context, command, player, enabled);
    }

    private int applyAndReport(ReModContext context, CommandContext command,
                               PlayerHandle player, boolean enabled) {
        rules.apply(player, enabled);
        if (enabled) {
            flightEnabled.put(player.uuid(), Boolean.TRUE);
        } else {
            flightEnabled.remove(player.uuid());
            rules.resetSpeed(player);
        }
        command.source().sendFeedback(enabled
                ? Text.literal("Flight ").append(Text.literal("enabled")
                        .color(TextColor.GREEN)).append(Text.literal(". Double-tap jump."))
                : Text.literal("Flight ").append(Text.literal("disabled")
                        .color(TextColor.YELLOW)).append(Text.literal(".")));
        context.logger().debug(() -> "Flight " + (enabled ? "on" : "off")
                + " for " + player.name());
        return 1;
    }

    private int speed(ReModContext context, CommandContext command) throws CommandException {
        PlayerHandle player = requireAllowedPlayer(command);
        double requested = command.getDouble("value");
        if (requested < 0 || requested > FlyRules.MAX_SPEED) {
            throw new CommandException(Text.literal("Speed must be between 0 and "
                    + FlyRules.MAX_SPEED + ". Minecraft's default is "
                    + FlyRules.VANILLA_SPEED + ".").color(TextColor.RED));
        }
        float applied = FlyRules.clampSpeed(requested);
        player.setFlightSpeed(applied);
        context.config().set("speed", (double) applied).save();
        command.source().sendFeedback(Text.literal("Flying speed set to ")
                .append(Text.literal(String.valueOf(applied)).color(TextColor.AQUA)));
        return 1;
    }

    private int status(ReModContext context, CommandContext command) throws CommandException {
        PlayerHandle player = FlyRules.callerOf(command)
                .orElseThrow(() -> FlyRules.refuse(rules.explain(
                        FlyRules.Verdict.NOT_A_PLAYER, Optional.empty())));
        command.source().sendFeedback(Text.literal("Flight allowed: ")
                .append(Text.literal(String.valueOf(player.isFlightAllowed()))
                        .color(player.isFlightAllowed() ? TextColor.GREEN : TextColor.GRAY)));
        command.source().sendFeedback(Text.literal("Currently flying: "
                + player.isFlying()));
        command.source().sendFeedback(Text.literal("Speed: " + player.flightSpeed()));
        command.source().sendFeedback(Text.literal("Game mode: "
                + player.gameMode().token()));
        return 1;
    }

    /**
     * Resolves the caller and applies the mod's rules, failing the command with
     * a readable reason when they do not pass.
     */
    private PlayerHandle requireAllowedPlayer(CommandContext command) throws CommandException {
        Optional<PlayerHandle> caller = FlyRules.callerOf(command);
        FlyRules.Verdict verdict = rules.check(caller, command.source().server());
        if (verdict != FlyRules.Verdict.ALLOWED) {
            throw FlyRules.refuse(rules.explain(verdict, caller));
        }
        return caller.orElseThrow();
    }

    @Override
    public void onShutdown(ReModContext context) {
        flightEnabled.clear();
        context.logger().info("ReMod Fly Mod stopped");
    }

    /** The players this mod currently has flight switched on for. Used by tests. */
    Map<UUID, Boolean> flightState() {
        return flightEnabled;
    }
}
