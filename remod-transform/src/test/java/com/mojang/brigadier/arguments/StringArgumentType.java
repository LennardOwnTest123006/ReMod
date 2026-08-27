package com.mojang.brigadier.arguments;

/** Stub of Brigadier's {@code StringArgumentType}. */
public final class StringArgumentType implements ArgumentType<String> {

    private final String kind;

    private StringArgumentType(String kind) {
        this.kind = kind;
    }

    public static StringArgumentType string() {
        return new StringArgumentType("string");
    }

    public static StringArgumentType word() {
        return new StringArgumentType("word");
    }

    public static StringArgumentType greedyString() {
        return new StringArgumentType("greedy");
    }

    @Override
    public String kind() {
        return kind;
    }
}
