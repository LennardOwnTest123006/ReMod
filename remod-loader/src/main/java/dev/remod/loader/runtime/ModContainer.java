package dev.remod.loader.runtime;

import dev.remod.api.ReModMod;
import dev.remod.api.mod.ModMetadata;
import dev.remod.loader.discovery.ModCandidate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One loaded mod: its metadata, its entrypoint instances and its context.
 *
 * <p>The container is what the loader iterates over during each lifecycle
 * phase, and what it consults when a mod misbehaves.</p>
 */
public final class ModContainer {

    private final ModCandidate candidate;
    private final List<ReModMod> entrypoints = new ArrayList<>();
    private final DefaultReModContext context;
    private volatile State state = State.DISCOVERED;
    private volatile Throwable failure;

    public ModContainer(ModCandidate candidate, DefaultReModContext context) {
        this.candidate = candidate;
        this.context = context;
    }

    public ModCandidate candidate() {
        return candidate;
    }

    public ModMetadata metadata() {
        return candidate.metadata();
    }

    public String id() {
        return candidate.id();
    }

    public DefaultReModContext context() {
        return context;
    }

    public List<ReModMod> entrypoints() {
        return Collections.unmodifiableList(entrypoints);
    }

    public void addEntrypoint(ReModMod mod) {
        entrypoints.add(mod);
    }

    public State state() {
        return state;
    }

    public void state(State value) {
        this.state = value;
    }

    /** The exception that stopped this mod, or {@code null}. */
    public Throwable failure() {
        return failure;
    }

    public void fail(Throwable cause) {
        this.failure = cause;
        this.state = State.FAILED;
    }

    public boolean isActive() {
        return state != State.FAILED && state != State.DISABLED;
    }

    @Override
    public String toString() {
        return id() + " " + metadata().version().raw() + " [" + state + "]";
    }

    /** Where a mod is in its lifecycle. */
    public enum State {
        DISCOVERED,
        CONSTRUCTED,
        PRE_INITIALISED,
        INITIALISED,
        POST_INITIALISED,
        SIDE_INITIALISED,
        STOPPED,
        /** Threw during a lifecycle phase; excluded from later phases. */
        FAILED,
        /** Rejected before construction, e.g. by the resolver. */
        DISABLED
    }
}
