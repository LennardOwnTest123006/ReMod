package com.mojang.brigadier.arguments;

/** Stub of Brigadier's {@code DoubleArgumentType}. */
public final class DoubleArgumentType implements ArgumentType<Double> {

    public static DoubleArgumentType doubleArg() {
        return new DoubleArgumentType();
    }

    @Override
    public String kind() {
        return "double";
    }
}
