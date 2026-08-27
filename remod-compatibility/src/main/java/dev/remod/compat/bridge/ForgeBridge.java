package dev.remod.compat.bridge;

import dev.remod.compat.CompatibilityLevel;
import dev.remod.compat.LoaderPlatform;

import java.util.List;

/**
 * Minecraft Forge.
 *
 * <p><b>Coexistence: yes</b>, on the same terms as Fabric -- its own launcher
 * profile, its own {@code mods} folder.</p>
 *
 * <p><b>Loading Forge mods: no.</b> Forge mods are compiled against SRG or
 * official mappings and run inside ModLauncher, which applies Forge's own
 * bytecode transformations and Access Transformers before the game starts. A
 * Forge mod's {@code @Mod} class and event bus have no meaning outside that
 * environment. ReMod runs as a plain launch wrapper with no transformation
 * layer, so there is nothing for those mods to attach to.</p>
 */
public final class ForgeBridge extends AbstractModLoaderBridge {

    public ForgeBridge() {
        super(LoaderPlatform.FORGE, "mods", "forge");
    }

    @Override
    public CompatibilityLevel level() {
        return CompatibilityLevel.COEXISTENCE;
    }

    @Override
    public List<String> coexistenceNotes() {
        return List.of(
                "Forge installs its own launcher profile; ReMod's is separate and both can"
                        + " stay installed.",
                "Forge mods live in .minecraft/mods; ReMod mods live in"
                        + " .minecraft/remod/mods.",
                "Forge and ReMod cannot run in the same launch: both replace the game's main"
                        + " class.");
    }

    @Override
    public String whyNotLoadable() {
        return "Forge mods run inside ModLauncher, which applies Forge's bytecode"
                + " transformations and Access Transformers before Minecraft starts. ReMod is a"
                + " launch wrapper with no transformation layer, so a Forge mod has nothing to"
                + " attach to.";
    }
}
