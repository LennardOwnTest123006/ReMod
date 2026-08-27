package com.mojang.brigadier.builder;

/** Stub of Brigadier's {@code LiteralArgumentBuilder}. */
public final class LiteralArgumentBuilder<S>
        extends ArgumentBuilder<S, LiteralArgumentBuilder<S>> {

    private final String literal;

    private LiteralArgumentBuilder(String literal) {
        this.literal = literal;
    }

    public static <S> LiteralArgumentBuilder<S> literal(String name) {
        return new LiteralArgumentBuilder<>(name);
    }

    public String getLiteral() {
        return literal;
    }

    @Override
    public String nodeName() {
        return literal;
    }
}
