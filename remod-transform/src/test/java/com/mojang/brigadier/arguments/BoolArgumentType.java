package com.mojang.brigadier.arguments;

/** Stub of Brigadier's {@code BoolArgumentType}. */
public final class BoolArgumentType implements ArgumentType<Boolean> {

    public static BoolArgumentType bool() {
        return new BoolArgumentType();
    }

    @Override
    public String kind() {
        return "boolean";
    }
}
