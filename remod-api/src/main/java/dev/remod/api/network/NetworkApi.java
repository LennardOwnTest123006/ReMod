package dev.remod.api.network;

import dev.remod.api.game.Identifier;

import java.util.Collection;

/** Entry point to ReMod networking. */
public interface NetworkApi {

    /**
     * Returns the channel with this id, creating it on first use.
     *
     * <p>The namespace must belong to the calling mod, so one mod cannot
     * intercept another's traffic.</p>
     */
    NetworkChannel channel(Identifier id);

    /** Every channel this mod has opened. */
    Collection<NetworkChannel> channels();

    /**
     * The largest payload a single message may carry, in bytes.
     * Larger payloads are rejected rather than silently truncated.
     */
    int maxPayloadSize();
}
