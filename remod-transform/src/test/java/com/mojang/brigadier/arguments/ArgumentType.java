package com.mojang.brigadier.arguments;

/** Stub of Brigadier's {@code ArgumentType}. */
public interface ArgumentType<T> {

    /** A label the tests assert on, standing in for real parsing behaviour. */
    String kind();
}
