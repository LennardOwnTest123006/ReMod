package com.mojang.brigadier.builder;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.CommandNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Stub of Brigadier's {@code ArgumentBuilder}.
 *
 * <p>Deliberately carries <b>both</b> {@code then} overloads, exactly as the
 * real class does. Reflection that picks a method by name and arity alone will
 * choose the wrong one here, which is the point: that bug should fail a test
 * rather than a user's game.</p>
 */
public abstract class ArgumentBuilder<S, T extends ArgumentBuilder<S, T>> {

    private final List<ArgumentBuilder<S, ?>> children = new ArrayList<>();
    private final List<CommandNode<S>> nodeChildren = new ArrayList<>();
    private Command<S> command;
    private Predicate<S> requirement;
    private CommandNode<S> redirect;

    @SuppressWarnings("unchecked")
    protected T self() {
        return (T) this;
    }

    public T then(ArgumentBuilder<S, ?> child) {
        children.add(child);
        return self();
    }

    public T then(CommandNode<S> child) {
        nodeChildren.add(child);
        return self();
    }

    public T executes(Command<S> value) {
        this.command = value;
        return self();
    }

    public T requires(Predicate<S> value) {
        this.requirement = value;
        return self();
    }

    public T redirect(CommandNode<S> target) {
        this.redirect = target;
        return self();
    }

    public List<ArgumentBuilder<S, ?>> getChildren() {
        return children;
    }

    public Command<S> getCommand() {
        return command;
    }

    public Predicate<S> getRequirement() {
        return requirement;
    }

    public CommandNode<S> getRedirect() {
        return redirect;
    }

    /** The literal or argument name this node matches. */
    public abstract String nodeName();
}
