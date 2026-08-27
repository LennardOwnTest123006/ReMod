package dev.remod.compat.bridge;

import dev.remod.compat.CompatibilityLevel;
import dev.remod.compat.LoaderPlatform;

import java.util.List;

/**
 * Quilt.
 *
 * <p>A fork of Fabric, so the situation is identical: separate launcher
 * profile, separate mods folder, safe to have both installed, and its mods are
 * not loadable by ReMod for the same reason Fabric's are not.</p>
 */
public final class QuiltBridge extends AbstractModLoaderBridge {

    public QuiltBridge() {
        super(LoaderPlatform.QUILT, "mods", "quilt");
    }

    @Override
    public CompatibilityLevel level() {
        return CompatibilityLevel.COEXISTENCE;
    }

    @Override
    public List<String> coexistenceNotes() {
        return List.of(
                "Quilt installs its own launcher profile alongside ReMod's; both can stay"
                        + " installed.",
                "Quilt mods live in .minecraft/mods; ReMod mods live in"
                        + " .minecraft/remod/mods.",
                "Quilt can load most Fabric mods. ReMod can load neither.");
    }

    @Override
    public String whyNotLoadable() {
        return "Quilt mods target Quilt Loader's entrypoint model and intermediary mappings,"
                + " and most also depend on Mixin. Loading them would mean bundling Quilt"
                + " Loader, which ReMod does not do.";
    }
}
