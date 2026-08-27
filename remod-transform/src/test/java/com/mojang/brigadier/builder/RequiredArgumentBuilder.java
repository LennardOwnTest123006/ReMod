package com.mojang.brigadier.builder;

import com.mojang.brigadier.arguments.ArgumentType;

/** Stub of Brigadier's {@code RequiredArgumentBuilder}. */
public final class RequiredArgumentBuilder<S, T>
        extends ArgumentBuilder<S, RequiredArgumentBuilder<S, T>> {

    private final String name;
    private final ArgumentType<T> type;

    private RequiredArgumentBuilder(String name, ArgumentType<T> type) {
        this.name = name;
        this.type = type;
    }

    public static <S, T> RequiredArgumentBuilder<S, T> argument(String name,
                                                                ArgumentType<T> type) {
        return new RequiredArgumentBuilder<>(name, type);
    }

    public ArgumentType<T> getType() {
        return type;
    }

    @Override
    public String nodeName() {
        return name;
    }
}
