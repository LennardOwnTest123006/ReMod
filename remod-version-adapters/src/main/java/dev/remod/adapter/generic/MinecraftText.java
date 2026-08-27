package dev.remod.adapter.generic;

import dev.remod.api.game.Text;
import dev.remod.common.log.ReModLog;
import dev.remod.common.log.ReModLogger;

import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * Sends ReMod {@link Text} to a Minecraft command source.
 *
 * <p>Minecraft's command source exposes {@code sendSuccess} and
 * {@code sendFailure}, both obfuscated, both taking the game's own
 * {@code Component} type -- which is also obfuscated. Rather than guess at
 * either, this identifies them by shape:</p>
 *
 * <ul>
 *   <li>{@code sendFailure} is the source's only single-argument {@code void}
 *       method taking a non-primitive, non-{@code String} type;</li>
 *   <li>{@code sendSuccess} takes that same type plus a {@code boolean}.</li>
 * </ul>
 *
 * <p>The {@code Component} itself is built through
 * {@code Component.literal(String)} -- located, in turn, as the static method
 * on the argument's own type that takes a {@link String} and returns that
 * type.</p>
 *
 * <p>All of this is best-effort. Every failure degrades to a logged debug line
 * rather than an exception, because a message that does not arrive is a far
 * smaller problem than a command that crashes the server.</p>
 */
final class MinecraftText {

    private static final ReModLogger LOG = ReModLog.get("ReMod/Commands");

    private MinecraftText() {
    }

    /** Sends a normal reply. Falls back to a failure message when unavailable. */
    static void sendSuccess(Object source, Text message) {
        if (source == null || message == null) {
            return;
        }
        Method sendFailure = findSendFailure(source);
        if (sendFailure == null) {
            LOG.debug(() -> "No message method found on " + source.getClass().getName());
            return;
        }
        Class<?> componentType = sendFailure.getParameterTypes()[0];
        Object component = buildComponent(componentType, message.plainText());
        if (component == null) {
            return;
        }
        Method sendSuccess = findSendSuccess(source, componentType);
        try {
            if (sendSuccess != null) {
                // The boolean is "broadcast to operators"; false keeps a mod's
                // reply between the command and its caller.
                sendSuccess.invoke(source, wrapSupplier(sendSuccess, component), false);
                return;
            }
            sendFailure.invoke(source, component);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.debug(() -> "Could not send a command reply: " + e);
        }
    }

    /** Sends a failure reply, which Minecraft renders in red. */
    static void sendFailure(Object source, Text message) {
        if (source == null || message == null) {
            return;
        }
        Method sendFailure = findSendFailure(source);
        if (sendFailure == null) {
            return;
        }
        Object component = buildComponent(sendFailure.getParameterTypes()[0],
                message.plainText());
        if (component == null) {
            return;
        }
        try {
            sendFailure.invoke(source, component);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.debug(() -> "Could not send a command failure: " + e);
        }
    }

    /**
     * Newer Minecraft takes a {@code Supplier<Component>} for the success
     * message; older versions take the component directly.
     */
    private static Object wrapSupplier(Method sendSuccess, Object component) {
        Class<?> first = sendSuccess.getParameterTypes()[0];
        if (Supplier.class.isAssignableFrom(first)) {
            return (Supplier<Object>) () -> component;
        }
        return component;
    }

    /** The single-argument void method that takes a component. */
    private static Method findSendFailure(Object source) {
        for (Method method : source.getClass().getMethods()) {
            if (method.getParameterCount() != 1
                    || method.getReturnType() != void.class) {
                continue;
            }
            Class<?> parameter = method.getParameterTypes()[0];
            if (parameter.isPrimitive() || parameter == String.class
                    || parameter == Object.class) {
                continue;
            }
            return method;
        }
        return null;
    }

    /** The two-argument method taking the same component type plus a boolean. */
    private static Method findSendSuccess(Object source, Class<?> componentType) {
        for (Method method : source.getClass().getMethods()) {
            if (method.getParameterCount() != 2 || method.getReturnType() != void.class) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters[1] != boolean.class) {
                continue;
            }
            if (parameters[0] == componentType
                    || Supplier.class.isAssignableFrom(parameters[0])) {
                return method;
            }
        }
        return null;
    }

    /** Builds a Minecraft text component from plain text. */
    private static Object buildComponent(Class<?> componentType, String text) {
        for (Method method : componentType.getMethods()) {
            if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())
                    || method.getParameterCount() != 1
                    || method.getParameterTypes()[0] != String.class) {
                continue;
            }
            if (!componentType.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            try {
                Object component = method.invoke(null, text);
                if (component != null) {
                    return component;
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                // Try the next candidate.
            }
        }
        LOG.debug(() -> "No literal(String) factory found on " + componentType.getName());
        return null;
    }
}
