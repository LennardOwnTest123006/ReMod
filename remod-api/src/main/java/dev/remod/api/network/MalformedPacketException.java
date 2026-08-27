package dev.remod.api.network;

/**
 * Thrown when a payload cannot be decoded.
 *
 * <p>ReMod catches this around every receiver, logs it against the owning mod
 * and drops the packet. A malformed packet from a hostile or out-of-date peer
 * must never disconnect an unrelated player or crash the server.</p>
 */
public class MalformedPacketException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MalformedPacketException(String message) {
        super(message);
    }
}
