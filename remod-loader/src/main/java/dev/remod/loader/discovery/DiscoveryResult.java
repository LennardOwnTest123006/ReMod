package dev.remod.loader.discovery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Everything one scan of the mods directory found. */
public final class DiscoveryResult {

    private final List<ModCandidate> candidates;
    private final List<DiscoveryProblem> problems;
    private final List<ForeignMod> foreignMods;

    public DiscoveryResult(List<ModCandidate> candidates, List<DiscoveryProblem> problems,
                           List<ForeignMod> foreignMods) {
        this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
        this.problems = Collections.unmodifiableList(new ArrayList<>(problems));
        this.foreignMods = Collections.unmodifiableList(new ArrayList<>(foreignMods));
    }

    /** Mods whose manifest parsed. Not yet checked for compatibility. */
    public List<ModCandidate> candidates() {
        return candidates;
    }

    /** Files that could not be read as ReMod mods. */
    public List<DiscoveryProblem> problems() {
        return problems;
    }

    /**
     * Mods for other loaders found in ReMod's mods directory.
     *
     * <p>Reported rather than ignored: "I put my Fabric mod in the ReMod folder
     * and nothing happened" is otherwise a completely silent failure.</p>
     */
    public List<ForeignMod> foreignMods() {
        return foreignMods;
    }

    public boolean isEmpty() {
        return candidates.isEmpty();
    }

    /** A mod belonging to another loader. */
    public static final class ForeignMod {

        private final java.nio.file.Path path;
        private final String loaderName;

        public ForeignMod(java.nio.file.Path path, String loaderName) {
            this.path = path;
            this.loaderName = loaderName;
        }

        public java.nio.file.Path path() {
            return path;
        }

        /** The loader this mod is for, e.g. {@code Fabric}. */
        public String loaderName() {
            return loaderName;
        }

        public String fileName() {
            return path.getFileName().toString();
        }
    }
}
