package com.mojang.brigadier.context;

import java.util.LinkedHashMap;
import java.util.Map;

/** Stub of Brigadier's {@code CommandContext}. */
public class CommandContext<S> {

    private final S source;
    private final Map<String, Object> arguments = new LinkedHashMap<>();

    public CommandContext(S source) {
        this.source = source;
    }

    public S getSource() {
        return source;
    }

    public CommandContext<S> with(String name, Object value) {
        arguments.put(name, value);
        return this;
    }

    public <T> T getArgument(String name, Class<T> type) {
        Object value = arguments.get(name);
        if (value == null) {
            throw new IllegalArgumentException("No such argument '" + name + "'");
        }
        return type.cast(value);
    }

    public Map<String, Object> arguments() {
        return arguments;
    }
}
