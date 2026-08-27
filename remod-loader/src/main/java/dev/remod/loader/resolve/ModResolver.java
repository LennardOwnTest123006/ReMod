package dev.remod.loader.resolve;

import dev.remod.api.Side;
import dev.remod.api.mod.ModDependency;
import dev.remod.api.mod.ModMetadata;
import dev.remod.common.log.ReModLog;
import dev.remod.common.log.ReModLogger;
import dev.remod.common.version.ApiVersion;
import dev.remod.common.version.SemanticVersion;
import dev.remod.loader.discovery.ModCandidate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides which discovered mods can load, and in what order.
 *
 * <p>Resolution runs as a pipeline, and each stage removes mods rather than
 * aborting, so the user gets one complete report instead of discovering
 * problems one restart at a time:</p>
 *
 * <ol>
 *   <li>Reject duplicates -- two files claiming the same mod id.</li>
 *   <li>Reject mods whose declared Minecraft range excludes this version.</li>
 *   <li>Reject mods whose required API version is not satisfied.</li>
 *   <li>Reject mods for the wrong side.</li>
 *   <li>Reject mods with a missing or unsatisfiable required dependency, then
 *       repeat, because removing one mod can orphan another.</li>
 *   <li>Reject mods declared incompatible with something present.</li>
 *   <li>Topologically sort what is left; a cycle rejects everything in it.</li>
 * </ol>
 */
public final class ModResolver {

    private static final ReModLogger LOG = ReModLog.get("ReMod/Resolver");

    private final String minecraftVersion;
    private final ApiVersion installedApi;
    private final Side side;

    /**
     * @param minecraftVersion the version actually running, e.g. {@code 1.21.4}
     * @param installedApi     the ReMod API version present
     * @param side             the side this process runs as
     */
    public ModResolver(String minecraftVersion, ApiVersion installedApi, Side side) {
        this.minecraftVersion = minecraftVersion;
        this.installedApi = installedApi;
        this.side = side;
    }

    public ResolutionResult resolve(Collection<ModCandidate> discovered) {
        List<ModLoadError> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        Map<String, ModCandidate> accepted = rejectDuplicates(discovered, errors);
        rejectIncompatible(accepted, errors);
        rejectUnsatisfiedDependencies(accepted, errors, warnings);
        rejectIncompatibleCombinations(accepted, errors);

        List<ModCandidate> ordered = sortByDependencies(accepted, errors);
        LOG.debug(() -> "Resolved " + ordered.size() + " mod(s); " + errors.size() + " rejected");
        return new ResolutionResult(ordered, errors, warnings);
    }

    /**
     * Two files claiming the same id is always a user-fixable mistake -- usually
     * an old copy left behind after an update.
     */
    private Map<String, ModCandidate> rejectDuplicates(Collection<ModCandidate> discovered,
                                                       List<ModLoadError> errors) {
        Map<String, ModCandidate> accepted = new LinkedHashMap<>();
        for (ModCandidate candidate : discovered) {
            ModCandidate existing = accepted.get(candidate.id());
            if (existing == null) {
                accepted.put(candidate.id(), candidate);
                continue;
            }
            // Keep the newer version; reject the older so the user is told
            // which file to delete.
            ModCandidate keep = existing;
            ModCandidate drop = candidate;
            if (candidate.metadata().version().compareTo(existing.metadata().version()) > 0) {
                keep = candidate;
                drop = existing;
                accepted.put(candidate.id(), candidate);
            }
            errors.add(ModLoadError.builder(ModLoadError.Reason.DUPLICATE_MOD)
                    .mod(drop.id(), drop.metadata().name(), drop.metadata().version().raw())
                    .file(drop.fileName())
                    .detail("Two files in the mods folder both provide the mod id '"
                            + drop.id() + "'.")
                    .expected("one file per mod id")
                    .found(keep.fileName() + " and " + drop.fileName())
                    .solution("Delete " + drop.fileName() + "; ReMod is using "
                            + keep.fileName() + " (version "
                            + keep.metadata().version().raw() + ").")
                    .build());
        }
        return accepted;
    }

    private void rejectIncompatible(Map<String, ModCandidate> accepted,
                                    List<ModLoadError> errors) {
        for (ModCandidate candidate : new ArrayList<>(accepted.values())) {
            ModMetadata metadata = candidate.metadata();

            if (!metadata.minecraft().matches(minecraftVersion)) {
                accepted.remove(candidate.id());
                errors.add(ModLoadError.builder(ModLoadError.Reason.INCOMPATIBLE_MINECRAFT)
                        .mod(metadata.id(), metadata.name(), metadata.version().raw())
                        .file(candidate.fileName())
                        .detail("This mod does not declare support for the Minecraft version"
                                + " you are running.")
                        .expected(metadata.minecraft().raw())
                        .found(minecraftVersion)
                        .solution("Install the build of " + metadata.name() + " made for Minecraft "
                                + minecraftVersion + ".")
                        .solution("Or install ReMod for a Minecraft version this mod supports ("
                                + metadata.minecraft().raw() + ").")
                        .build());
                continue;
            }

            if (installedApi != null && !installedApi.satisfies(metadata.apiVersion())) {
                accepted.remove(candidate.id());
                ModLoadError.Builder error =
                        ModLoadError.builder(ModLoadError.Reason.INCOMPATIBLE_API)
                                .mod(metadata.id(), metadata.name(), metadata.version().raw())
                                .file(candidate.fileName())
                                .expected(metadata.apiVersion().toString())
                                .found(installedApi.toString());
                if (!metadata.apiVersion().isPortable()
                        && !installedApi.minecraftSeries().equals(
                                metadata.apiVersion().minecraftSeries())) {
                    error.detail("This mod is pinned to the ReMod API for Minecraft "
                                    + metadata.apiVersion().minecraftSeries()
                                    + ", but ReMod is running the API for Minecraft "
                                    + installedApi.minecraftSeries() + ".")
                            .solution("Install the " + metadata.apiVersion().minecraftSeries()
                                    + " build of " + metadata.name() + ".")
                            .solution("Mod authors: declaring \"remod_api\": \""
                                    + metadata.apiVersion().baseline().raw() + "\" instead of \""
                                    + metadata.apiVersion() + "\" makes one jar work on every"
                                    + " Minecraft version the mod supports.");
                } else if (installedApi.baseline().compareTo(
                        metadata.apiVersion().baseline()) < 0) {
                    error.detail("This mod needs newer ReMod API features than the installed"
                                    + " API provides.")
                            .solution("Update ReMod: run ReMod.jar and reinstall for Minecraft "
                                    + minecraftVersion + ".");
                } else {
                    error.detail("This mod was built against a ReMod API major version that is"
                                    + " no longer source-compatible.")
                            .solution("Ask the mod's author for a build against ReMod API "
                                    + installedApi.baseline().raw() + ".");
                }
                errors.add(error.build());
                continue;
            }

            if (!metadata.side().runsOn(side)) {
                accepted.remove(candidate.id());
                errors.add(ModLoadError.builder(ModLoadError.Reason.WRONG_SIDE)
                        .mod(metadata.id(), metadata.name(), metadata.version().raw())
                        .file(candidate.fileName())
                        .detail("This mod is declared as " + metadata.side().token()
                                + "-only and cannot run here.")
                        .expected(metadata.side().token())
                        .found(side.token())
                        .solution(metadata.side() == Side.CLIENT
                                ? "Remove this mod from the server; install it on clients only."
                                : "Remove this mod from the client; install it on the server only.")
                        .build());
            }
        }
    }

    /**
     * Removing a mod can orphan the mods that depended on it, so this repeats
     * until nothing more is removed.
     */
    private void rejectUnsatisfiedDependencies(Map<String, ModCandidate> accepted,
                                               List<ModLoadError> errors,
                                               List<String> warnings) {
        boolean changed = true;
        Set<String> reportedOptional = new HashSet<>();
        while (changed) {
            changed = false;
            for (ModCandidate candidate : new ArrayList<>(accepted.values())) {
                for (ModDependency dependency :
                        candidate.metadata().dependencies(ModDependency.Kind.REQUIRED)) {
                    ModCandidate provider = accepted.get(dependency.modId());
                    if (provider == null) {
                        accepted.remove(candidate.id());
                        errors.add(missingDependency(candidate, dependency));
                        changed = true;
                        break;
                    }
                    SemanticVersion providerVersion = provider.metadata().version();
                    if (!dependency.versionRange().matches(providerVersion)) {
                        accepted.remove(candidate.id());
                        errors.add(ModLoadError.builder(
                                        ModLoadError.Reason.UNSATISFIED_DEPENDENCY)
                                .mod(candidate.id(), candidate.metadata().name(),
                                        candidate.metadata().version().raw())
                                .file(candidate.fileName())
                                .detail("Requires " + dependency.modId() + " "
                                        + dependency.versionRange().raw()
                                        + ", but a different version is installed.")
                                .expected(dependency.modId() + " "
                                        + dependency.versionRange().raw())
                                .found(dependency.modId() + " " + providerVersion.raw())
                                .solution("Update " + dependency.modId() + " to a version"
                                        + " matching " + dependency.versionRange().raw() + ".")
                                .build());
                        changed = true;
                        break;
                    }
                }
            }
        }
        for (ModCandidate candidate : accepted.values()) {
            for (ModDependency dependency :
                    candidate.metadata().dependencies(ModDependency.Kind.OPTIONAL)) {
                ModCandidate provider = accepted.get(dependency.modId());
                String key = candidate.id() + "->" + dependency.modId();
                if (provider != null
                        && !dependency.versionRange().matches(provider.metadata().version())
                        && reportedOptional.add(key)) {
                    warnings.add(candidate.id() + " has an optional dependency on "
                            + dependency.modId() + " " + dependency.versionRange().raw()
                            + " but " + provider.metadata().version().raw()
                            + " is installed; its integration features will be disabled.");
                }
            }
        }
    }

    private ModLoadError missingDependency(ModCandidate candidate, ModDependency dependency) {
        ModLoadError.Builder error = ModLoadError.builder(ModLoadError.Reason.MISSING_DEPENDENCY)
                .mod(candidate.id(), candidate.metadata().name(),
                        candidate.metadata().version().raw())
                .file(candidate.fileName())
                .detail("Requires the mod '" + dependency.modId() + "', which is not installed"
                        + " or failed to load.")
                .expected(dependency.modId() + " " + dependency.versionRange().raw())
                .found("not installed")
                .solution("Install " + dependency.modId() + " "
                        + dependency.versionRange().raw() + " into the ReMod mods folder.");
        error.solution("Or remove " + candidate.metadata().name() + " if you do not need it.");
        return error.build();
    }

    private void rejectIncompatibleCombinations(Map<String, ModCandidate> accepted,
                                                List<ModLoadError> errors) {
        for (ModCandidate candidate : new ArrayList<>(accepted.values())) {
            if (!accepted.containsKey(candidate.id())) {
                continue;
            }
            for (ModDependency dependency :
                    candidate.metadata().dependencies(ModDependency.Kind.INCOMPATIBLE)) {
                ModCandidate other = accepted.get(dependency.modId());
                if (other != null
                        && dependency.versionRange().matches(other.metadata().version())) {
                    accepted.remove(candidate.id());
                    errors.add(ModLoadError.builder(ModLoadError.Reason.INCOMPATIBLE_MOD)
                            .mod(candidate.id(), candidate.metadata().name(),
                                    candidate.metadata().version().raw())
                            .file(candidate.fileName())
                            .detail("Declares that it cannot run alongside "
                                    + other.metadata().name() + ".")
                            .expected(dependency.modId() + " "
                                    + dependency.versionRange().raw() + " absent")
                            .found(other.metadata().name() + " "
                                    + other.metadata().version().raw() + " installed")
                            .solution("Remove either " + candidate.metadata().name()
                                    + " or " + other.metadata().name() + ".")
                            .build());
                    break;
                }
            }
        }
    }

    /** Depth-first topological sort; any mod involved in a cycle is rejected. */
    private List<ModCandidate> sortByDependencies(Map<String, ModCandidate> accepted,
                                                  List<ModLoadError> errors) {
        List<ModCandidate> ordered = new ArrayList<>();
        Map<String, Mark> marks = new HashMap<>();
        Set<String> inCycle = new LinkedHashSet<>();

        // Sorting the roots by id keeps load order deterministic across runs,
        // which matters when diagnosing a user's log.
        List<String> ids = new ArrayList<>(accepted.keySet());
        java.util.Collections.sort(ids);

        for (String id : ids) {
            visit(id, accepted, marks, ordered, new ArrayList<>(), inCycle);
        }
        for (String id : inCycle) {
            ModCandidate candidate = accepted.get(id);
            if (candidate == null) {
                continue;
            }
            errors.add(ModLoadError.builder(ModLoadError.Reason.DEPENDENCY_CYCLE)
                    .mod(candidate.id(), candidate.metadata().name(),
                            candidate.metadata().version().raw())
                    .file(candidate.fileName())
                    .detail("These mods depend on each other in a loop, so none of them can"
                            + " load first: " + String.join(" -> ", inCycle) + ".")
                    .solution("Report this to the authors of " + String.join(", ", inCycle)
                            + "; one of the dependencies should be optional.")
                    .build());
        }
        ordered.removeIf(candidate -> inCycle.contains(candidate.id()));
        return ordered;
    }

    private void visit(String id, Map<String, ModCandidate> accepted, Map<String, Mark> marks,
                       List<ModCandidate> ordered, List<String> stack, Set<String> inCycle) {
        Mark mark = marks.get(id);
        if (mark == Mark.DONE) {
            return;
        }
        if (mark == Mark.VISITING) {
            int start = stack.indexOf(id);
            inCycle.addAll(stack.subList(start < 0 ? 0 : start, stack.size()));
            return;
        }
        ModCandidate candidate = accepted.get(id);
        if (candidate == null) {
            return;
        }
        marks.put(id, Mark.VISITING);
        stack.add(id);
        for (ModDependency dependency : candidate.metadata().dependencies()) {
            if (dependency.kind() == ModDependency.Kind.INCOMPATIBLE) {
                continue;
            }
            if (accepted.containsKey(dependency.modId())) {
                visit(dependency.modId(), accepted, marks, ordered, stack, inCycle);
            }
        }
        stack.remove(stack.size() - 1);
        marks.put(id, Mark.DONE);
        ordered.add(candidate);
    }

    private enum Mark { VISITING, DONE }
}
