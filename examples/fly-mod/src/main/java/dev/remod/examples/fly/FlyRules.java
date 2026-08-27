package dev.remod.examples.fly;

import dev.remod.api.ReModContext;
import dev.remod.api.command.CommandContext;
import dev.remod.api.command.CommandException;
import dev.remod.api.config.Config;
import dev.remod.api.game.GameMode;
import dev.remod.api.game.PlayerHandle;
import dev.remod.api.game.ServerHandle;
import dev.remod.api.game.Text;
import dev.remod.api.game.TextColor;

import java.util.Optional;

/**
 * The decisions behind {@code /fly}, kept apart from the command wiring.
 *
 * <p>Separated so the rules -- who may fly, where, and what happens to the
 * player's abilities -- are testable without a command dispatcher or a running
 * game. Everything here is a pure function of the caller, the server and the
 * configuration.</p>
 */
final class FlyRules {

    /** Why a {@code /fly} attempt was refused, or that it was allowed. */
    enum Verdict {
        ALLOWED,
        /** Ran from the server console, which has no player to lift. */
        NOT_A_PLAYER,
        /** On a dedicated server, and the config has not opened it up. */
        NOT_YOUR_WORLD,
        /** On a dedicated server with it enabled, but the caller lacks the rank. */
        NOT_PERMITTED,
        /** The game mode already grants flight, so the toggle would do nothing. */
        ALREADY_FLYING_BY_GAME_MODE
    }

    private final Config config;

    FlyRules(Config config) {
        this.config = config;
    }

    /**
     * Decides whether {@code source} may toggle their own flight.
     *
     * <p>The default is deliberately narrow: your own world, yourself only.
     * "Your own world" means the integrated server that single-player runs --
     * a dedicated server is somebody else's, so flight there is off unless the
     * owner turns it on and sets a permission level.</p>
     */
    Verdict check(Optional<PlayerHandle> caller, ServerHandle server) {
        if (caller.isEmpty()) {
            return Verdict.NOT_A_PLAYER;
        }
        PlayerHandle player = caller.get();
        if (server.isDedicated()) {
            if (!config.getBoolean("allowOnDedicatedServers")) {
                return Verdict.NOT_YOUR_WORLD;
            }
            if (player.permissionLevel() < config.getInt("dedicatedServerPermissionLevel")) {
                return Verdict.NOT_PERMITTED;
            }
        }
        if (player.gameMode().grantsFlight()) {
            return Verdict.ALREADY_FLYING_BY_GAME_MODE;
        }
        return Verdict.ALLOWED;
    }

    /** The message shown when a verdict other than {@link Verdict#ALLOWED} is reached. */
    Text explain(Verdict verdict, Optional<PlayerHandle> caller) {
        switch (verdict) {
            case NOT_A_PLAYER:
                return Text.literal("/fly can only be used by a player -- there is nobody"
                        + " for the console to lift.").color(TextColor.RED);
            case NOT_YOUR_WORLD:
                return Text.literal("/fly only works in your own single-player world."
                        + " The owner of this server can enable it in the flymod config.")
                        .color(TextColor.RED);
            case NOT_PERMITTED:
                return Text.literal("You do not have permission to use /fly on this server.")
                        .color(TextColor.RED);
            case ALREADY_FLYING_BY_GAME_MODE:
                return Text.literal("You can already fly in "
                        + caller.map(p -> p.gameMode().token()).orElse("this game mode")
                        + " mode, so /fly has nothing to change.").color(TextColor.YELLOW);
            default:
                return Text.literal("Flight enabled.").color(TextColor.GREEN);
        }
    }

    /**
     * Applies a flight state to a player.
     *
     * <p>Turning flight off while airborne would drop the player, so this stops
     * the flying first and then withdraws the ability -- the same order the
     * game uses when a player leaves creative.</p>
     *
     * @return the state actually applied
     */
    boolean apply(PlayerHandle player, boolean enabled) {
        if (enabled) {
            player.setFlightSpeed(clampSpeed(config.getDouble("speed")));
            player.setFlightAllowed(true);
            return true;
        }
        if (player.isFlying()) {
            player.setFlying(false);
        }
        player.setFlightAllowed(false);
        return false;
    }

    /**
     * Restores vanilla flight speed.
     *
     * <p>Called when flight is switched off, so a mod-set speed does not linger
     * on a player who later gains flight from creative mode.</p>
     */
    void resetSpeed(PlayerHandle player) {
        player.setFlightSpeed(VANILLA_SPEED);
    }

    /** Minecraft's own default flying speed. */
    static final float VANILLA_SPEED = 0.05f;

    /** The widest speed the mod will set, well short of anything unplayable. */
    static final float MAX_SPEED = 1.0f;

    /**
     * Brings a speed into range.
     *
     * <p>Clamps rather than rejects: a number out of range in a config file is
     * the user's typo, and dropping them into an unusable session over it would
     * be the wrong trade.</p>
     */
    static float clampSpeed(double requested) {
        if (Double.isNaN(requested)) {
            return VANILLA_SPEED;
        }
        return (float) Math.max(0.0, Math.min(MAX_SPEED, requested));
    }

    /** The caller of a command, when it was a player. */
    static Optional<PlayerHandle> callerOf(CommandContext command) {
        return command.source().player();
    }

    /** Fails the command with a message the caller sees in red. */
    static CommandException refuse(Text message) {
        return new CommandException(message);
    }
}
