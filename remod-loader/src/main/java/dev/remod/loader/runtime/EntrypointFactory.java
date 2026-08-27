package dev.remod.loader.runtime;

import dev.remod.api.ReModMod;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

/**
 * Turns an entrypoint class name into a {@link ReModMod} instance.
 *
 * <p>Each failure mode gets its own message. "ClassNotFoundException" tells a
 * mod author nothing; "your manifest names dev.example.Main but the jar does
 * not contain that class -- check the package" tells them exactly what to
 * fix.</p>
 */
public final class EntrypointFactory {

    private EntrypointFactory() {
    }

    public static ReModMod instantiate(String className, ClassLoader loader, String modId)
            throws ModInstantiationException {
        Class<?> type;
        try {
            type = Class.forName(className, false, loader);
        } catch (ClassNotFoundException e) {
            throw new ModInstantiationException(
                    "Mod '" + modId + "' names the entrypoint '" + className
                            + "' but no such class is in its jar",
                    "Check the entrypoints field in remod.mod.json matches the class's full"
                            + " package name, and that the class was included in the build.", e);
        } catch (LinkageError e) {
            throw new ModInstantiationException(
                    "Mod '" + modId + "' entrypoint '" + className + "' could not be linked: "
                            + e.getMessage(),
                    "This usually means the mod was built against a different ReMod API or a"
                            + " different Java version. Rebuild it against ReMod API "
                            + "for your Minecraft version.", e);
        }

        if (!ReModMod.class.isAssignableFrom(type)) {
            throw new ModInstantiationException(
                    "Mod '" + modId + "' entrypoint '" + className
                            + "' does not implement dev.remod.api.ReModMod",
                    "Add 'implements ReModMod' to the class, or point the entrypoints field at"
                            + " the class that does.", null);
        }
        if (Modifier.isAbstract(type.getModifiers())) {
            throw new ModInstantiationException(
                    "Mod '" + modId + "' entrypoint '" + className + "' is abstract",
                    "Name a concrete class in the entrypoints field.", null);
        }
        if (type.getEnclosingClass() != null && !Modifier.isStatic(type.getModifiers())) {
            throw new ModInstantiationException(
                    "Mod '" + modId + "' entrypoint '" + className
                            + "' is a non-static inner class, which cannot be constructed on"
                            + " its own",
                    "Make the class static, or move it to its own file.", null);
        }

        Constructor<?> constructor;
        try {
            constructor = type.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            throw new ModInstantiationException(
                    "Mod '" + modId + "' entrypoint '" + className
                            + "' has no no-argument constructor",
                    "ReMod constructs entrypoints itself. Add a public no-argument"
                            + " constructor and do your setup in onInitialize instead.", e);
        }
        if (!Modifier.isPublic(constructor.getModifiers())
                || !Modifier.isPublic(type.getModifiers())) {
            throw new ModInstantiationException(
                    "Mod '" + modId + "' entrypoint '" + className
                            + "' or its constructor is not public",
                    "Make both the class and its no-argument constructor public.", null);
        }

        try {
            return (ReModMod) constructor.newInstance();
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new ModInstantiationException(
                    "Mod '" + modId + "' entrypoint '" + className
                            + "' threw from its constructor: " + cause,
                    "Move work out of the constructor into onInitialize, where ReMod can"
                            + " report failures properly and the game is further along.", cause);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new ModInstantiationException(
                    "Mod '" + modId + "' entrypoint '" + className
                            + "' could not be constructed: " + e,
                    "Check the class is public with a public no-argument constructor.", e);
        }
    }
}
