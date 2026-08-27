package dev.remod.loader.runtime;

import dev.remod.api.game.Identifier;
import dev.remod.api.registry.DuplicateRegistrationException;
import dev.remod.api.registry.Registry;
import dev.remod.api.registry.RegistryEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A registry that records definitions and hands them to the version adapter.
 *
 * <p>Registration is a two-step process on purpose. ReMod stores the definition
 * immediately, so mods can refer to their own content right away, and binds it
 * to the running game through the {@link dev.remod.api.service.GameBridge}
 * afterwards. That is what lets the same mod code run under {@code remod test}
 * with no game attached.</p>
 */
public final class DefaultRegistry<D> implements Registry<D> {

    private final String name;
    private final Map<Identifier, Entry<D>> entries = new LinkedHashMap<>();
    private final Function<D, Identifier> idExtractor;
    private final Supplier<String> currentOwner;
    private final Supplier<Boolean> openCheck;
    private final Binder<D> binder;

    /**
     * @param idExtractor  reads the identifier out of a definition
     * @param currentOwner the mod currently registering; used for attribution
     * @param openCheck    whether registration is currently allowed
     * @param binder       hands a new entry to the version adapter
     */
    public DefaultRegistry(String name, Function<D, Identifier> idExtractor,
                           Supplier<String> currentOwner, Supplier<Boolean> openCheck,
                           Binder<D> binder) {
        this.name = name;
        this.idExtractor = idExtractor;
        this.currentOwner = currentOwner;
        this.openCheck = openCheck;
        this.binder = binder;
    }

    /** Hands a registered entry to the version adapter. */
    @FunctionalInterface
    public interface Binder<D> {
        boolean bind(RegistryEntry<D> entry);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public synchronized RegistryEntry<D> register(D definition) {
        if (definition == null) {
            throw new IllegalArgumentException("Cannot register a null " + name + " definition");
        }
        if (!openCheck.get()) {
            throw new IllegalStateException("The " + name + " registry is closed. Register content"
                    + " during onInitialize (the INIT phase), not later -- Minecraft's registries"
                    + " are frozen once the game has started.");
        }
        Identifier id = idExtractor.apply(definition);
        String owner = currentOwner.get();
        Entry<D> existing = entries.get(id);
        if (existing != null) {
            throw new DuplicateRegistrationException(name, id, existing.ownerModId(), owner);
        }
        Entry<D> entry = new Entry<>(id, definition, owner);
        entries.put(id, entry);
        entry.bound = binder.bind(entry);
        return entry;
    }

    @Override
    public Optional<RegistryEntry<D>> get(Identifier id) {
        return Optional.ofNullable(entries.get(id));
    }

    @Override
    public boolean contains(Identifier id) {
        return entries.containsKey(id);
    }

    @Override
    public Collection<RegistryEntry<D>> entries() {
        return Collections.unmodifiableCollection(new ArrayList<>(entries.values()));
    }

    @Override
    public Collection<RegistryEntry<D>> entriesOf(String modId) {
        List<RegistryEntry<D>> owned = new ArrayList<>();
        for (Entry<D> entry : entries.values()) {
            if (entry.ownerModId().equals(modId)) {
                owned.add(entry);
            }
        }
        return owned;
    }

    @Override
    public int size() {
        return entries.size();
    }

    /** Removes everything one mod registered, used when a mod fails mid-load. */
    public synchronized void removeAllOf(String modId) {
        entries.entrySet().removeIf(entry -> entry.getValue().ownerModId().equals(modId));
    }

    /** Re-binds every entry; called once the adapter attaches to a live game. */
    public synchronized int bindAll() {
        int bound = 0;
        for (Entry<D> entry : entries.values()) {
            if (!entry.bound) {
                entry.bound = binder.bind(entry);
            }
            if (entry.bound) {
                bound++;
            }
        }
        return bound;
    }

    private static final class Entry<D> implements RegistryEntry<D> {

        private final Identifier id;
        private final D definition;
        private final String ownerModId;
        private volatile boolean bound;

        Entry(Identifier id, D definition, String ownerModId) {
            this.id = id;
            this.definition = definition;
            this.ownerModId = ownerModId;
        }

        @Override
        public Identifier id() {
            return id;
        }

        @Override
        public D definition() {
            return definition;
        }

        @Override
        public String ownerModId() {
            return ownerModId;
        }

        @Override
        public boolean isBound() {
            return bound;
        }

        @Override
        public String toString() {
            return id + " (from " + ownerModId + ")";
        }
    }
}
