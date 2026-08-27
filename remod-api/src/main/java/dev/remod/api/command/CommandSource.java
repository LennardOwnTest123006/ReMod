package dev.remod.api.command;

import dev.remod.api.game.PlayerHandle;
import dev.remod.api.game.ServerHandle;
import dev.remod.api.game.Text;
import dev.remod.api.game.Vec3;

import java.util.Optional;

/** Who ran a command: a player, the server console, or a command block. */
public interface CommandSource {

    /** The display name of the caller, e.g. a player name or {@code Server}. */
    String name();

    /** The player who ran the command, or empty for the console. */
    Optional<PlayerHandle> player();

    /** The server the command ran on. */
    ServerHandle server();

    /** Where the command was run from. */
    Vec3 position();

    /** Minecraft's 0-4 permission scale. The console is always 4. */
    int permissionLevel();

    /** Sends a normal reply, visible only to the caller. */
    void sendFeedback(Text message);

    /** Sends a failure reply, rendered in red. */
    void sendError(Text message);
}
