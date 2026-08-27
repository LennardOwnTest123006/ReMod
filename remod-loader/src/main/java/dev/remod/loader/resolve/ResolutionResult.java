package dev.remod.loader.resolve;

import dev.remod.loader.discovery.ModCandidate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The outcome of resolving a set of discovered mods. */
public final class ResolutionResult {

    private final List<ModCandidate> loadOrder;
    private final List<ModLoadError> errors;
    private final List<String> warnings;

    public ResolutionResult(List<ModCandidate> loadOrder, List<ModLoadError> errors,
                            List<String> warnings) {
        this.loadOrder = Collections.unmodifiableList(new ArrayList<>(loadOrder));
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
        this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
    }

    /** The mods that will load, in dependency order: dependencies first. */
    public List<ModCandidate> loadOrder() {
        return loadOrder;
    }

    /** One entry per mod that was rejected, with a full explanation. */
    public List<ModLoadError> errors() {
        return errors;
    }

    /** Non-fatal notes, e.g. an unsatisfied optional dependency. */
    public List<String> warnings() {
        return warnings;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public int loadedCount() {
        return loadOrder.size();
    }
}
