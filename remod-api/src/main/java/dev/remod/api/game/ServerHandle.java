package dev.remod.api.game;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The running server -- the dedicated server, or the integrated one in single-player. */
public interface ServerHandle {

    /** Every connected player. */
    List<PlayerHandle> players();

    Optional<PlayerHandle> player(UUID uuid);

    Optional<PlayerHandle> player(String name);

    /** Every loaded world. */
    List<WorldHandle> worlds();

    /** Sends a message to every connected player. */
    void broadcast(Text message);

    /** True for a dedicated server, false for the integrated single-player server. */
    boolean isDedicated();

    /** Runs {@code task} on the server thread. Safe to call from any thread. */
    void execute(Runnable task);
}
