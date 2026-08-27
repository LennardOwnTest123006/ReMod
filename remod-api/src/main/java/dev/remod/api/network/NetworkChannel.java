package dev.remod.api.network;

import dev.remod.api.game.Identifier;
import dev.remod.api.game.PlayerHandle;

import java.util.Collection;

/**
 * A named channel for custom client/server messages.
 *
 * <p>Rides Minecraft's vanilla custom-payload packet, so a ReMod server and a
 * ReMod client talk to each other without any protocol change, and a vanilla
 * client simply ignores payloads it does not understand.</p>
 *
 * <pre>{@code
 * NetworkChannel channel = context.network().channel(Identifier.of("simplemod", "sync"));
 * channel.registerServerReceiver((player, buffer) -> {
 *     int value = buffer.readVarInt();
 *     context.game().side(); // ...
 * });
 * channel.sendToServer(new PacketBuffer().writeVarInt(42));
 * }</pre>
 */
public interface NetworkChannel {

    Identifier id();

    /** Handles payloads arriving at the server from a client. */
    void registerServerReceiver(PacketReceiver receiver);

    /** Handles payloads arriving at the client from the server. */
    void registerClientReceiver(PacketReceiver receiver);

    /** Client to server. */
    void sendToServer(PacketBuffer payload);

    /** Server to one client. */
    void sendToPlayer(PlayerHandle player, PacketBuffer payload);

    /** Server to a set of clients. */
    void sendToPlayers(Collection<PlayerHandle> players, PacketBuffer payload);

    /** Server to everyone. */
    void sendToAll(PacketBuffer payload);

    /**
     * True when {@code player}'s client has this channel registered.
     *
     * <p>Always check before sending to avoid spamming vanilla clients with
     * payloads they will discard.</p>
     */
    boolean isSupportedBy(PlayerHandle player);
}
