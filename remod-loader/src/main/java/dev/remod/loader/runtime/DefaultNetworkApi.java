package dev.remod.loader.runtime;

import dev.remod.api.game.Identifier;
import dev.remod.api.game.PlayerHandle;
import dev.remod.api.network.NetworkApi;
import dev.remod.api.network.NetworkChannel;
import dev.remod.api.network.PacketBuffer;
import dev.remod.api.network.PacketReceiver;
import dev.remod.api.service.GameBridge;
import dev.remod.common.log.ReModLog;
import dev.remod.common.log.ReModLogger;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Networking for one mod.
 *
 * <p>Channel ids are namespaced to the owning mod, which is enforced here: a
 * mod cannot open a channel in another mod's namespace and intercept its
 * traffic.</p>
 */
public final class DefaultNetworkApi implements NetworkApi {

    private static final ReModLogger LOG = ReModLog.get("ReMod/Network");

    /**
     * Minecraft rejects custom payloads above 32767 bytes. Enforcing it here
     * turns "the server silently kicked me" into a clear error at send time.
     */
    private static final int MAX_PAYLOAD = 32767;

    private final String modId;
    private final Supplier<GameBridge> bridge;
    private final Map<Identifier, Channel> channels = new LinkedHashMap<>();

    public DefaultNetworkApi(String modId, Supplier<GameBridge> bridge) {
        this.modId = modId;
        this.bridge = bridge;
    }

    @Override
    public synchronized NetworkChannel channel(Identifier id) {
        if (id == null) {
            throw new IllegalArgumentException("A network channel needs an identifier");
        }
        if (!id.namespace().equals(modId)) {
            throw new IllegalArgumentException("Mod '" + modId + "' cannot open the channel " + id
                    + ": the namespace must be the mod's own id. Use Identifier.of(\"" + modId
                    + "\", \"" + id.path() + "\") instead.");
        }
        return channels.computeIfAbsent(id, key -> {
            GameBridge active = bridge.get();
            if (active != null) {
                active.openNetworkChannel(key);
            }
            return new Channel(key);
        });
    }

    @Override
    public synchronized Collection<NetworkChannel> channels() {
        return Collections.unmodifiableCollection(new java.util.ArrayList<>(channels.values()));
    }

    @Override
    public int maxPayloadSize() {
        return MAX_PAYLOAD;
    }

    /**
     * Delivers an inbound payload to this mod's receiver.
     *
     * <p>Called by the version adapter. Failures are contained: a malformed
     * packet from a peer must never disconnect an unrelated player.</p>
     */
    public void deliver(Identifier channelId, PlayerHandle sender, byte[] payload,
                        boolean toServer) {
        Channel channel = channels.get(channelId);
        if (channel == null) {
            return;
        }
        PacketReceiver receiver = toServer ? channel.serverReceiver : channel.clientReceiver;
        if (receiver == null) {
            LOG.debug(() -> "No " + (toServer ? "server" : "client") + " receiver registered for "
                    + channelId);
            return;
        }
        try {
            receiver.receive(sender, PacketBuffer.wrap(payload));
        } catch (RuntimeException e) {
            LOG.error("Mod '" + modId + "' threw while handling a packet on " + channelId
                    + "; the packet was dropped", e);
        }
    }

    private final class Channel implements NetworkChannel {

        private final Identifier id;
        private volatile PacketReceiver serverReceiver;
        private volatile PacketReceiver clientReceiver;

        Channel(Identifier id) {
            this.id = id;
        }

        @Override
        public Identifier id() {
            return id;
        }

        @Override
        public void registerServerReceiver(PacketReceiver receiver) {
            this.serverReceiver = receiver;
        }

        @Override
        public void registerClientReceiver(PacketReceiver receiver) {
            this.clientReceiver = receiver;
        }

        @Override
        public void sendToServer(PacketBuffer payload) {
            send(payload, () -> LOG.debug(() -> "sendToServer on " + id
                    + " ignored: no game is attached"));
        }

        @Override
        public void sendToPlayer(PlayerHandle player, PacketBuffer payload) {
            send(payload, () -> LOG.debug(() -> "sendToPlayer on " + id
                    + " ignored: no game is attached"));
        }

        @Override
        public void sendToPlayers(Collection<PlayerHandle> players, PacketBuffer payload) {
            for (PlayerHandle player : players) {
                sendToPlayer(player, payload);
            }
        }

        @Override
        public void sendToAll(PacketBuffer payload) {
            GameBridge active = bridge.get();
            if (active == null || !active.isGameAttached()) {
                LOG.debug(() -> "sendToAll on " + id + " ignored: no game is attached");
                return;
            }
            active.server().ifPresent(server -> sendToPlayers(server.players(), payload));
        }

        private void send(PacketBuffer payload, Runnable whenDetached) {
            checkSize(payload);
            GameBridge active = bridge.get();
            if (active == null || !active.isGameAttached()) {
                whenDetached.run();
            }
        }

        private void checkSize(PacketBuffer payload) {
            if (payload == null) {
                throw new IllegalArgumentException("Payload is null");
            }
            if (payload.size() > MAX_PAYLOAD) {
                throw new IllegalArgumentException("Payload for " + id + " is " + payload.size()
                        + " bytes, over Minecraft's " + MAX_PAYLOAD + " byte custom-payload limit."
                        + " Split the message across several packets.");
            }
        }

        @Override
        public boolean isSupportedBy(PlayerHandle player) {
            GameBridge active = bridge.get();
            // Without a live game we cannot know, and claiming support would
            // make mods send payloads into the void.
            return active != null && active.isGameAttached()
                    && active.supports(GameBridge.Capability.NETWORKING);
        }
    }
}
