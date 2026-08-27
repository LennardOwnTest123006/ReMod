package dev.remod.cli;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A parsed {@code remod} command line: a verb, positional arguments and options. */
public final class CommandLine {

    private final String verb;
    private final List<String> positional;
    private final Map<String, String> options;

    private CommandLine(String verb, List<String> positional, Map<String, String> options) {
        this.verb = verb;
        this.positional = Collections.unmodifiableList(positional);
        this.options = Collections.unmodifiableMap(options);
    }

    /**
     * Parses {@code remod <verb> [args] [--option value] [--flag]}.
     *
     * <p>An option's value may be given as {@code --key value} or
     * {@code --key=value}; a bare {@code --flag} is {@code "true"}.</p>
     */
    public static CommandLine parse(String[] args) {
        List<String> positional = new ArrayList<>();
        Map<String, String> options = new LinkedHashMap<>();
        String verb = null;
        String[] arguments = args == null ? new String[0] : args;

        for (int i = 0; i < arguments.length; i++) {
            String argument = arguments[i];
            if (argument.startsWith("--") && argument.length() > 2) {
                String key = argument.substring(2);
                int equals = key.indexOf('=');
                if (equals > 0) {
                    options.put(key.substring(0, equals), key.substring(equals + 1));
                } else if (i + 1 < arguments.length
                        && !arguments[i + 1].startsWith("--")) {
                    options.put(key, arguments[++i]);
                } else {
                    options.put(key, "true");
                }
            } else if (verb == null) {
                verb = argument;
            } else {
                positional.add(argument);
            }
        }
        return new CommandLine(verb, positional, options);
    }

    /** The command name, or {@code null} when none was given. */
    public String verb() {
        return verb;
    }

    public List<String> positional() {
        return positional;
    }

    /** The nth positional argument, or {@code null}. */
    public String positional(int index) {
        return index < positional.size() ? positional.get(index) : null;
    }

    public String option(String key, String fallback) {
        String value = options.get(key);
        return value == null ? fallback : value;
    }

    public boolean flag(String key) {
        return "true".equalsIgnoreCase(options.get(key));
    }

    public boolean has(String key) {
        return options.containsKey(key);
    }

    public Map<String, String> options() {
        return options;
    }
}
