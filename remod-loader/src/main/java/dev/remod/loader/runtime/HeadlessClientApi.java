package dev.remod.loader.runtime;

import dev.remod.api.client.ClientApi;
import dev.remod.api.client.Key;
import dev.remod.api.client.Keybind;
import dev.remod.api.client.KeybindRegistry;
import dev.remod.api.client.gui.DrawContext;
import dev.remod.api.client.gui.HudLayer;
import dev.remod.api.client.gui.HudRegistry;
import dev.remod.api.game.PlayerHandle;
import dev.remod.api.game.Text;
import dev.remod.api.game.WorldHandle;
import dev.remod.common.log.ReModLog;
import dev.remod.common.log.ReModLogger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A client API with no Minecraft behind it.
 *
 * <p>Used by {@code remod test} and by unit tests. Registrations are recorded
 * and queryable, keybind presses can be simulated, and HUD layers can be
 * rendered into a capturing {@link DrawContext}, so a client mod's logic is
 * testable without launching the game.</p>
 */
public final class HeadlessClientApi implements ClientApi {

    private static final ReModLogger LOG = ReModLog.get("ReMod/Client");

    private final Keybinds keybinds = new Keybinds();
    private final Huds hud = new Huds();
    private final List<Text> chatLog = new ArrayList<>();
    private final List<Text> actionBarLog = new ArrayList<>();

    @Override
    public KeybindRegistry keybinds() {
        return keybinds;
    }

    @Override
    public HudRegistry hud() {
        return hud;
    }

    @Override
    public Optional<PlayerHandle> player() {
        return Optional.empty();
    }

    @Override
    public Optional<WorldHandle> world() {
        return Optional.empty();
    }

    @Override
    public void sendChatMessage(Text message) {
        chatLog.add(message);
        LOG.info("[chat] " + message.plainText());
    }

    @Override
    public void sendActionBar(Text message) {
        actionBarLog.add(message);
        LOG.debug(() -> "[action bar] " + message.plainText());
    }

    @Override
    public int framesPerSecond() {
        return 0;
    }

    @Override
    public void execute(Runnable task) {
        // Without a client thread, running inline is the honest equivalent.
        task.run();
    }

    /** Messages a mod has printed to chat, for assertions in tests. */
    public List<Text> chatLog() {
        return Collections.unmodifiableList(chatLog);
    }

    public List<Text> actionBarLog() {
        return Collections.unmodifiableList(actionBarLog);
    }

    /** Queues a press on a registered keybind, as the player pressing the key would. */
    public void simulatePress(String keybindId) {
        keybinds.find(keybindId).ifPresent(bind -> ((RecordedKeybind) bind).queuePress());
    }

    /** Renders every visible HUD layer into {@code context}. */
    public void renderHud(DrawContext context) {
        hud.render(context);
    }

    private static final class Keybinds implements KeybindRegistry {

        private final Map<String, Keybind> byId = new LinkedHashMap<>();

        @Override
        public Keybind register(String id, Key defaultKey, String category) {
            if (id == null || id.trim().isEmpty()) {
                throw new IllegalArgumentException("A keybind needs an id");
            }
            if (byId.containsKey(id)) {
                throw new IllegalStateException("Keybind '" + id + "' is already registered."
                        + " Keybind ids must be unique; prefix yours with your mod id.");
            }
            RecordedKeybind keybind = new RecordedKeybind(id,
                    defaultKey == null ? Key.UNKNOWN : defaultKey,
                    category == null ? "ReMod" : category);
            byId.put(id, keybind);
            return keybind;
        }

        @Override
        public Optional<Keybind> find(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Collection<Keybind> keybinds() {
            return Collections.unmodifiableCollection(byId.values());
        }
    }

    private static final class RecordedKeybind implements Keybind {

        private final String id;
        private final Key defaultKey;
        private final String category;
        private final Deque<Boolean> pending = new ArrayDeque<>();
        private volatile boolean down;

        RecordedKeybind(String id, Key defaultKey, String category) {
            this.id = id;
            this.defaultKey = defaultKey;
            this.category = category;
        }

        void queuePress() {
            pending.add(Boolean.TRUE);
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String category() {
            return category;
        }

        @Override
        public Key defaultKey() {
            return defaultKey;
        }

        @Override
        public Key boundKey() {
            return defaultKey;
        }

        @Override
        public boolean wasPressed() {
            return pending.poll() != null;
        }

        @Override
        public boolean isDown() {
            return down;
        }
    }

    private static final class Huds implements HudRegistry {

        private final Map<String, Layer> layers = new LinkedHashMap<>();

        @Override
        public HudHandle register(String id, HudLayer layer) {
            if (id == null || id.trim().isEmpty()) {
                throw new IllegalArgumentException("A HUD layer needs an id");
            }
            if (layer == null) {
                throw new IllegalArgumentException("HUD layer '" + id + "' has no renderer");
            }
            if (layers.containsKey(id)) {
                throw new IllegalStateException("HUD layer '" + id + "' is already registered");
            }
            Layer registered = new Layer(id, layer, layers);
            layers.put(id, registered);
            return registered;
        }

        @Override
        public Collection<String> layerIds() {
            return Collections.unmodifiableCollection(new ArrayList<>(layers.keySet()));
        }

        void render(DrawContext context) {
            for (Layer layer : new ArrayList<>(layers.values())) {
                if (layer.visible && layer.delegate.isVisible()) {
                    try {
                        layer.delegate.render(context);
                    } catch (RuntimeException e) {
                        LOG.error("HUD layer '" + layer.id + "' threw while rendering", e);
                    }
                }
            }
        }
    }

    private static final class Layer implements HudRegistry.HudHandle {

        private final String id;
        private final HudLayer delegate;
        private final Map<String, Layer> owner;
        private volatile boolean visible = true;

        Layer(String id, HudLayer delegate, Map<String, Layer> owner) {
            this.id = id;
            this.delegate = delegate;
            this.owner = owner;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public void setVisible(boolean value) {
            this.visible = value;
        }

        @Override
        public boolean isVisible() {
            return visible;
        }

        @Override
        public void remove() {
            owner.remove(id);
        }
    }
}
