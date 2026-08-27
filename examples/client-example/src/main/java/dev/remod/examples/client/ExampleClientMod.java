package dev.remod.examples.client;

import dev.remod.api.ReModContext;
import dev.remod.api.ReModMod;
import dev.remod.api.client.ClientApi;
import dev.remod.api.client.Key;
import dev.remod.api.client.Keybind;
import dev.remod.api.client.gui.DrawContext;
import dev.remod.api.client.gui.HudRegistry;
import dev.remod.api.config.ConfigSpec;
import dev.remod.api.event.client.KeyInputEvent;
import dev.remod.api.event.tick.ClientTickEvent;
import dev.remod.api.event.tick.TickPhase;
import dev.remod.api.game.Text;
import dev.remod.api.game.TextColor;

import java.util.Optional;

/**
 * ReMod Example Client Mod -- keybind, HUD element and client tick handling.
 *
 * <p>Declared {@code "side": "client"} in its manifest, so ReMod refuses to
 * load it on a dedicated server rather than letting it crash there.</p>
 *
 * <p>Note the shape of the tick handler: keybind presses are drained in a
 * {@code while} loop, which is how Minecraft itself consumes them and what
 * stops a held key from toggling the HUD twenty times a second.</p>
 */
public class ExampleClientMod implements ReModMod {

    public static final String MOD_ID = "remodexampleclient";

    private static final ConfigSpec CONFIG = ConfigSpec.builder()
            .comment("Show the ReMod HUD overlay when the game starts.")
            .define("hudVisibleByDefault", true)
            .comment("Where the overlay sits: top-left, top-right, bottom-left, bottom-right.")
            .defineEnum("hudCorner", "top-left",
                    java.util.List.of("top-left", "top-right", "bottom-left", "bottom-right"))
            .build();

    private Keybind toggleHud;
    private HudRegistry.HudHandle overlay;
    private long ticks;
    private boolean hudVisible = true;

    @Override
    public void onPreInitialize(ReModContext context) {
        context.config().withSpec(CONFIG);
        hudVisible = context.config().getBoolean("hudVisibleByDefault");
    }

    @Override
    public void onInitialize(ReModContext context) {
        context.logger().info("ReMod Example Client Mod starting");
    }

    /**
     * CLIENT_INIT is the only phase where keybinds and HUD layers may be
     * registered; it never runs on a dedicated server.
     */
    @Override
    public void onClientInitialize(ReModContext context) {
        Optional<ClientApi> maybeClient = context.client();
        if (maybeClient.isEmpty()) {
            // Defensive: a client mod running with no client API means ReMod is
            // headless, e.g. under 'remod test'.
            context.logger().warn("No client API is available; skipping keybind and HUD setup.");
            return;
        }
        ClientApi client = maybeClient.get();

        toggleHud = client.keybinds().register(
                MOD_ID + ".toggle_hud", Key.H, "ReMod Examples");
        context.logger().info("Registered keybind " + toggleHud.id()
                + " (default key: " + toggleHud.defaultKey() + ")");

        overlay = client.hud().register(MOD_ID + ":overlay", this::renderOverlay);
        overlay.setVisible(hudVisible);

        context.events().subscribe(ClientTickEvent.class, event -> onClientTick(context, event));

        // A raw key listener, for comparison: this fires for every key, and is
        // not rebindable. Prefer a Keybind for anything the player should be
        // able to change.
        context.events().subscribe(KeyInputEvent.class, event -> {
            if (event.key() == Key.F9 && event.action() == KeyInputEvent.Action.PRESS) {
                client.sendChatMessage(Text.literal("[ReMod] Example client mod is loaded.")
                        .color(TextColor.AQUA));
            }
        });
    }

    private void onClientTick(ReModContext context, ClientTickEvent event) {
        if (event.phase() != TickPhase.END) {
            return;
        }
        ticks = event.tickCount();

        // Drain every queued press: a key held across several ticks would
        // otherwise toggle repeatedly.
        while (toggleHud != null && toggleHud.wasPressed()) {
            hudVisible = !hudVisible;
            if (overlay != null) {
                overlay.setVisible(hudVisible);
            }
            context.client().ifPresent(client -> client.sendActionBar(
                    Text.literal("ReMod overlay " + (hudVisible ? "shown" : "hidden"))));
        }
    }

    /**
     * Runs every frame, so it does no allocation beyond the text it draws and
     * no string formatting that could be hoisted.
     */
    private void renderOverlay(DrawContext draw) {
        Text line = Text.literal("ReMod  ").color(TextColor.GREEN)
                .append(Text.literal("tick " + ticks).color(TextColor.WHITE));
        int width = draw.textWidth(line) + 8;
        int height = draw.lineHeight() + 6;
        int x = 6;
        int y = 6;

        draw.fill(x, y, width, height, 0x80000000);
        draw.drawBorder(x, y, width, height, 0x40FFFFFF);
        draw.drawText(line, x + 4, y + 3, 0xFFFFFFFF);
    }

    @Override
    public void onShutdown(ReModContext context) {
        if (overlay != null) {
            overlay.remove();
        }
        context.logger().info("ReMod Example Client Mod stopped");
    }
}
