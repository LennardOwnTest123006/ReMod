package dev.remod.api.game;

import dev.remod.common.json.Json;
import dev.remod.common.json.JsonArray;
import dev.remod.common.json.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A chat/UI text component.
 *
 * <p>Mods build text through this class rather than against Minecraft's own
 * {@code Component} type, because that type's package and shape have changed
 * repeatedly between versions. ReMod serialises to Minecraft's stable
 * <em>text component JSON</em> format, which every version since 1.7
 * understands, and the version adapter converts that to whatever the running
 * game expects.</p>
 *
 * <pre>{@code
 * Text greeting = Text.literal("Welcome, ")
 *         .append(Text.literal(player.name()).color(TextColor.GOLD).bold(true))
 *         .append(Text.literal("!"));
 * player.sendMessage(greeting);
 * }</pre>
 */
public final class Text {

    private final String literal;
    private final String translate;
    private final List<Text> args = new ArrayList<>();
    private final List<Text> extra = new ArrayList<>();
    private TextColor color;
    private Boolean bold;
    private Boolean italic;
    private Boolean underlined;
    private Boolean strikethrough;
    private Boolean obfuscated;

    private Text(String literal, String translate) {
        this.literal = literal;
        this.translate = translate;
    }

    /** Plain text. */
    public static Text literal(String text) {
        return new Text(text == null ? "" : text, null);
    }

    /** Alias for {@link #literal(String)}. */
    public static Text of(String text) {
        return literal(text);
    }

    /** An empty component, useful as the root of a concatenation. */
    public static Text empty() {
        return literal("");
    }

    /**
     * A client-translated component. The key is resolved from the player's
     * language file, so this is the right choice for anything a player reads.
     */
    public static Text translatable(String key, Text... arguments) {
        Text text = new Text(null, key);
        Collections.addAll(text.args, arguments);
        return text;
    }

    public Text append(Text child) {
        if (child != null) {
            extra.add(child);
        }
        return this;
    }

    public Text append(String child) {
        return append(literal(child));
    }

    public Text color(TextColor value) {
        this.color = value;
        return this;
    }

    public Text bold(boolean value) {
        this.bold = value;
        return this;
    }

    public Text italic(boolean value) {
        this.italic = value;
        return this;
    }

    public Text underlined(boolean value) {
        this.underlined = value;
        return this;
    }

    public Text strikethrough(boolean value) {
        this.strikethrough = value;
        return this;
    }

    public Text obfuscated(boolean value) {
        this.obfuscated = value;
        return this;
    }

    public TextColor colorOrNull() {
        return color;
    }

    /** The text with all formatting and children flattened away. */
    public String plainText() {
        StringBuilder sb = new StringBuilder();
        flatten(sb);
        return sb.toString();
    }

    private void flatten(StringBuilder sb) {
        if (literal != null) {
            sb.append(literal);
        } else if (translate != null) {
            // Without a language file the key itself is the most useful stand-in.
            sb.append(translate);
            for (Text arg : args) {
                sb.append(' ');
                arg.flatten(sb);
            }
        }
        for (Text child : extra) {
            child.flatten(sb);
        }
    }

    /** Minecraft's text component JSON representation of this text. */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        if (translate != null) {
            json.put("translate", translate);
            if (!args.isEmpty()) {
                JsonArray with = new JsonArray();
                for (Text arg : args) {
                    with.add(arg.toJson());
                }
                json.put("with", with);
            }
        } else {
            json.put("text", literal == null ? "" : literal);
        }
        if (color != null) {
            json.put("color", color.colorName());
        }
        json.putIfPresent("bold", bold);
        json.putIfPresent("italic", italic);
        json.putIfPresent("underlined", underlined);
        json.putIfPresent("strikethrough", strikethrough);
        json.putIfPresent("obfuscated", obfuscated);
        if (!extra.isEmpty()) {
            JsonArray children = new JsonArray();
            for (Text child : extra) {
                children.add(child.toJson());
            }
            json.put("extra", children);
        }
        return json;
    }

    /** The serialised form the version adapter hands to the game. */
    public String toJsonString() {
        return Json.write(toJson());
    }

    @Override
    public String toString() {
        return plainText();
    }
}
