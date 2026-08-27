package dev.remod.loader.runtime;

import dev.remod.api.Side;
import dev.remod.api.game.GameInfo;
import dev.remod.api.service.GameBridge;
import dev.remod.common.version.MinecraftVersions;

import java.util.function.Supplier;

/** {@link GameInfo} backed by the installed {@link GameBridge}. */
public final class DefaultGameInfo implements GameInfo {

    private final String minecraftVersion;
    private final Side side;
    private final Supplier<GameBridge> bridge;

    public DefaultGameInfo(String minecraftVersion, Side side, Supplier<GameBridge> bridge) {
        this.minecraftVersion = minecraftVersion;
        this.side = side;
        this.bridge = bridge;
    }

    @Override
    public String minecraftVersion() {
        return minecraftVersion;
    }

    @Override
    public String minecraftSeries() {
        return MinecraftVersions.series(minecraftVersion);
    }

    @Override
    public Side side() {
        return side;
    }

    @Override
    public boolean isGameAttached() {
        GameBridge active = bridge.get();
        return active != null && active.isGameAttached();
    }

    @Override
    public String adapterId() {
        GameBridge active = bridge.get();
        return active == null ? "none" : active.id();
    }
}
