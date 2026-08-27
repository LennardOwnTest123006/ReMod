package com.mojang.brigadier;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;

import java.util.ArrayList;
import java.util.List;

/** Stub of Brigadier's {@code CommandDispatcher}. */
public final class CommandDispatcher<S> {

    private final List<LiteralArgumentBuilder<S>> registered = new ArrayList<>();

    public LiteralCommandNode<S> register(LiteralArgumentBuilder<S> command) {
        registered.add(command);
        return new LiteralCommandNode<>(command.getLiteral());
    }

    /** Everything registered so far, for assertions. */
    public List<LiteralArgumentBuilder<S>> registered() {
        return registered;
    }

    /** Finds a registered top-level command by its literal. */
    public LiteralArgumentBuilder<S> find(String literal) {
        return registered.stream()
                .filter(builder -> builder.getLiteral().equals(literal))
                .findFirst().orElse(null);
    }
}
