package dev.remod.examples.simple;

import dev.remod.api.ReModContext;
import dev.remod.api.ReModMod;
import dev.remod.api.command.ArgumentType;
import dev.remod.api.command.CommandBuilder;
import dev.remod.api.command.CommandException;
import dev.remod.api.config.ConfigSpec;
import dev.remod.api.event.lifecycle.ModsLoadedEvent;
import dev.remod.api.event.player.PlayerJoinEvent;
import dev.remod.api.game.Identifier;
import dev.remod.api.game.Text;
import dev.remod.api.game.TextColor;
import dev.remod.api.registry.BlockDefinition;
import dev.remod.api.registry.CreativeTabDefinition;
import dev.remod.api.registry.ItemDefinition;
import dev.remod.api.registry.Rarity;
import dev.remod.api.registry.RegistryEntry;

/**
 * ReMod Simple Mod -- the reference example.
 *
 * <p>Demonstrates, in the order a new mod author meets them:</p>
 *
 * <ol>
 *   <li>a configuration file with defaults and validation;</li>
 *   <li>a custom item and a custom block;</li>
 *   <li>a creative tab holding both;</li>
 *   <li>a command with subcommands and arguments;</li>
 *   <li>an event listener;</li>
 *   <li>a startup message.</li>
 * </ol>
 *
 * <p>Everything here compiles against the public ReMod API only -- there is no
 * privileged access a mod in the wild would not have.</p>
 */
public class SimpleMod implements ReModMod {

    /** The mod id, and the namespace for everything this mod registers. */
    public static final String MOD_ID = "simplemod";

    /**
     * The configuration schema. Declaring it means the file is generated with
     * comments and defaults, and a bad value falls back rather than crashing.
     */
    private static final ConfigSpec CONFIG = ConfigSpec.builder()
            .comment("Shown to players when they join the server.")
            .define("greeting", "Welcome to a ReMod server!")
            .comment("Set to false to stop greeting players.")
            .define("greetPlayers", true)
            .comment("How many rubies /simplemod give hands out at once.")
            .defineInRange("giveAmount", 4, 1, 64)
            .build();

    private RegistryEntry<ItemDefinition> ruby;
    private RegistryEntry<BlockDefinition> rubyBlock;

    @Override
    public void onPreInitialize(ReModContext context) {
        // PRE_INIT is for reading configuration and deciding what to register.
        context.config().withSpec(CONFIG);
        context.logger().debug(() -> "Config loaded from " + context.config().file());
    }

    @Override
    public void onInitialize(ReModContext context) {
        context.logger().info("Hello from ReMod Simple Mod "
                + context.modVersion() + " on Minecraft "
                + context.game().minecraftVersion());

        registerContent(context);
        registerCommands(context);
        registerEvents(context);
    }

    /** Items and blocks are registered during INIT, while the registries are open. */
    private void registerContent(ReModContext context) {
        ruby = context.registries().items().register(
                ItemDefinition.builder(Identifier.of(MOD_ID, "ruby"))
                        .displayName(Text.literal("Ruby").color(TextColor.RED))
                        .maxStackSize(64)
                        .rarity(Rarity.RARE)
                        .build());

        rubyBlock = context.registries().blocks().register(
                BlockDefinition.builder(Identifier.of(MOD_ID, "ruby_block"))
                        .displayName(Text.literal("Block of Ruby"))
                        .strength(5.0f, 6.0f)
                        .material(BlockDefinition.MaterialKind.METAL)
                        .soundGroup(BlockDefinition.SoundGroup.METAL)
                        .requiresTool(true)
                        .build());

        context.registries().creativeTabs().register(
                CreativeTabDefinition.builder(Identifier.of(MOD_ID, "general"))
                        .title(Text.literal("ReMod Simple Mod"))
                        .icon(ruby.id())
                        .add(ruby.id())
                        .add(rubyBlock.id())
                        .build());

        context.logger().info("Registered " + ruby.id() + " and " + rubyBlock.id());
    }

    /** A command with two subcommands, one of which takes arguments. */
    private void registerCommands(ReModContext context) {
        context.commands().register(CommandBuilder.create(MOD_ID)
                .aliases("smod")
                .description("ReMod Simple Mod utilities")
                .subcommand(CommandBuilder.create("info")
                        .description("Show what this mod registered")
                        .executes(command -> {
                            command.source().sendFeedback(Text.literal("ReMod Simple Mod ")
                                    .append(Text.literal(context.modVersion())
                                            .color(TextColor.GOLD)));
                            command.source().sendFeedback(
                                    Text.literal("Item:  " + ruby.id()));
                            command.source().sendFeedback(
                                    Text.literal("Block: " + rubyBlock.id()));
                            return 1;
                        }))
                .subcommand(CommandBuilder.create("give")
                        .description("Give rubies to a player")
                        .permissionLevel(2)
                        .argument("target", ArgumentType.PLAYER)
                        .optionalArgument("amount", ArgumentType.INTEGER, null)
                        .executes(command -> {
                            int amount = command.has("amount")
                                    ? command.getInt("amount")
                                    : context.config().getInt("giveAmount");
                            if (amount < 1) {
                                // A CommandException is shown to the caller in
                                // red; anything else would be a mod bug.
                                throw new CommandException(
                                        Text.literal("Amount must be at least 1.")
                                                .color(TextColor.RED));
                            }
                            String target = command.getString("target");
                            command.source().sendFeedback(Text.literal(
                                    "Gave " + amount + " ruby(s) to " + target + "."));
                            return amount;
                        })));
    }

    /** Two listeners: one on ReMod's own lifecycle, one on a game event. */
    private void registerEvents(ReModContext context) {
        context.events().subscribe(ModsLoadedEvent.class, event ->
                context.logger().info("ReMod finished loading " + event.modCount()
                        + " mod(s); Simple Mod is ready."));

        context.events().subscribe(PlayerJoinEvent.class, event -> {
            if (!context.config().getBoolean("greetPlayers")) {
                return;
            }
            event.player().sendMessage(
                    Text.literal(context.config().getString("greeting"))
                            .color(TextColor.GREEN));
        });
    }

    @Override
    public void onPostInitialize(ReModContext context) {
        // POST_INIT runs once every mod has finished INIT, so cross-mod
        // integration belongs here.
        if (context.isModLoaded("remodexampleserver")) {
            context.logger().info("The example server mod is present; "
                    + "its features will work alongside this one.");
        }
    }

    @Override
    public void onShutdown(ReModContext context) {
        context.logger().info("ReMod Simple Mod shutting down.");
    }
}
