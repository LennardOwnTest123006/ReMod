package dev.remod.transform.hook;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.remod.api.command.ArgumentType;
import dev.remod.api.command.CommandBuilder;
import dev.remod.api.command.CommandSpec;
import dev.remod.common.log.ReModLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the Brigadier bridge against stub classes carrying the real
 * Brigadier signatures.
 *
 * <p>Brigadier is published on Mojang's own repository rather than Maven
 * Central, so the genuine library is not available to this build. The stubs in
 * {@code src/test/java/com/mojang/brigadier} mirror the signatures the bridge
 * reflects against -- including the two {@code then} overloads, which is where
 * naive reflection goes wrong.</p>
 *
 * <p>This proves the bridge's reflection resolves and its command tree is built
 * correctly. It does not prove Brigadier's real classes are identical to the
 * stubs; that is noted in the release notes rather than assumed here.</p>
 */
class BrigadierCommandBridgeTest {

    /** Stands in for Minecraft's command source, with a permission check. */
    public static final class FakeSource {

        private final int permissionLevel;

        FakeSource(int permissionLevel) {
            this.permissionLevel = permissionLevel;
        }

        /** Shaped like Minecraft's obfuscated {@code hasPermission(int)}. */
        public boolean hasPermission(int level) {
            return permissionLevel >= level;
        }
    }

    private BrigadierCommandBridge bridge;
    private CommandDispatcher<FakeSource> dispatcher;
    private List<String> invoked;

    @BeforeEach
    void setUp() {
        ReModLog.reset();
        bridge = new BrigadierCommandBridge(getClass().getClassLoader());
        dispatcher = new CommandDispatcher<>();
        invoked = new ArrayList<>();
    }

    private BrigadierCommandBridge.CommandInvoker recording() {
        return (command, context) -> {
            invoked.add(command.name());
            return 1;
        };
    }

    @Test
    void reportsBrigadierAsAvailableWhenItIsOnTheClasspath() {
        assertTrue(bridge.isAvailable());
    }

    @Test
    void registersASimpleCommand() {
        CommandSpec fly = CommandBuilder.create("fly")
                .description("Toggle flight")
                .executes(command -> 1)
                .build();

        assertTrue(bridge.register(dispatcher, fly, recording()));

        LiteralArgumentBuilder<FakeSource> node = dispatcher.find("fly");
        assertNotNull(node, "the command should be registered under its own literal");
        assertNotNull(node.getCommand(), "a command with a body must be executable");
    }

    @Test
    void theRegisteredCommandActuallyRunsTheModsCode() {
        CommandSpec fly = CommandBuilder.create("fly").executes(command -> 1).build();
        bridge.register(dispatcher, fly, recording());

        LiteralArgumentBuilder<FakeSource> node = dispatcher.find("fly");
        int result = node.getCommand().run(new CommandContext<>(new FakeSource(4)));

        assertEquals(1, result);
        assertEquals(List.of("fly"), invoked);
    }

    @Test
    void registersSubcommandsAsChildLiterals() {
        CommandSpec fly = CommandBuilder.create("fly")
                .subcommand(CommandBuilder.create("on").executes(command -> 1))
                .subcommand(CommandBuilder.create("off").executes(command -> 1))
                .build();

        bridge.register(dispatcher, fly, recording());

        LiteralArgumentBuilder<FakeSource> node = dispatcher.find("fly");
        List<String> children = new ArrayList<>();
        node.getChildren().forEach(child -> children.add(child.nodeName()));
        assertEquals(List.of("on", "off"), children);
    }

    @Test
    void subcommandBodiesAreReachable() {
        CommandSpec fly = CommandBuilder.create("fly")
                .subcommand(CommandBuilder.create("on").executes(command -> 7))
                .build();
        bridge.register(dispatcher, fly, (command, context) -> {
            invoked.add(command.name());
            return 7;
        });

        ArgumentBuilder<FakeSource, ?> on = dispatcher.find("fly").getChildren().get(0);
        assertEquals(7, on.getCommand().run(new CommandContext<>(new FakeSource(4))));
        assertEquals(List.of("on"), invoked);
    }

    @Test
    void chainsArgumentsAndMapsTheirTypes() {
        CommandSpec speed = CommandBuilder.create("speed")
                .argument("value", ArgumentType.DOUBLE)
                .executes(command -> 1)
                .build();

        bridge.register(dispatcher, speed, recording());

        ArgumentBuilder<FakeSource, ?> argument =
                dispatcher.find("speed").getChildren().get(0);
        assertEquals("value", argument.nodeName());
        assertEquals("double",
                ((RequiredArgumentBuilder<FakeSource, ?>) argument).getType().kind());
        // The body hangs off the argument, not the literal: /speed alone must
        // not run a command that needs a value.
        assertNotNull(argument.getCommand());
    }

    @Test
    void mapsEveryArgumentTypeItClaimsToSupport() {
        record Case(ArgumentType type, String expected) { }
        List<Case> cases = List.of(
                new Case(ArgumentType.STRING, "string"),
                new Case(ArgumentType.GREEDY_STRING, "greedy"),
                new Case(ArgumentType.INTEGER, "integer"),
                new Case(ArgumentType.DOUBLE, "double"),
                new Case(ArgumentType.BOOLEAN, "boolean"),
                // Minecraft's own player argument is obfuscated, so a word is
                // parsed and resolved by name at execution time.
                new Case(ArgumentType.PLAYER, "word"));

        for (Case testCase : cases) {
            CommandDispatcher<FakeSource> fresh = new CommandDispatcher<>();
            CommandSpec command = CommandBuilder.create("test")
                    .argument("value", testCase.type())
                    .executes(context -> 1)
                    .build();

            assertTrue(bridge.register(fresh, command, recording()), testCase.type().name());

            RequiredArgumentBuilder<FakeSource, ?> argument =
                    (RequiredArgumentBuilder<FakeSource, ?>)
                            fresh.find("test").getChildren().get(0);
            assertEquals(testCase.expected(), argument.getType().kind(),
                    testCase.type().name());
        }
    }

    @Test
    void anOptionalArgumentLeavesTheNodeBeforeItExecutable() {
        CommandSpec give = CommandBuilder.create("give")
                .argument("target", ArgumentType.PLAYER)
                .optionalArgument("amount", ArgumentType.INTEGER, "1")
                .executes(command -> 1)
                .build();

        bridge.register(dispatcher, give, recording());

        ArgumentBuilder<FakeSource, ?> target = dispatcher.find("give").getChildren().get(0);
        assertEquals("target", target.nodeName());
        // "/give <target>" must run with the amount omitted...
        assertNotNull(target.getCommand(), "the node before an optional argument must run");
        ArgumentBuilder<FakeSource, ?> amount = target.getChildren().get(0);
        // ...and "/give <target> <amount>" must run too.
        assertNotNull(amount.getCommand());
    }

    @Test
    void appliesAPermissionRequirementThatConsultsTheSource() {
        CommandSpec op = CommandBuilder.create("opcommand")
                .permissionLevel(2)
                .executes(command -> 1)
                .build();

        bridge.register(dispatcher, op, recording());

        var requirement = dispatcher.find("opcommand").getRequirement();
        assertNotNull(requirement, "a permission level should become a requires(...)");
        assertTrue(requirement.test(new FakeSource(4)), "an operator should pass");
        assertTrue(requirement.test(new FakeSource(2)), "exactly the threshold should pass");
        assertFalse(requirement.test(new FakeSource(0)), "a normal player should not");
    }

    @Test
    void addsNoRequirementForAnEveryonePermissionLevel() {
        CommandSpec open = CommandBuilder.create("open").executes(command -> 1).build();

        bridge.register(dispatcher, open, recording());

        org.junit.jupiter.api.Assertions.assertNull(dispatcher.find("open").getRequirement());
    }

    @Test
    void registersAliasesAsRedirectsToTheSameCommand() {
        CommandSpec fly = CommandBuilder.create("flytoggle")
                .aliases("f")
                .executes(command -> 1)
                .build();

        bridge.register(dispatcher, fly, recording());

        assertEquals(2, dispatcher.registered().size());
        LiteralArgumentBuilder<FakeSource> alias = dispatcher.find("f");
        assertNotNull(alias, "the alias should be registered too");
        assertNotNull(alias.getRedirect(), "an alias should redirect rather than duplicate");
        assertEquals("flytoggle", alias.getRedirect().getName());
    }

    @Test
    void aModCommandThatThrowsReportsFailureRatherThanCrashingTheGame() {
        ReModLog.reset();
        CommandSpec broken = CommandBuilder.create("broken").executes(command -> 1).build();
        bridge.register(dispatcher, broken, (command, context) -> {
            throw new IllegalStateException("a mod's command is broken");
        });

        int result = dispatcher.find("broken").getCommand()
                .run(new CommandContext<>(new FakeSource(4)));

        assertEquals(0, result, "a failure should report zero, not propagate");
    }

    @Test
    void aLiteralCanBothExecuteAndHaveSubcommands() {
        // This is the /fly shape: "/fly" toggles, "/fly on" is explicit.
        CommandSpec fly = CommandBuilder.create("fly")
                .executes(command -> 1)
                .subcommand(CommandBuilder.create("on").executes(command -> 1))
                .subcommand(CommandBuilder.create("off").executes(command -> 1))
                .build();

        assertTrue(bridge.register(dispatcher, fly, recording()));

        LiteralArgumentBuilder<FakeSource> node = dispatcher.find("fly");
        assertNotNull(node.getCommand(), "a bare /fly must run");
        assertEquals(2, node.getChildren().size(), "and its subcommands must still exist");
        assertNotNull(node.getChildren().get(0).getCommand());
    }

    @Test
    void refusesNullsQuietly() {
        CommandSpec fly = CommandBuilder.create("fly").executes(command -> 1).build();

        assertFalse(bridge.register(null, fly, recording()));
        assertFalse(bridge.register(dispatcher, null, recording()));
    }

    @Test
    void registersOnlyOncePerCommandEvenWithSubcommandsAndArguments() {
        AtomicInteger registrations = new AtomicInteger();
        CommandSpec fly = CommandBuilder.create("fly")
                .subcommand(CommandBuilder.create("speed")
                        .argument("value", ArgumentType.DOUBLE)
                        .executes(command -> 1))
                .subcommand(CommandBuilder.create("on").executes(command -> 1))
                .build();

        bridge.register(dispatcher, fly, (command, context) -> {
            registrations.incrementAndGet();
            return 1;
        });

        // One top-level literal, however deep the tree beneath it.
        assertEquals(1, dispatcher.registered().size());
        ArgumentBuilder<FakeSource, ?> speed = dispatcher.find("fly").getChildren().get(0);
        assertEquals("speed", speed.nodeName());
        assertEquals("value", speed.getChildren().get(0).nodeName());
    }
}
