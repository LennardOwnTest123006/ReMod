package dev.remod.examples.fly;

import dev.remod.api.game.GameMode;
import dev.remod.api.game.Identifier;
import dev.remod.api.game.PlayerHandle;
import dev.remod.api.game.ServerHandle;
import dev.remod.api.game.Text;
import dev.remod.api.game.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** A player whose state tests can set and inspect. */
final class FakePlayer implements PlayerHandle {

    private final UUID uuid = UUID.randomUUID();
    private final String name;
    private int permissionLevel;
    private GameMode gameMode = GameMode.SURVIVAL;
    private boolean flightAllowed;
    private boolean flying;
    private float flightSpeed = FlyRules.VANILLA_SPEED;
    private final List<String> messages = new ArrayList<>();
    private final List<String> actionBars = new ArrayList<>();

    FakePlayer(String name) {
        this.name = name;
    }

    FakePlayer permissionLevel(int value) {
        this.permissionLevel = value;
        return this;
    }

    FakePlayer gameMode(GameMode value) {
        this.gameMode = value;
        return this;
    }

    /** Puts the player in the air, as the game would once flight is allowed. */
    FakePlayer airborne() {
        this.flying = true;
        return this;
    }

    List<String> messages() {
        return messages;
    }

    List<String> actionBars() {
        return actionBars;
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
        messages.add(message.plainText());
    }

    @Override
    public void sendActionBar(Text message) {
        actionBars.add(message.plainText());
    }

    @Override
    public Vec3 position() {
        return Vec3.ZERO;
    }

    @Override
    public Identifier dimension() {
        return Identifier.parse("minecraft:overworld");
    }

    @Override
    public int permissionLevel() {
        return permissionLevel;
    }

    @Override
    public boolean isOnline() {
        return true;
    }

    @Override
    public GameMode gameMode() {
        return gameMode;
    }

    @Override
    public boolean isFlightAllowed() {
        return flightAllowed || gameMode.grantsFlight();
    }

    @Override
    public void setFlightAllowed(boolean allowed) {
        this.flightAllowed = allowed;
        if (!allowed) {
            // The game drops a player whose flight is withdrawn.
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

    /** A server the tests can declare dedicated or integrated. */
    static final class FakeServer implements ServerHandle {

        private final boolean dedicated;
        private final List<PlayerHandle> players = new ArrayList<>();

        FakeServer(boolean dedicated) {
            this.dedicated = dedicated;
        }

        /** The integrated server single-player runs -- "your own world". */
        static FakeServer singlePlayer() {
            return new FakeServer(false);
        }

        static FakeServer dedicated() {
            return new FakeServer(true);
        }

        FakeServer with(PlayerHandle player) {
            players.add(player);
            return this;
        }

        @Override
        public List<PlayerHandle> players() {
            return players;
        }

        @Override
        public Optional<PlayerHandle> player(UUID uuid) {
            return players.stream().filter(p -> p.uuid().equals(uuid)).findFirst();
        }

        @Override
        public Optional<PlayerHandle> player(String name) {
            return players.stream().filter(p -> p.name().equals(name)).findFirst();
        }

        @Override
        public List<dev.remod.api.game.WorldHandle> worlds() {
            return List.of();
        }

        @Override
        public void broadcast(Text message) {
            players.forEach(p -> p.sendMessage(message));
        }

        @Override
        public boolean isDedicated() {
            return dedicated;
        }

        @Override
        public void execute(Runnable task) {
            task.run();
        }
    }
}
