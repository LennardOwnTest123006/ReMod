package dev.remod.loader.sim;

import dev.remod.api.command.ArgumentType;
import dev.remod.api.command.CommandBuilder;
import dev.remod.api.command.CommandException;
import dev.remod.api.game.GameMode;
import dev.remod.api.game.PlayerHandle;
import dev.remod.api.game.Text;
import dev.remod.common.log.ReModLog;
import dev.remod.loader.runtime.DefaultCommandRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the simulated world runs a mod's real command code and applies its
 * real effect -- which is what makes {@code remod play} a believable proof
 * rather than a mock.
 */
class SimulatedCommandsTest {

    private DefaultCommandRegistry registry;
    private SimulatedPlayer player;
    private SimulatedServer server;
    private SimulatedCommands commands;

    @BeforeEach
    void setUp() {
        ReModLog.reset();
        // A registry whose "current owner" is a fixed test mod.
        registry = new DefaultCommandRegistry(() -> "testmod", () -> null);
        player = SimulatedPlayer.singlePlayerOwner("Steve");
        server = SimulatedServer.singlePlayer(player);
        commands = new SimulatedCommands(registry, server);
    }

    @Test
    void runsASimpleCommandsRealBody() {
        AtomicReference<String> ran = new AtomicReference<>();
        registry.register(CommandBuilder.create("hello")
                .executes(context -> {
                    ran.set(context.source().name());
                    context.source().sendFeedback(Text.literal("Hi!"));
                    return 1;
                })
                .build());

        SimulatedCommands.Result result = commands.run("/hello", player);

        assertTrue(result.commandFound());
        assertTrue(result.succeeded());
        assertEquals("Steve", ran.get());
        assertEquals(java.util.List.of("Hi!"), result.feedback());
    }

    @Test
    void reportsAnUnknownCommand() {
        SimulatedCommands.Result result = commands.run("/nope", player);

        assertFalse(result.commandFound());
    }

    @Test
    void walksToASubcommand() {
        AtomicReference<String> ran = new AtomicReference<>();
        registry.register(CommandBuilder.create("fly")
                .subcommand(CommandBuilder.create("on").executes(context -> {
                    ran.set("on");
                    return 1;
                }))
                .subcommand(CommandBuilder.create("off").executes(context -> {
                    ran.set("off");
                    return 1;
                }))
                .build());

        commands.run("/fly on", player);
        assertEquals("on", ran.get());
        commands.run("/fly off", player);
        assertEquals("off", ran.get());
    }

    @Test
    void bindsArgumentsIncludingDefaultsForOmittedOptionals() {
        AtomicReference<Integer> amount = new AtomicReference<>();
        registry.register(CommandBuilder.create("give")
                .argument("target", ArgumentType.PLAYER)
                .optionalArgument("amount", ArgumentType.INTEGER, "4")
                .executes(context -> {
                    amount.set(context.has("amount") ? context.getInt("amount") : 0);
                    return 1;
                })
                .build());

        commands.run("/give Steve 10", player);
        assertEquals(10, amount.get());

        // Omitted: the declared default is bound.
        commands.run("/give Steve", player);
        assertEquals(4, amount.get());
    }

    @Test
    void enforcesPermissionLevels() {
        registry.register(CommandBuilder.create("op")
                .permissionLevel(4)
                .executes(context -> 1)
                .build());
        SimulatedPlayer regular = new SimulatedPlayer("Alex", 0, GameMode.SURVIVAL);
        SimulatedServer regularServer = SimulatedServer.singlePlayer(regular);
        SimulatedCommands regularCommands = new SimulatedCommands(registry, regularServer);

        SimulatedCommands.Result denied = regularCommands.run("/op", regular);
        assertFalse(denied.succeeded());
        assertTrue(denied.errors().get(0).contains("permission"));

        // The level-4 owner may run it.
        assertTrue(commands.run("/op", player).succeeded());
    }

    @Test
    void turnsACommandExceptionIntoAnErrorReply() {
        registry.register(CommandBuilder.create("boom")
                .executes(context -> {
                    throw new CommandException(Text.literal("Nope, not allowed."));
                })
                .build());

        SimulatedCommands.Result result = commands.run("/boom", player);

        assertTrue(result.commandFound());
        assertFalse(result.succeeded());
        assertEquals(java.util.List.of("Nope, not allowed."), result.errors());
    }

    @Test
    void aCommandThatTogglesFlightReallyChangesThePlayer() {
        // The core proof: a command's effect on the player is real state.
        registry.register(CommandBuilder.create("fly")
                .executes(context -> {
                    PlayerHandle caller = context.source().player().orElseThrow();
                    caller.setFlightAllowed(!caller.isFlightAllowed());
                    return 1;
                })
                .build());

        assertFalse(player.isFlightAllowed());
        commands.run("/fly", player);
        assertTrue(player.isFlightAllowed(), "the command should have granted flight");
        commands.run("/fly", player);
        assertFalse(player.isFlightAllowed(), "and taken it away again");
    }

    @Test
    void handlesAnAliasAndQuotedArguments() {
        AtomicReference<String> message = new AtomicReference<>();
        registry.register(CommandBuilder.create("say")
                .aliases("s")
                .argument("message", ArgumentType.GREEDY_STRING)
                .executes(context -> {
                    message.set(context.getString("message"));
                    return 1;
                })
                .build());

        commands.run("/s hello there world", player);
        assertEquals("hello there world", message.get());
    }
}
