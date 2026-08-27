package dev.remod.api.network;

import dev.remod.api.game.PlayerHandle;

/**
 * Handles one incoming payload.
 *
 * <p>Called on the network thread. Anything that touches game state must be
 * handed to {@code server.execute(...)} or {@code client.execute(...)} first --
 * ReMod cannot do that for you, because only the mod knows which parts of its
 * handler are thread-safe.</p>
 */
@FunctionalInterface
public interface PacketReceiver {

    /**
     * @param sender the player the payload came from; on the client this is the
     *               local player, since the payload came from the server
     * @param buffer the payload, positioned at the start
     */
    void receive(PlayerHandle sender, PacketBuffer buffer);
}
