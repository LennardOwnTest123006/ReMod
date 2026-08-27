package dev.remod.compat.bridge;

import dev.remod.compat.CompatibilityLevel;
import dev.remod.compat.LoaderPlatform;

import java.util.List;

/**
 * Fabric.
 *
 * <p><b>Coexistence: yes.</b> Fabric installs its own launcher profile
 * ({@code fabric-loader-<version>-<mc>}) and reads {@code .minecraft/mods}.
 * ReMod installs a separate profile and reads {@code .minecraft/remod/mods}, so
 * the two never touch each other's files and a user can switch between them in
 * the launcher's installation list.</p>
 *
 * <p><b>Loading Fabric mods: no.</b> A Fabric mod is compiled against
 * intermediary mappings and expects Fabric Loader's entrypoint container, its
 * Mixin bootstrap and its own {@code net.fabricmc.api} classes to be present.
 * Providing those means either shipping Fabric Loader (a different project's
 * code, with its own licence and release cycle) or reimplementing it. ReMod
 * does neither, and does not pretend to.</p>
 */
public final class FabricBridge extends AbstractModLoaderBridge {

    public FabricBridge() {
        super(LoaderPlatform.FABRIC, "mods", "fabric");
    }

    @Override
    public CompatibilityLevel level() {
        return CompatibilityLevel.COEXISTENCE;
    }

    @Override
    public List<String> coexistenceNotes() {
        return List.of(
                "Fabric and ReMod install separate launcher profiles, so both can stay"
                        + " installed and you pick one per launch.",
                "Fabric mods live in .minecraft/mods; ReMod mods live in"
                        + " .minecraft/remod/mods. Keep them apart.",
                "Running Fabric and ReMod in the same launch is not supported: both install"
                        + " their own main class, and only one can run.");
    }

    @Override
    public String whyNotLoadable() {
        return "Fabric mods are compiled against intermediary mappings and require Fabric"
                + " Loader's entrypoint and Mixin infrastructure at runtime. ReMod would have"
                + " to bundle or reimplement Fabric Loader to run them, which it does not do.";
    }
}
