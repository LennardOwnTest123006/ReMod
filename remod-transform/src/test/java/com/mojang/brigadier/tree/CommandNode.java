package com.mojang.brigadier.tree;

import java.util.ArrayList;
import java.util.List;

/** Stub of Brigadier's {@code CommandNode}. */
public class CommandNode<S> {

    private final String name;
    private final List<CommandNode<S>> children = new ArrayList<>();

    public CommandNode(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public List<CommandNode<S>> getChildren() {
        return children;
    }

    public void addChild(CommandNode<S> child) {
        children.add(child);
    }
}
