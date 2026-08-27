package dev.remod.loader.runtime;

import dev.remod.api.command.CommandRegistry;
import dev.remod.api.command.CommandSpec;
import dev.remod.api.command.DuplicateCommandException;
import dev.remod.api.service.GameBridge;
import dev.remod.common.log.ReModLog;
import dev.remod.common.log.ReModLogger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The command registry shared by every mod.
 *
 * <p>Names and aliases share one namespace, exactly as Minecraft's own command
 * tree does, so a clash is caught here with both mod ids named rather than
 * surfacing as one mod's command mysteriously not working.</p>
 */
public final class DefaultCommandRegistry implements CommandRegistry {

    private static final ReModLogger LOG = ReModLog.get("ReMod/Commands");

    private final Map<String, CommandSpec> byName = new LinkedHashMap<>();
    private final Map<String, String> owners = new LinkedHashMap<>();
    private final Supplier<String> currentOwner;
    private final Supplier<GameBridge> bridge;

    public DefaultCommandRegistry(Supplier<String> currentOwner, Supplier<GameBridge> bridge) {
        this.currentOwner = currentOwner;
        this.bridge = bridge;
    }

    @Override
    public synchronized void register(CommandSpec command) {
        if (command == null) {
            throw new IllegalArgumentException("Cannot register a null command");
        }
        String owner = currentOwner.get();
        checkFree(command.name(), owner);
        for (String alias : command.aliases()) {
            checkFree(alias, owner);
        }
        byName.put(command.name(), command);
        owners.put(command.name(), owner);
        for (String alias : command.aliases()) {
            byName.put(alias, command);
            owners.put(alias, owner);
        }
        GameBridge activeBridge = bridge.get();
        boolean bound = activeBridge != null && activeBridge.bindCommand(command, owner);
        if (!bound) {
            LOG.debug(() -> "/" + command.name() + " from " + owner
                    + " is registered but not yet bound to a running game");
        }
    }

    private void checkFree(String name, String owner) {
        String key = name.toLowerCase(Locale.ROOT);
        String existingOwner = owners.get(key);
        if (existingOwner != null) {
            throw new DuplicateCommandException(name, existingOwner, owner);
        }
    }

    @Override
    public Optional<CommandSpec> find(String nameOrAlias) {
        if (nameOrAlias == null) {
            return Optional.empty();
        }
        String key = nameOrAlias.trim().toLowerCase(Locale.ROOT);
        if (key.startsWith("/")) {
            key = key.substring(1);
        }
        return Optional.ofNullable(byName.get(key));
    }

    @Override
    public synchronized Collection<CommandSpec> commands() {
        // Aliases map to the same spec; list each command once, under its own name.
        List<CommandSpec> distinct = new ArrayList<>();
        for (Map.Entry<String, CommandSpec> entry : byName.entrySet()) {
            if (entry.getKey().equals(entry.getValue().name())) {
                distinct.add(entry.getValue());
            }
        }
        return Collections.unmodifiableList(distinct);
    }

    @Override
    public Optional<String> ownerOf(String name) {
        return Optional.ofNullable(owners.get(name == null ? "" : name.toLowerCase(Locale.ROOT)));
    }

    /** Removes every command one mod registered, aliases included. */
    public synchronized void removeAllOf(String modId) {
        List<String> ownedKeys = new ArrayList<>();
        for (Map.Entry<String, String> entry : owners.entrySet()) {
            if (entry.getValue().equals(modId)) {
                ownedKeys.add(entry.getKey());
            }
        }
        for (String key : ownedKeys) {
            byName.remove(key);
            owners.remove(key);
        }
    }

    /** Re-binds every command once a game bridge becomes available. */
    public synchronized int bindAll() {
        GameBridge activeBridge = bridge.get();
        if (activeBridge == null) {
            return 0;
        }
        int bound = 0;
        for (CommandSpec command : commands()) {
            String owner = owners.getOrDefault(command.name(), "remod");
            if (activeBridge.bindCommand(command, owner)) {
                bound++;
            }
        }
        return bound;
    }
}
