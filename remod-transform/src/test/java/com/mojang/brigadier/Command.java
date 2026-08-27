package com.mojang.brigadier;

import com.mojang.brigadier.context.CommandContext;

/** Stub of Brigadier's {@code Command}, matching the real signature. */
@FunctionalInterface
public interface Command<S> {

    int SINGLE_SUCCESS = 1;

    int run(CommandContext<S> context);
}
