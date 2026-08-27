package dev.remod.adapter.reflect;

import dev.remod.common.log.ReModLog;
import dev.remod.common.log.ReModLogger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Careful reflection against Minecraft's own classes.
 *
 * <p>ReMod deliberately does not bytecode-patch Minecraft. That keeps the
 * vanilla jar untouched -- nothing is redistributed, nothing is rewritten on
 * disk, and an uninstall is just a deleted folder -- but it means the only way
 * to reach the game's internals is reflection.</p>
 *
 * <p><b>The honest caveat.</b> Minecraft ships obfuscated. Class and field
 * names like {@code net.minecraft.core.registries.BuiltInRegistries} exist only
 * when the game is running against Mojang's published <em>official
 * mappings</em> -- which is the case in a development environment, and is not
 * the case for a stock launcher install. Every probe here therefore reports
 * honestly whether it found what it was looking for, and the bridge above
 * degrades to "mods load, nothing binds" rather than pretending.</p>
 *
 * <p>Every lookup is cached, because a failed {@code Class.forName} is not
 * cheap and the bridge asks the same questions repeatedly.</p>
 */
public final class MinecraftReflection {

    private static final ReModLogger LOG = ReModLog.get("ReMod/Reflect");

    private final ClassLoader loader;
    private final Map<String, Optional<Class<?>>> classCache = new LinkedHashMap<>();

    public MinecraftReflection(ClassLoader loader) {
        this.loader = loader == null ? MinecraftReflection.class.getClassLoader() : loader;
    }

    /** Looks a class up by name, caching both hits and misses. */
    public synchronized Optional<Class<?>> findClass(String name) {
        return classCache.computeIfAbsent(name, key -> {
            try {
                return Optional.of(Class.forName(key, false, loader));
            } catch (ClassNotFoundException | LinkageError e) {
                LOG.trace(() -> "Minecraft class " + key + " is not present ("
                        + e.getClass().getSimpleName() + ")");
                return Optional.empty();
            }
        });
    }

    /** True when every named class is present. */
    public boolean hasAll(String... names) {
        for (String name : names) {
            if (findClass(name).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Finds a public static field, making it accessible. */
    public Optional<Field> findStaticField(String className, String fieldName) {
        return findClass(className).flatMap(type -> {
            try {
                Field field = type.getField(fieldName);
                field.setAccessible(true);
                return Optional.of(field);
            } catch (NoSuchFieldException | RuntimeException e) {
                LOG.trace(() -> className + " has no accessible static field " + fieldName);
                return Optional.empty();
            }
        });
    }

    /** Reads a public static field's value. */
    public Optional<Object> readStaticField(String className, String fieldName) {
        return findStaticField(className, fieldName).flatMap(field -> {
            try {
                return Optional.ofNullable(field.get(null));
            } catch (IllegalAccessException | RuntimeException e) {
                LOG.trace(() -> "Could not read " + className + "." + fieldName + ": " + e);
                return Optional.empty();
            }
        });
    }

    /** Finds a method by name and parameter types. */
    public Optional<Method> findMethod(String className, String methodName,
                                       Class<?>... parameterTypes) {
        return findClass(className).flatMap(type -> {
            try {
                Method method = type.getMethod(methodName, parameterTypes);
                method.setAccessible(true);
                return Optional.of(method);
            } catch (NoSuchMethodException | RuntimeException e) {
                LOG.trace(() -> className + " has no accessible method " + methodName);
                return Optional.empty();
            }
        });
    }

    /**
     * Finds a static method by name and arity, when the parameter types cannot
     * be named because they are Minecraft's own.
     */
    public Optional<Method> findStaticMethod(String className, String methodName, int arity) {
        return findClass(className).flatMap(type -> {
            for (Method method : type.getMethods()) {
                if (method.getName().equals(methodName)
                        && method.getParameterCount() == arity
                        && java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                    method.setAccessible(true);
                    return Optional.of(method);
                }
            }
            LOG.trace(() -> className + " has no static " + methodName + " taking " + arity
                    + " argument(s)");
            return Optional.empty();
        });
    }

    /** Invokes a method, converting any failure into an empty result plus a trace line. */
    public Optional<Object> invoke(Method method, Object receiver, Object... arguments) {
        try {
            return Optional.ofNullable(method.invoke(receiver, arguments));
        } catch (ReflectiveOperationException | RuntimeException e) {
            Throwable cause = e instanceof java.lang.reflect.InvocationTargetException
                    && e.getCause() != null ? e.getCause() : e;
            LOG.debug(() -> "Calling " + method.getDeclaringClass().getSimpleName() + "."
                    + method.getName() + " failed: " + cause);
            return Optional.empty();
        }
    }

    /** The class loader this instance reflects against. */
    public ClassLoader loader() {
        return loader;
    }
}
