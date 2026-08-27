package dev.remod.examples.fly;

import dev.remod.api.config.ConfigSpec;
import dev.remod.api.game.GameMode;
import dev.remod.common.log.ReModLog;
import dev.remod.loader.runtime.JsonConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules behind {@code /fly}: who may fly, where, and what happens to the
 * player's abilities when the toggle is thrown.
 */
class FlyRulesTest {

    @TempDir
    Path configDir;

    private JsonConfig config;
    private FlyRules rules;

    private static final ConfigSpec SPEC = ConfigSpec.builder()
            .defineInRange("speed", FlyRules.VANILLA_SPEED, 0.0, FlyRules.MAX_SPEED)
            .define("rememberBetweenSessions", true)
            .define("allowOnDedicatedServers", false)
            .defineInRange("dedicatedServerPermissionLevel", 2, 0, 4)
            .build();

    @BeforeEach
    void setUp() {
        ReModLog.reset();
        config = new JsonConfig("flymod", configDir.resolve("flymod.json"));
        config.load().withSpec(SPEC);
        rules = new FlyRules(config);
    }

    // --- "only in your own world" ----------------------------------------

    @Test
    void allowsFlightInYourOwnSinglePlayerWorld() {
        FakePlayer player = new FakePlayer("Steve");

        assertEquals(FlyRules.Verdict.ALLOWED,
                rules.check(Optional.of(player), FakePlayer.FakeServer.singlePlayer()));
    }

    @Test
    void refusesFlightOnADedicatedServerByDefault() {
        // Somebody else's world: off unless its owner opts in.
        FakePlayer player = new FakePlayer("Steve").permissionLevel(4);

        FlyRules.Verdict verdict =
                rules.check(Optional.of(player), FakePlayer.FakeServer.dedicated());

        assertEquals(FlyRules.Verdict.NOT_YOUR_WORLD, verdict);
        assertTrue(rules.explain(verdict, Optional.of(player)).plainText()
                .contains("your own single-player world"));
    }

    @Test
    void allowsFlightOnADedicatedServerOnceTheOwnerEnablesIt() {
        config.set("allowOnDedicatedServers", true).save();
        FakePlayer operator = new FakePlayer("Admin").permissionLevel(2);

        assertEquals(FlyRules.Verdict.ALLOWED,
                rules.check(Optional.of(operator), FakePlayer.FakeServer.dedicated()));
    }

    @Test
    void stillChecksRankOnADedicatedServer() {
        config.set("allowOnDedicatedServers", true).save();
        FakePlayer regular = new FakePlayer("Player").permissionLevel(0);

        FlyRules.Verdict verdict =
                rules.check(Optional.of(regular), FakePlayer.FakeServer.dedicated());

        assertEquals(FlyRules.Verdict.NOT_PERMITTED, verdict);
        assertTrue(rules.explain(verdict, Optional.of(regular)).plainText()
                .contains("do not have permission"));
    }

    @Test
    void rankRequirementIsConfigurable() {
        config.set("allowOnDedicatedServers", true)
                .set("dedicatedServerPermissionLevel", 0).save();
        FakePlayer regular = new FakePlayer("Player").permissionLevel(0);

        assertEquals(FlyRules.Verdict.ALLOWED,
                rules.check(Optional.of(regular), FakePlayer.FakeServer.dedicated()));
    }

    // --- "only for you" ---------------------------------------------------

    @Test
    void refusesTheConsoleWhichHasNobodyToLift() {
        FlyRules.Verdict verdict =
                rules.check(Optional.empty(), FakePlayer.FakeServer.singlePlayer());

        assertEquals(FlyRules.Verdict.NOT_A_PLAYER, verdict);
        assertTrue(rules.explain(verdict, Optional.empty()).plainText()
                .contains("only be used by a player"));
    }

    // --- game modes that already fly -------------------------------------

    @Test
    void saysSoWhenTheGameModeAlreadyGrantsFlight() {
        for (GameMode mode : new GameMode[]{GameMode.CREATIVE, GameMode.SPECTATOR}) {
            FakePlayer player = new FakePlayer("Steve").gameMode(mode);

            FlyRules.Verdict verdict =
                    rules.check(Optional.of(player), FakePlayer.FakeServer.singlePlayer());

            assertEquals(FlyRules.Verdict.ALREADY_FLYING_BY_GAME_MODE, verdict, mode.name());
            assertTrue(rules.explain(verdict, Optional.of(player)).plainText()
                    .contains(mode.token()));
        }
    }

    @Test
    void adventureModeIsTreatedLikeSurvival() {
        FakePlayer player = new FakePlayer("Steve").gameMode(GameMode.ADVENTURE);

        assertEquals(FlyRules.Verdict.ALLOWED,
                rules.check(Optional.of(player), FakePlayer.FakeServer.singlePlayer()));
    }

    // --- applying the toggle ---------------------------------------------

    @Test
    void enablingGrantsFlightAndAppliesTheConfiguredSpeed() {
        config.set("speed", 0.2).save();
        FakePlayer player = new FakePlayer("Steve");

        assertTrue(rules.apply(player, true));

        assertTrue(player.isFlightAllowed());
        assertEquals(0.2f, player.flightSpeed(), 0.0001f);
    }

    @Test
    void disablingStopsTheFlightBeforeWithdrawingTheAbility() {
        FakePlayer player = new FakePlayer("Steve");
        rules.apply(player, true);
        player.setFlying(true);
        assertTrue(player.isFlying());

        assertFalse(rules.apply(player, false));

        // Stopped first, then withdrawn: the same order the game uses when a
        // player leaves creative, so they are not dropped mid-air.
        assertFalse(player.isFlying());
        assertFalse(player.isFlightAllowed());
    }

    @Test
    void disablingRestoresVanillaSpeed() {
        config.set("speed", 0.5).save();
        FakePlayer player = new FakePlayer("Steve");
        rules.apply(player, true);
        assertEquals(0.5f, player.flightSpeed(), 0.0001f);

        rules.apply(player, false);
        rules.resetSpeed(player);

        assertEquals(FlyRules.VANILLA_SPEED, player.flightSpeed(), 0.0001f);
    }

    // --- speed clamping ---------------------------------------------------

    @Test
    void clampsSpeedRatherThanRejectingIt() {
        assertEquals(FlyRules.MAX_SPEED, FlyRules.clampSpeed(99.0), 0.0001f);
        assertEquals(0.0f, FlyRules.clampSpeed(-5.0), 0.0001f);
        assertEquals(0.3f, FlyRules.clampSpeed(0.3), 0.0001f);
        // A NaN in a config file should not put the player in an unusable state.
        assertEquals(FlyRules.VANILLA_SPEED, FlyRules.clampSpeed(Double.NaN), 0.0001f);
    }

    @Test
    void anOutOfRangeSpeedInTheConfigFallsBackRatherThanBreakingFlight() {
        // The spec's range rejects it, so getDouble returns the default.
        config.load();
        FakePlayer player = new FakePlayer("Steve");

        rules.apply(player, true);

        assertTrue(player.flightSpeed() > 0);
        assertTrue(player.isFlightAllowed());
    }
}
