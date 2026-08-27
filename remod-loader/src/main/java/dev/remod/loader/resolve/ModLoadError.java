package dev.remod.loader.resolve;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A structured explanation of why one mod will not load.
 *
 * <p>Structured rather than a string, because the same failure has to be
 * rendered three ways: a one-line log entry, a block in the console, and a row
 * in the installer's GUI. Every error carries what was expected, what was
 * found, and what the user should do about it -- a bare
 * {@code NullPointerException} is never an acceptable answer.</p>
 *
 * <pre>
 * ReMod Mod Loading Error
 *
 * Mod:
 *   ExampleMod (examplemod 1.0.0)
 *
 * Reason:
 *   Incompatible ReMod API version
 *
 * Expected:
 *   1.21-1.0.0
 *
 * Installed:
 *   1.20-1.0.0
 *
 * What to do:
 *   Install the 1.21 build of this mod, or install ReMod for Minecraft 1.20.
 * </pre>
 */
public final class ModLoadError {

    private final String modId;
    private final String modName;
    private final String modVersion;
    private final String fileName;
    private final Reason reason;
    private final String detail;
    private final String expected;
    private final String found;
    private final List<String> solutions;
    private final Throwable cause;

    private ModLoadError(Builder builder) {
        this.modId = builder.modId;
        this.modName = builder.modName;
        this.modVersion = builder.modVersion;
        this.fileName = builder.fileName;
        this.reason = builder.reason;
        this.detail = builder.detail;
        this.expected = builder.expected;
        this.found = builder.found;
        this.solutions = Collections.unmodifiableList(new ArrayList<>(builder.solutions));
        this.cause = builder.cause;
    }

    public static Builder builder(Reason reason) {
        return new Builder(reason);
    }

    public String modId() {
        return modId;
    }

    public String modName() {
        return modName;
    }

    public String modVersion() {
        return modVersion;
    }

    public String fileName() {
        return fileName;
    }

    public Reason reason() {
        return reason;
    }

    public String detail() {
        return detail;
    }

    /** What ReMod needed, or {@code null} when the reason has no such pair. */
    public String expected() {
        return expected;
    }

    /** What was actually present. */
    public String found() {
        return found;
    }

    /** Concrete next steps, in the order the user should try them. */
    public List<String> solutions() {
        return solutions;
    }

    public Throwable cause() {
        return cause;
    }

    /** The compact form for the log. */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append(modName == null ? modId : modName);
        if (modVersion != null) {
            sb.append(' ').append(modVersion);
        }
        sb.append(": ").append(reason.title());
        if (detail != null && !detail.isEmpty()) {
            sb.append(" -- ").append(detail);
        }
        return sb.toString();
    }

    /** The full block, as shown in the console and the installer. */
    public String report() {
        StringBuilder sb = new StringBuilder();
        sb.append("ReMod Mod Loading Error").append(System.lineSeparator());
        sb.append(System.lineSeparator());
        sb.append("Mod:").append(System.lineSeparator());
        sb.append("  ").append(modName == null ? modId : modName);
        if (modId != null && !modId.equals(modName)) {
            sb.append(" (").append(modId);
            if (modVersion != null) {
                sb.append(' ').append(modVersion);
            }
            sb.append(')');
        }
        sb.append(System.lineSeparator());
        if (fileName != null) {
            sb.append("  ").append(fileName).append(System.lineSeparator());
        }
        sb.append(System.lineSeparator());
        sb.append("Reason:").append(System.lineSeparator());
        sb.append("  ").append(reason.title()).append(System.lineSeparator());
        if (detail != null && !detail.isEmpty()) {
            sb.append("  ").append(detail).append(System.lineSeparator());
        }
        if (expected != null) {
            sb.append(System.lineSeparator());
            sb.append("Expected:").append(System.lineSeparator());
            sb.append("  ").append(expected).append(System.lineSeparator());
        }
        if (found != null) {
            sb.append(System.lineSeparator());
            sb.append(reason == Reason.INCOMPATIBLE_API ? "Installed:" : "Found:")
                    .append(System.lineSeparator());
            sb.append("  ").append(found).append(System.lineSeparator());
        }
        if (!solutions.isEmpty()) {
            sb.append(System.lineSeparator());
            sb.append("What to do:").append(System.lineSeparator());
            for (String solution : solutions) {
                sb.append("  - ").append(solution).append(System.lineSeparator());
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return summary();
    }

    /** The categories of load failure ReMod distinguishes. */
    public enum Reason {

        INCOMPATIBLE_MINECRAFT("Incompatible Minecraft version"),
        INCOMPATIBLE_API("Incompatible ReMod API version"),
        MISSING_DEPENDENCY("Missing dependency"),
        UNSATISFIED_DEPENDENCY("Dependency version not satisfied"),
        INCOMPATIBLE_MOD("Incompatible mod installed"),
        DUPLICATE_MOD("Duplicate mod id"),
        DEPENDENCY_CYCLE("Circular dependency"),
        WRONG_SIDE("Wrong side"),
        ENTRYPOINT_MISSING("Entrypoint class not found"),
        ENTRYPOINT_INVALID("Entrypoint is not a ReMod mod"),
        INITIALISATION_FAILED("The mod threw an exception while loading"),
        DEPENDENCY_FAILED("A dependency failed to load");

        private final String title;

        Reason(String title) {
            this.title = title;
        }

        public String title() {
            return title;
        }
    }

    /** Fluent builder for {@link ModLoadError}. */
    public static final class Builder {

        private final Reason reason;
        private String modId = "unknown";
        private String modName;
        private String modVersion;
        private String fileName;
        private String detail;
        private String expected;
        private String found;
        private final List<String> solutions = new ArrayList<>();
        private Throwable cause;

        private Builder(Reason reason) {
            this.reason = reason;
        }

        public Builder mod(String id, String name, String version) {
            this.modId = id;
            this.modName = name;
            this.modVersion = version;
            return this;
        }

        public Builder file(String value) {
            this.fileName = value;
            return this;
        }

        public Builder detail(String value) {
            this.detail = value;
            return this;
        }

        public Builder expected(String value) {
            this.expected = value;
            return this;
        }

        public Builder found(String value) {
            this.found = value;
            return this;
        }

        public Builder solution(String value) {
            this.solutions.add(value);
            return this;
        }

        public Builder cause(Throwable value) {
            this.cause = value;
            return this;
        }

        public ModLoadError build() {
            return new ModLoadError(this);
        }
    }
}
