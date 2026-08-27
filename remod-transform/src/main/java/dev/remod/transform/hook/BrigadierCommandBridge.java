package dev.remod.transform.hook;

import dev.remod.api.command.ArgumentType;
import dev.remod.api.command.CommandSpec;
import dev.remod.common.log.ReModLog;
import dev.remod.common.log.ReModLogger;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Registers ReMod commands into Minecraft's live Brigadier dispatcher.
 *
 * <h2>Why reflection</h2>
 *
 * <p>Brigadier ships unobfuscated, so its class and method names are real and
 * stable. Using it reflectively rather than compiling against it keeps ReMod
 * free of a Brigadier dependency it would otherwise have to version-match
 * against whatever Minecraft bundles -- and a mismatch there is the kind of
 * failure that only shows up on a user's machine.</p>
 *
 * <h2>What is registered</h2>
 *
 * <p>A {@link CommandSpec} becomes a literal node with its subcommands as
 * child literals and its arguments as Brigadier argument nodes. Aliases are
 * registered as redirects to the same node, which is how vanilla does it.</p>
 *
 * <p>Everything here degrades rather than throws. A command that cannot be
 * expressed is logged and skipped; the game keeps its own commands and the rest
 * of ReMod's still register.</p>
 */
public final class BrigadierCommandBridge {

    private static final ReModLogger LOG = ReModLog.get("ReMod/Commands");

    private static final String DISPATCHER = "com.mojang.brigadier.CommandDispatcher";
    private static final String LITERAL_BUILDER =
            "com.mojang.brigadier.builder.LiteralArgumentBuilder";
    private static final String REQUIRED_BUILDER =
            "com.mojang.brigadier.builder.RequiredArgumentBuilder";
    private static final String COMMAND = "com.mojang.brigadier.Command";
    private static final String ARGUMENT_BUILDER =
            "com.mojang.brigadier.builder.ArgumentBuilder";
    private static final String STRING_TYPE =
            "com.mojang.brigadier.arguments.StringArgumentType";
    private static final String INTEGER_TYPE =
            "com.mojang.brigadier.arguments.IntegerArgumentType";
    private static final String DOUBLE_TYPE =
            "com.mojang.brigadier.arguments.DoubleArgumentType";
    private static final String BOOLEAN_TYPE =
            "com.mojang.brigadier.arguments.BoolArgumentType";

    private final ClassLoader loader;

    public BrigadierCommandBridge(ClassLoader loader) {
        this.loader = loader == null ? BrigadierCommandBridge.class.getClassLoader() : loader;
    }

    /** True when Brigadier is reachable, so registration can be attempted at all. */
    public boolean isAvailable() {
        return findClass(DISPATCHER).isPresent()
                && findClass(LITERAL_BUILDER).isPresent()
                && findClass(COMMAND).isPresent();
    }

    /**
     * Registers one command into the game's dispatcher.
     *
     * @param dispatcher the object handed over by {@link ReModHooks}
     * @param executor   invoked when the command runs, receiving the Brigadier
     *                   context; returns the command's result count
     * @return true when the command was registered
     */
    public boolean register(Object dispatcher, CommandSpec command,
                            CommandInvoker executor) {
        if (dispatcher == null || command == null) {
            return false;
        }
        try {
            Object node = buildLiteral(command, executor);
            if (node == null) {
                return false;
            }
            Method registerMethod = dispatcher.getClass()
                    .getMethod("register", forName(LITERAL_BUILDER));
            Object registered = registerMethod.invoke(dispatcher, node);

            for (String alias : command.aliases()) {
                registerAlias(dispatcher, alias, registered, executor);
            }
            LOG.info("Registered /" + command.name() + " with Minecraft"
                    + (command.aliases().isEmpty() ? ""
                            : " (aliases: " + String.join(", ", command.aliases()) + ")"));
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.error("Could not register /" + command.name()
                    + " with Minecraft; the command will not be available", e);
            return false;
        }
    }

    /** Builds a literal node, with subcommands and arguments beneath it. */
    private Object buildLiteral(CommandSpec command, CommandInvoker executor)
            throws ReflectiveOperationException {
        Object builder = forName(LITERAL_BUILDER)
                .getMethod("literal", String.class)
                .invoke(null, command.name());

        if (command.permissionLevel() > 0) {
            builder = requirePermission(builder, command.permissionLevel());
        }

        for (CommandSpec sub : command.subcommands()) {
            Object child = buildLiteral(sub, executor);
            if (child != null) {
                builder = then(builder, child);
            }
        }

        Object body = buildArgumentChain(command, executor);
        if (body != null) {
            builder = body == builder ? builder : then(builder, body);
        }
        // A literal may both execute and have children: "/fly" toggles while
        // "/fly on" still works.
        if (command.executor() != null && command.arguments().isEmpty()) {
            builder = executes(builder, command, executor);
        }
        return builder;
    }

    /**
     * Chains a command's arguments into nested Brigadier nodes.
     *
     * <p>Brigadier models arguments as a chain rather than a list, and an
     * optional argument means the node before it must also be executable --
     * that is how "the argument may be omitted" is expressed.</p>
     */
    private Object buildArgumentChain(CommandSpec command, CommandInvoker executor)
            throws ReflectiveOperationException {
        List<CommandSpec.Argument> arguments = command.arguments();
        if (arguments.isEmpty()) {
            return null;
        }
        Object[] nodes = new Object[arguments.size()];
        for (int i = 0; i < arguments.size(); i++) {
            CommandSpec.Argument argument = arguments.get(i);
            Object type = brigadierType(argument.type());
            if (type == null) {
                LOG.warn("Argument '" + argument.name() + "' of /" + command.name()
                        + " uses " + argument.type() + ", which this bridge cannot express;"
                        + " the command was not registered");
                return null;
            }
            nodes[i] = forName(REQUIRED_BUILDER)
                    .getMethod("argument", String.class, forName(
                            "com.mojang.brigadier.arguments.ArgumentType"))
                    .invoke(null, argument.name(), type);
        }
        // Build the chain from the tail so each node can be attached to its parent.
        for (int i = nodes.length - 1; i >= 0; i--) {
            if (i == nodes.length - 1 || !arguments.get(i + 1).isRequired()) {
                nodes[i] = executes(nodes[i], command, executor);
            }
            if (i > 0) {
                nodes[i - 1] = then(nodes[i - 1], nodes[i]);
            }
        }
        return nodes[0];
    }

    private void registerAlias(Object dispatcher, String alias, Object target,
                               CommandInvoker executor) {
        try {
            Object builder = forName(LITERAL_BUILDER)
                    .getMethod("literal", String.class).invoke(null, alias);
            Method redirect = findMethodAccepting(forName(ARGUMENT_BUILDER), "redirect", target);
            if (redirect != null) {
                builder = redirect.invoke(builder, target);
            }
            dispatcher.getClass().getMethod("register", forName(LITERAL_BUILDER))
                    .invoke(dispatcher, builder);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.debug(() -> "Could not register the alias /" + alias + ": " + e);
        }
    }

    /** Attaches a {@code Command} implementation, built as a dynamic proxy. */
    private Object executes(Object builder, CommandSpec command, CommandInvoker executor)
            throws ReflectiveOperationException {
        Class<?> commandInterface = forName(COMMAND);
        Object proxy = Proxy.newProxyInstance(loader, new Class<?>[]{commandInterface},
                new CommandProxy(command, executor));
        Method executes = findMethodAccepting(forName(ARGUMENT_BUILDER), "executes", proxy);
        if (executes == null) {
            throw new NoSuchMethodException("ArgumentBuilder.executes");
        }
        return executes.invoke(builder, proxy);
    }

    private Object then(Object builder, Object child) throws ReflectiveOperationException {
        // ArgumentBuilder declares both then(ArgumentBuilder) and
        // then(CommandNode). Matching on name and arity alone picks whichever
        // the JVM lists first, and passing a builder to the CommandNode
        // overload fails at invoke time -- so the overload has to be chosen by
        // what it actually accepts.
        Method then = findMethodAccepting(forName(ARGUMENT_BUILDER), "then", child);
        if (then == null) {
            throw new NoSuchMethodException("ArgumentBuilder.then(" + child.getClass() + ")");
        }
        return then.invoke(builder, child);
    }

    /**
     * Applies a permission requirement.
     *
     * <p>The predicate receives Minecraft's own command source, whose
     * {@code hasPermission(int)} method is obfuscated. Rather than guess at it,
     * this asks by shape: the source's only single-int-argument method
     * returning boolean is the permission check on every version ReMod
     * targets.</p>
     */
    private Object requirePermission(Object builder, int level)
            throws ReflectiveOperationException {
        Object predicate = Proxy.newProxyInstance(loader,
                new Class<?>[]{java.util.function.Predicate.class},
                (proxy, method, args) -> {
                    if (!"test".equals(method.getName()) || args == null || args.length != 1) {
                        return defaultFor(method);
                    }
                    return hasPermission(args[0], level);
                });
        Method requires = findMethodAccepting(forName(ARGUMENT_BUILDER), "requires", predicate);
        if (requires == null) {
            return builder;
        }
        return requires.invoke(builder, predicate);
    }

    private static boolean hasPermission(Object source, int level) {
        if (source == null) {
            return false;
        }
        for (Method method : source.getClass().getMethods()) {
            if (method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == int.class
                    && (method.getReturnType() == boolean.class
                        || method.getReturnType() == Boolean.class)) {
                try {
                    return Boolean.TRUE.equals(method.invoke(source, level));
                } catch (ReflectiveOperationException | RuntimeException e) {
                    // Try the next candidate rather than denying outright.
                }
            }
        }
        // No recognisable check: allow, because a command that silently refuses
        // everyone is harder to diagnose than one that ran when it should not.
        LOG.debug(() -> "No permission check found on " + source.getClass().getName()
                + "; allowing");
        return true;
    }

    /** Maps a ReMod argument type onto Brigadier's. */
    private Object brigadierType(ArgumentType type) throws ReflectiveOperationException {
        switch (type) {
            case STRING:
                return forName(STRING_TYPE).getMethod("string").invoke(null);
            case GREEDY_STRING:
                return forName(STRING_TYPE).getMethod("greedyString").invoke(null);
            case INTEGER:
                return forName(INTEGER_TYPE).getMethod("integer").invoke(null);
            case DOUBLE:
                return forName(DOUBLE_TYPE).getMethod("doubleArg").invoke(null);
            case BOOLEAN:
                return forName(BOOLEAN_TYPE).getMethod("bool").invoke(null);
            case PLAYER:
                // Minecraft's own player argument is obfuscated; a plain word
                // parses the same input and is resolved by name at execution.
                return forName(STRING_TYPE).getMethod("word").invoke(null);
            default:
                return null;
        }
    }

    /** Invoked when a registered command runs. */
    @FunctionalInterface
    public interface CommandInvoker {

        /**
         * @param command          the spec that was registered
         * @param brigadierContext Brigadier's own {@code CommandContext}
         * @return the command's result count
         */
        int invoke(CommandSpec command, Object brigadierContext);
    }

    /** Bridges Brigadier's {@code Command} interface onto a {@link CommandInvoker}. */
    private static final class CommandProxy implements InvocationHandler {

        private final CommandSpec command;
        private final CommandInvoker executor;

        CommandProxy(CommandSpec command, CommandInvoker executor) {
            this.command = command;
            this.executor = executor;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (!"run".equals(method.getName()) || args == null || args.length != 1) {
                return defaultFor(method);
            }
            try {
                return executor.invoke(command, args[0]);
            } catch (RuntimeException e) {
                LOG.error("/" + command.name() + " threw; reporting failure to the caller", e);
                return 0;
            }
        }
    }

    private static Object defaultFor(Method method) {
        Class<?> type = method.getReturnType();
        if (type == void.class) {
            return null;
        }
        if (type.isPrimitive()) {
            return type == boolean.class ? Boolean.FALSE : Array.get(Array.newInstance(type, 1), 0);
        }
        return null;
    }

    private Optional<Class<?>> findClass(String name) {
        try {
            return Optional.of(Class.forName(name, false, loader));
        } catch (ClassNotFoundException | LinkageError e) {
            return Optional.empty();
        }
    }

    private Class<?> forName(String name) throws ClassNotFoundException {
        return Class.forName(name, false, loader);
    }

    /**
     * Finds the single-argument overload of {@code name} that accepts
     * {@code argument}.
     *
     * <p>Choosing by name and arity is not enough on Brigadier's
     * {@code ArgumentBuilder}, which overloads {@code then} for both builders
     * and finished nodes.</p>
     */
    private static Method findMethodAccepting(Class<?> type, String name, Object argument) {
        Method fallback = null;
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != 1) {
                continue;
            }
            Class<?> parameter = method.getParameterTypes()[0];
            if (argument != null && parameter.isInstance(argument)) {
                return method;
            }
            // A proxy implements the interface it was created for, so an
            // interface parameter is the right home for one even when
            // isInstance cannot see through the proxy's own class.
            if (fallback == null && parameter.isInterface()) {
                fallback = method;
            }
        }
        return fallback;
    }
}
