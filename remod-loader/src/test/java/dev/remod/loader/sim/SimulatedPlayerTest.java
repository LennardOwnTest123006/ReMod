package dev.remod.loader.sim;

import dev.remod.api.game.GameMode;
import dev.remod.api.game.Text;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The simulated player has real state, which is what makes it a proof and not a mock. */
class SimulatedPlayerTest {

    @Test
    void flightStateIsReal() {
        SimulatedPlayer player = new SimulatedPlayer("Steve", 4, GameMode.SURVIVAL);

        assertFalse(player.isFlightAllowed());
        player.setFlightAllowed(true);
        assertTrue(player.isFlightAllowed());
        player.setFlying(true);
        assertTrue(player.isFlying());
    }

    @Test
    void withdrawingFlightDropsThePlayer() {
        SimulatedPlayer player = new SimulatedPlayer("Steve", 4, GameMode.SURVIVAL);
        player.setFlightAllowed(true);
        player.setFlying(true);

        player.setFlightAllowed(false);

        // Same behaviour as a real game: no fly permission means not flying.
        assertFalse(player.isFlying());
    }

    @Test
    void creativeGrantsFlightRegardlessOfTheToggle() {
        SimulatedPlayer player = new SimulatedPlayer("Steve", 4, GameMode.CREATIVE);

        assertTrue(player.isFlightAllowed(), "creative always allows flight");
    }

    @Test
    void cannotFlyWithoutPermission() {
        SimulatedPlayer player = new SimulatedPlayer("Steve", 4, GameMode.SURVIVAL);

        player.setFlying(true);

        assertFalse(player.isFlying(), "setFlying must be ignored without permission");
    }

    @Test
    void messagesAreDelivered() {
        SimulatedPlayer player = new SimulatedPlayer("Steve", 4, GameMode.SURVIVAL);

        player.sendMessage(Text.literal("Welcome!"));

        assertEquals("Welcome!", player.lastMessage());
    }

    @Test
    void singlePlayerServerIsNotDedicated() {
        SimulatedPlayer owner = SimulatedPlayer.singlePlayerOwner("Steve");
        SimulatedServer server = SimulatedServer.singlePlayer(owner);

        assertFalse(server.isDedicated(), "single-player is 'your own world'");
        assertEquals(1, server.players().size());
        assertTrue(server.player("Steve").isPresent());
    }
}
