package dev.remod.api.game;

import java.util.Locale;

/** Minecraft's four game modes. */
public enum GameMode {

    SURVIVAL("survival"),
    CREATIVE("creative"),
    ADVENTURE("adventure"),
    SPECTATOR("spectator");

    private final String token;

    GameMode(String token) {
        this.token = token;
    }

    public String token() {
        return token;
    }

    /**
     * True when this mode grants flight on its own.
     *
     * <p>Worth checking before a mod toggles flight: in creative and spectator
     * the player can already fly, so turning it "off" there would fight the
     * game rather than the mod's own state.</p>
     */
    public boolean grantsFlight() {
        return this == CREATIVE || this == SPECTATOR;
    }

    public static GameMode parse(String text, GameMode fallback) {
        if (text != null) {
            for (GameMode mode : values()) {
                if (mode.token.equalsIgnoreCase(text.trim())) {
                    return mode;
                }
            }
        }
        return fallback;
    }
}
