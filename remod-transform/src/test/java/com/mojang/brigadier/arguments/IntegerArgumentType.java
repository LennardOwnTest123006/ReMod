package com.mojang.brigadier.arguments;

/** Stub of Brigadier's {@code IntegerArgumentType}. */
public final class IntegerArgumentType implements ArgumentType<Integer> {

    public static IntegerArgumentType integer() {
        return new IntegerArgumentType();
    }

    @Override
    public String kind() {
        return "integer";
    }
}
