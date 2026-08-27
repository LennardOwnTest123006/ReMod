package dev.remod.loader.sim;

import dev.remod.api.game.GameMode;
import dev.remod.api.game.Identifier;
import dev.remod.api.game.PlayerHandle;
import dev.remod.api.game.Text;
import dev.remod.api.game.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A fully working player, in memory.
 *
 * <p>This is not a stub that records calls and does nothing -- it is a real
 * implementation of {@link PlayerHandle} with genuine state. Setting flight
 * actually changes {@link #isFlightAllowed()}; sending a message actually adds
 * to {@link #inbox()}. That is what lets {@code remod play} run a mod's real
 * code and show a real result without a copy of Minecraft: the mod cannot tell
 * this player from a game one, because every method does what it says.</p>
 */
public final class SimulatedPlayer implements PlayerHandle {

    private final UUID uuid;
    private final String name;
    private int permissionLevel;
    private GameMode gameMode;
    private Vec3 position = new Vec3(0, 64, 0);
    private Identifier dimension = Identifier.parse("minecraft:overworld");
    private boolean flightAllowed;
    private boolean flying;
    private float flightSpeed = 0.05f;
    private boolean online = true;
    private final List<String> inbox = new ArrayList<>();
    private final List<String> actionBar = new ArrayList<>();

    public SimulatedPlayer(String name, int permissionLevel, GameMode gameMode) {
        this.uuid = UUID.nameUUIDFromBytes(("SimulatedPlayer:" + name)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        this.name = name;
        this.permissionLevel = permissionLevel;
        this.gameMode = gameMode == null ? GameMode.SURVIVAL : gameMode;
    }

    /** A single-player-style operator in survival, which is the usual case. */
    public static SimulatedPlayer singlePlayerOwner(String name) {
        return new SimulatedPlayer(name, 4, GameMode.SURVIVAL);
    }

    @Override
    public UUID uuid() {
        return uuid;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void sendMessage(Text message) {
        inbox.add(message.plainText());
    }

    @Override
    public void sendActionBar(Text message) {
        actionBar.add(message.plainText());
    }

    @Override
    public Vec3 position() {
        return position;
    }

    public void setPosition(Vec3 value) {
        this.position = value;
    }

    @Override
    public Identifier dimension() {
        return dimension;
    }

    @Override
    public int permissionLevel() {
        return permissionLevel;
    }

    public void setPermissionLevel(int value) {
        this.permissionLevel = value;
    }

    @Override
    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean value) {
        this.online = value;
    }

    @Override
    public GameMode gameMode() {
        return gameMode;
    }

    public void setGameMode(GameMode value) {
        this.gameMode = value == null ? GameMode.SURVIVAL : value;
    }

    @Override
    public boolean isFlightAllowed() {
        return flightAllowed || gameMode.grantsFlight();
    }

    @Override
    public void setFlightAllowed(boolean allowed) {
        this.flightAllowed = allowed;
        if (!allowed) {
            // A real game drops a player whose flight is withdrawn.
            this.flying = false;
        }
    }

    @Override
    public boolean isFlying() {
        return flying;
    }

    @Override
    public void setFlying(boolean value) {
        this.flying = value && isFlightAllowed();
    }

    @Override
    public float flightSpeed() {
        return flightSpeed;
    }

    @Override
    public void setFlightSpeed(float speed) {
        this.flightSpeed = speed;
    }

    /** Every chat message this player has been sent, in order. */
    public List<String> inbox() {
        return inbox;
    }

    /** Every action-bar message this player has been shown. */
    public List<String> actionBar() {
        return actionBar;
    }

    /** The most recent chat message, or empty when none. */
    public String lastMessage() {
        return inbox.isEmpty() ? "" : inbox.get(inbox.size() - 1);
    }
}
