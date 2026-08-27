package dev.remod.api.game;

import java.util.Locale;

/** Minecraft's sixteen named colours, plus their RGB values for modern clients. */
public enum TextColor {

    BLACK("black", 0x000000),
    DARK_BLUE("dark_blue", 0x0000AA),
    DARK_GREEN("dark_green", 0x00AA00),
    DARK_AQUA("dark_aqua", 0x00AAAA),
    DARK_RED("dark_red", 0xAA0000),
    DARK_PURPLE("dark_purple", 0xAA00AA),
    GOLD("gold", 0xFFAA00),
    GRAY("gray", 0xAAAAAA),
    DARK_GRAY("dark_gray", 0x555555),
    BLUE("blue", 0x5555FF),
    GREEN("green", 0x55FF55),
    AQUA("aqua", 0x55FFFF),
    RED("red", 0xFF5555),
    LIGHT_PURPLE("light_purple", 0xFF55FF),
    YELLOW("yellow", 0xFFFF55),
    WHITE("white", 0xFFFFFF);

    private final String name;
    private final int rgb;

    TextColor(String name, int rgb) {
        this.name = name;
        this.rgb = rgb;
    }

    /** The token Minecraft's text component JSON uses. */
    public String colorName() {
        return name;
    }

    public int rgb() {
        return rgb;
    }

    public static TextColor byName(String name, TextColor fallback) {
        if (name != null) {
            for (TextColor color : values()) {
                if (color.name.equals(name.trim().toLowerCase(Locale.ROOT))) {
                    return color;
                }
            }
        }
        return fallback;
    }
}
