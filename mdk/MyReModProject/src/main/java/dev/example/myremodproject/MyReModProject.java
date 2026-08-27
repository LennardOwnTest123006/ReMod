package dev.example.myremodproject;

import dev.remod.api.ReModContext;
import dev.remod.api.ReModMod;
import dev.remod.api.command.CommandBuilder;
import dev.remod.api.config.ConfigSpec;
import dev.remod.api.event.lifecycle.ModsLoadedEvent;
import dev.remod.api.game.Identifier;
import dev.remod.api.game.Text;
import dev.remod.api.registry.ItemDefinition;

/**
 * MyReModProject.
 *
 * <p>Every ReMod mod starts here: implement {@link ReModMod} and name this
 * class in remod.mod.json. See tutorial.txt for the full walkthrough.</p>
 */
public class MyReModProject implements ReModMod {

    /** Your mod id. Use it as the namespace for everything you register. */
    public static final String MOD_ID = "myremodproject";

    private static final ConfigSpec CONFIG = ConfigSpec.builder()
            .comment("Printed to the log when the mod starts.")
            .define("greeting", "Hello from MyReModProject!")
            .comment("Set to false to keep quiet.")
            .define("enabled", true)
            .build();

    @Override
    public void onPreInitialize(ReModContext context) {
        // Attach the schema; the file is created with defaults on first run.
        context.config().withSpec(CONFIG);
    }

    @Override
    public void onInitialize(ReModContext context) {
        if (context.config().getBoolean("enabled")) {
            context.logger().info(context.config().getString("greeting"));
        }

        // Register an item.
        context.registries().items().register(
                ItemDefinition.builder(Identifier.of(MOD_ID, "example_item"))
                        .displayName(Text.literal("Example Item"))
                        .maxStackSize(16)
                        .build());

        // Register a command: /myremodproject
        context.commands().register(CommandBuilder.create(MOD_ID)
                .description("MyReModProject information")
                .executes(command -> {
                    command.source().sendFeedback(
                            Text.literal("MyReModProject "
                                    + context.modVersion() + " is running."));
                    return 1;
                }));

        // React to an event.
        context.events().subscribe(ModsLoadedEvent.class, event ->
                context.logger().info("ReMod finished loading "
                        + event.modCount() + " mod(s)."));
    }

    @Override
    public void onShutdown(ReModContext context) {
        context.logger().info("Goodbye from MyReModProject");
    }
}
