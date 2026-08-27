package dev.remod.loader.sim;

import dev.remod.api.game.PlayerHandle;
import dev.remod.api.game.ServerHandle;
import dev.remod.api.game.Text;
import dev.remod.api.game.WorldHandle;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A working single-player-style server, in memory.
 *
 * <p>Reports {@link #isDedicated()} as false, so a mod's "only in my own world"
 * rule -- like the fly mod's -- takes the single-player path exactly as it
 * would in a real single-player world.</p>
 */
public final class SimulatedServer implements ServerHandle {

    private final List<PlayerHandle> players = new ArrayList<>();
    private final boolean dedicated;
    private final List<String> broadcasts = new ArrayList<>();

    public SimulatedServer(boolean dedicated) {
        this.dedicated = dedicated;
    }

    /** The integrated server single-player runs: "your own world". */
    public static SimulatedServer singlePlayer(PlayerHandle owner) {
        SimulatedServer server = new SimulatedServer(false);
        server.players.add(owner);
        return server;
    }

    public SimulatedServer with(PlayerHandle player) {
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
        return players.stream().filter(p -> p.name().equalsIgnoreCase(name)).findFirst();
    }

    @Override
    public List<WorldHandle> worlds() {
        return List.of();
    }

    @Override
    public void broadcast(Text message) {
        broadcasts.add(message.plainText());
        players.forEach(player -> player.sendMessage(message));
    }

    @Override
    public boolean isDedicated() {
        return dedicated;
    }

    @Override
    public void execute(Runnable task) {
        task.run();
    }

    /** Everything broadcast to the whole server, in order. */
    public List<String> broadcasts() {
        return broadcasts;
    }
}
