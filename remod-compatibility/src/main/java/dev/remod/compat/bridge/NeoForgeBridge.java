package dev.remod.compat.bridge;

import dev.remod.compat.CompatibilityLevel;
import dev.remod.compat.LoaderPlatform;

import java.util.List;

/**
 * NeoForge.
 *
 * <p>A fork of Forge with the same launch architecture, and therefore the same
 * answer: coexistence yes, mod loading no.</p>
 */
public final class NeoForgeBridge extends AbstractModLoaderBridge {

    public NeoForgeBridge() {
        super(LoaderPlatform.NEOFORGE, "mods", "neoforge");
    }

    @Override
    public CompatibilityLevel level() {
        return CompatibilityLevel.COEXISTENCE;
    }

    @Override
    public List<String> coexistenceNotes() {
        return List.of(
                "NeoForge installs its own launcher profile; ReMod's is separate.",
                "NeoForge mods live in .minecraft/mods; ReMod mods live in"
                        + " .minecraft/remod/mods.",
                "NeoForge and Forge mods are not interchangeable with each other either, and"
                        + " neither works with ReMod.");
    }

    @Override
    public String whyNotLoadable() {
        return "NeoForge inherits Forge's ModLauncher-based startup, including bytecode"
                + " transformation before the game loads. ReMod has no transformation layer,"
                + " so NeoForge mods cannot attach to it.";
    }
}
