package dev.remod.loader;

import dev.remod.loader.discovery.DiscoveryProblem;
import dev.remod.loader.discovery.DiscoveryResult;
import dev.remod.loader.resolve.ModLoadError;
import dev.remod.loader.runtime.ModContainer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Everything that happened during one load: what loaded, what did not, and why. */
public final class LoadReport {

    private final List<ModContainer> loaded;
    private final List<ModLoadError> errors;
    private final List<String> warnings;
    private final List<DiscoveryProblem> discoveryProblems;
    private final List<DiscoveryResult.ForeignMod> foreignMods;
    private final long durationMillis;

    public LoadReport(List<ModContainer> loaded, List<ModLoadError> errors, List<String> warnings,
                      List<DiscoveryProblem> discoveryProblems,
                      List<DiscoveryResult.ForeignMod> foreignMods, long durationMillis) {
        this.loaded = Collections.unmodifiableList(new ArrayList<>(loaded));
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
        this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
        this.discoveryProblems = Collections.unmodifiableList(new ArrayList<>(discoveryProblems));
        this.foreignMods = Collections.unmodifiableList(new ArrayList<>(foreignMods));
        this.durationMillis = durationMillis;
    }

    /** The mods that loaded successfully, in load order. */
    public List<ModContainer> loaded() {
        return loaded;
    }

    /** One entry per mod that was rejected or failed. */
    public List<ModLoadError> errors() {
        return errors;
    }

    public List<String> warnings() {
        return warnings;
    }

    /** Files in the mods folder that could not be read as ReMod mods. */
    public List<DiscoveryProblem> discoveryProblems() {
        return discoveryProblems;
    }

    /** Mods for other loaders found in ReMod's mods folder. */
    public List<DiscoveryResult.ForeignMod> foreignMods() {
        return foreignMods;
    }

    public long durationMillis() {
        return durationMillis;
    }

    public boolean isClean() {
        return errors.isEmpty() && discoveryProblems.isEmpty();
    }

    public int loadedCount() {
        return loaded.size();
    }
}
