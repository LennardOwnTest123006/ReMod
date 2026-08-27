package dev.remod.transform.hook;

import java.util.ArrayList;
import java.util.List;

/**
 * Stands in for {@code com.mojang.brigadier.CommandDispatcher} in tests.
 *
 * <p>The real type is not a dependency of this module: the transformer matches
 * on a field descriptor string rather than a compiled type, which is exactly
 * what lets it work against an obfuscated jar. Tests supply this instead and
 * configure the transformer with its descriptor.</p>
 *
 * <p>Top-level and public because the stand-in game classes the tests compile
 * have to import it.</p>
 */
public final class FakeDispatcher {

    /** The descriptor tests hand to the transformer. */
    public static final String DESCRIPTOR = "Ldev/remod/transform/hook/FakeDispatcher;";

    private final List<String> registered = new ArrayList<>();

    public void register(String literal) {
        registered.add(literal);
    }

    /** The literals registered so far, in order. */
    public List<String> registered() {
        return registered;
    }
}
