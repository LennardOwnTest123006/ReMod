package dev.remod.api;

/**
 * Which side of Minecraft's client/server split code is running on.
 *
 * <p>Minecraft ships one jar that runs as an integrated client, a dedicated
 * server, or both at once (single-player runs a server inside the client).
 * Getting this wrong is the single most common cause of a mod crashing on a
 * dedicated server, so ReMod makes the distinction explicit everywhere:
 * declared in the manifest, checked before an entrypoint is instantiated, and
 * queryable at runtime through {@link ReModContext#side()}.</p>
 */
public enum Side {

    /** The Minecraft client: rendering, input, HUD, keybinds. */
    CLIENT,

    /** A dedicated server: no rendering, no input, no client-only classes. */
    DEDICATED_SERVER,

    /** Code that is valid on both sides. */
    COMMON;

    /** True when code declared for {@code this} may run on {@code actual}. */
    public boolean runsOn(Side actual) {
        return this == COMMON || this == actual;
    }

    public boolean isClient() {
        return this == CLIENT;
    }

    public boolean isDedicatedServer() {
        return this == DEDICATED_SERVER;
    }

    /** Parses a manifest token such as {@code "client"}, defaulting to {@link #COMMON}. */
    public static Side parse(String text) {
        if (text == null) {
            return COMMON;
        }
        switch (text.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "client":
                return CLIENT;
            case "server":
            case "dedicated_server":
            case "dedicated-server":
                return DEDICATED_SERVER;
            default:
                return COMMON;
        }
    }

    /** The lower-case token used in manifests. */
    public String token() {
        switch (this) {
            case CLIENT: return "client";
            case DEDICATED_SERVER: return "server";
            default: return "common";
        }
    }
}
