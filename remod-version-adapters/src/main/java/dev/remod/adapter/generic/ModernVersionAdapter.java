package dev.remod.adapter.generic;

import dev.remod.adapter.VersionSupportTable;
import dev.remod.api.Side;
import dev.remod.api.service.GameBridge;
import dev.remod.common.version.MinecraftVersions;
import dev.remod.loader.adapter.MinecraftVersionAdapter;

/**
 * The adapter for modern Minecraft: 1.17 and newer.
 *
 * <p>1.17 is the boundary where Minecraft moved to Java 17, adopted the modern
 * {@code net.minecraft.client.main.Main} entry point and started shipping the
 * bundled library list that ReMod's generated version JSON inherits. Everything
 * from there up shares one launch shape, which is why one adapter covers the
 * whole range rather than one per release.</p>
 *
 * <p>Where a future Minecraft release breaks that shape, the fix is a second
 * adapter with a higher {@link Support} score for the affected versions -- not
 * a change to this one, and not a change to the loader or the API.</p>
 */
public final class ModernVersionAdapter implements MinecraftVersionAdapter {

    public static final String ID = "remod:modern";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "ReMod Modern Adapter (Minecraft " + VersionSupportTable.OLDEST_SUPPORTED + "+)";
    }

    @Override
    public Support supportFor(String minecraftVersion) {
        return VersionSupportTable.supportFor(minecraftVersion);
    }

    @Override
    public GameBridge createBridge(String minecraftVersion, Side side,
                                   ClassLoader gameClassLoader) {
        String series = MinecraftVersions.series(minecraftVersion);
        String adapterId = series == null ? ID : ID + "-" + series;
        return new ModernGameBridge(adapterId, minecraftVersion, side, gameClassLoader);
    }
}
