package dev.remod.api;

/**
 * The ordered phases a ReMod mod passes through.
 *
 * <p>Every mod completes a phase before any mod starts the next one. That is
 * what makes cross-mod interaction predictable: by the time your
 * {@code POST_INIT} runs, every other mod's registrations from {@code INIT}
 * exist.</p>
 *
 * <pre>
 *   PRE_INIT -&gt; INIT -&gt; POST_INIT -&gt; CLIENT_INIT or SERVER_INIT -&gt; ... -&gt; SHUTDOWN
 * </pre>
 */
public enum LifecyclePhase {

    /**
     * Configuration is available; nothing has been registered yet. Read your
     * config here and decide what you are going to register.
     */
    PRE_INIT("pre-init", Side.COMMON),

    /**
     * The main phase. Register items, blocks, commands, events and network
     * channels here.
     */
    INIT("init", Side.COMMON),

    /**
     * Every mod has finished {@link #INIT}. Safe place to look up content
     * registered by other mods and to wire cross-mod integrations.
     */
    POST_INIT("post-init", Side.COMMON),

    /** Client-only setup: keybinds, HUD layers, screens. Skipped on a dedicated server. */
    CLIENT_INIT("client-init", Side.CLIENT),

    /** Dedicated-server-only setup. Skipped on the client. */
    SERVER_INIT("server-init", Side.DEDICATED_SERVER),

    /** The game is shutting down. Flush and close anything you own. */
    SHUTDOWN("shutdown", Side.COMMON);

    private final String label;
    private final Side side;

    LifecyclePhase(String label, Side side) {
        this.label = label;
        this.side = side;
    }

    public String label() {
        return label;
    }

    /** The side this phase runs on; {@link Side#COMMON} means "always". */
    public Side side() {
        return side;
    }

    /** True when this phase should run on {@code actual}. */
    public boolean appliesTo(Side actual) {
        return side.runsOn(actual);
    }
}
