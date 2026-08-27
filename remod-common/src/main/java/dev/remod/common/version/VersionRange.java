package dev.remod.common.version;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A version constraint, in the notations mod authors actually write.
 *
 * <table>
 *   <caption>Supported syntax</caption>
 *   <tr><td>{@code *} / {@code any}</td><td>matches everything</td></tr>
 *   <tr><td>{@code 1.21.4}</td><td>exactly that version</td></tr>
 *   <tr><td>{@code 1.21.x} / {@code 1.21.*}</td><td>any patch of the 1.21 series</td></tr>
 *   <tr><td>{@code &gt;=1.20 &lt;1.22}</td><td>space-separated comparators, all must hold</td></tr>
 *   <tr><td>{@code ~1.21.2}</td><td>&gt;=1.21.2 and &lt;1.22.0</td></tr>
 *   <tr><td>{@code ^1.21.2}</td><td>&gt;=1.21.2 and &lt;2.0.0</td></tr>
 *   <tr><td>{@code [1.20,1.22)}</td><td>Maven-style interval</td></tr>
 *   <tr><td>{@code a || b}</td><td>union of two ranges</td></tr>
 * </table>
 *
 * <p>An unparseable range is a hard error rather than a silent "matches
 * nothing": a typo in a mod manifest should be reported to its author, not
 * quietly disable the mod.</p>
 */
public final class VersionRange {

    private final String raw;
    /** Outer list is OR, inner list is AND. */
    private final List<List<Comparator>> alternatives;

    private VersionRange(String raw, List<List<Comparator>> alternatives) {
        this.raw = raw;
        this.alternatives = alternatives;
    }

    public static VersionRange parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new InvalidVersionException("Version range is empty");
        }
        String raw = text.trim();
        List<List<Comparator>> alternatives = new ArrayList<>();
        // -1 keeps a trailing empty alternative ("1.20 || ") so it is reported
        // as a typo instead of being silently dropped by split().
        for (String alternative : raw.split("\\|\\|", -1)) {
            String trimmed = alternative.trim();
            if (trimmed.isEmpty()) {
                throw new InvalidVersionException("Empty alternative in version range '" + raw + "'");
            }
            alternatives.add(parseAlternative(trimmed, raw));
        }
        return new VersionRange(raw, alternatives);
    }

    /** A range that accepts every version. */
    public static VersionRange any() {
        return parse("*");
    }

    private static List<Comparator> parseAlternative(String text, String raw) {
        List<Comparator> comparators = new ArrayList<>();
        if (text.equals("*") || text.equalsIgnoreCase("any")) {
            comparators.add(Comparator.always());
            return comparators;
        }
        if (isInterval(text)) {
            comparators.addAll(parseInterval(text, raw));
            return comparators;
        }
        for (String token : text.split("\\s+")) {
            if (!token.isEmpty()) {
                comparators.addAll(parseToken(token, raw));
            }
        }
        if (comparators.isEmpty()) {
            throw new InvalidVersionException("Version range '" + raw + "' has no comparators");
        }
        return comparators;
    }

    private static boolean isInterval(String text) {
        return (text.startsWith("[") || text.startsWith("("))
                && (text.endsWith("]") || text.endsWith(")"));
    }

    private static List<Comparator> parseInterval(String text, String raw) {
        boolean lowerInclusive = text.charAt(0) == '[';
        boolean upperInclusive = text.charAt(text.length() - 1) == ']';
        String body = text.substring(1, text.length() - 1);
        String[] bounds = body.split(",", -1);
        if (bounds.length != 2) {
            throw new InvalidVersionException(
                    "Interval '" + text + "' in range '" + raw + "' must have exactly one comma");
        }
        List<Comparator> comparators = new ArrayList<>();
        String lower = bounds[0].trim();
        String upper = bounds[1].trim();
        if (!lower.isEmpty()) {
            comparators.add(new Comparator(lowerInclusive ? Op.GE : Op.GT, version(lower, raw)));
        }
        if (!upper.isEmpty()) {
            comparators.add(new Comparator(upperInclusive ? Op.LE : Op.LT, version(upper, raw)));
        }
        if (comparators.isEmpty()) {
            comparators.add(Comparator.always());
        }
        return comparators;
    }

    private static List<Comparator> parseToken(String token, String raw) {
        if (token.startsWith(">=")) {
            return one(Op.GE, token.substring(2), raw);
        }
        if (token.startsWith("<=")) {
            return one(Op.LE, token.substring(2), raw);
        }
        if (token.startsWith("==")) {
            return one(Op.EQ, token.substring(2), raw);
        }
        if (token.startsWith("!=")) {
            return one(Op.NE, token.substring(2), raw);
        }
        if (token.startsWith(">")) {
            return one(Op.GT, token.substring(1), raw);
        }
        if (token.startsWith("<")) {
            return one(Op.LT, token.substring(1), raw);
        }
        if (token.startsWith("=")) {
            return one(Op.EQ, token.substring(1), raw);
        }
        if (token.startsWith("~")) {
            return tilde(token.substring(1), raw);
        }
        if (token.startsWith("^")) {
            return caret(token.substring(1), raw);
        }
        if (isWildcard(token)) {
            return wildcard(token, raw);
        }
        return one(Op.EQ, token, raw);
    }

    private static boolean isWildcard(String token) {
        return token.endsWith(".x") || token.endsWith(".X") || token.endsWith(".*")
                || token.equals("x") || token.equals("X");
    }

    /** {@code 1.21.x} becomes {@code >=1.21.0 <1.22.0}; {@code 1.x} becomes {@code >=1.0 <2.0}. */
    private static List<Comparator> wildcard(String token, String raw) {
        if (token.equals("x") || token.equals("X")) {
            return Collections.singletonList(Comparator.always());
        }
        String prefix = token.substring(0, token.length() - 2);
        String[] parts = prefix.split("\\.");
        List<Comparator> comparators = new ArrayList<>();
        if (parts.length >= 2) {
            SemanticVersion lower = version(prefix + ".0", raw);
            comparators.add(new Comparator(Op.GE, lower));
            comparators.add(new Comparator(Op.LT,
                    version(lower.major() + "." + (lower.minor() + 1) + ".0", raw)));
        } else {
            SemanticVersion lower = version(prefix + ".0.0", raw);
            comparators.add(new Comparator(Op.GE, lower));
            comparators.add(new Comparator(Op.LT, version((lower.major() + 1) + ".0.0", raw)));
        }
        return comparators;
    }

    private static List<Comparator> tilde(String text, String raw) {
        SemanticVersion base = version(text, raw);
        List<Comparator> comparators = new ArrayList<>();
        comparators.add(new Comparator(Op.GE, base));
        comparators.add(new Comparator(Op.LT,
                version(base.major() + "." + (base.minor() + 1) + ".0", raw)));
        return comparators;
    }

    private static List<Comparator> caret(String text, String raw) {
        SemanticVersion base = version(text, raw);
        List<Comparator> comparators = new ArrayList<>();
        comparators.add(new Comparator(Op.GE, base));
        if (base.major() > 0) {
            comparators.add(new Comparator(Op.LT, version((base.major() + 1) + ".0.0", raw)));
        } else {
            // 0.x releases treat the minor component as breaking, per SemVer.
            comparators.add(new Comparator(Op.LT, version("0." + (base.minor() + 1) + ".0", raw)));
        }
        return comparators;
    }

    private static List<Comparator> one(Op op, String text, String raw) {
        return Collections.singletonList(new Comparator(op, version(text, raw)));
    }

    private static SemanticVersion version(String text, String raw) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidVersionException("Version range '" + raw + "' has a missing operand");
        }
        try {
            return SemanticVersion.parse(trimmed);
        } catch (InvalidVersionException e) {
            throw new InvalidVersionException(
                    "Version range '" + raw + "' contains an unusable version '" + trimmed + "'");
        }
    }

    public boolean matches(String version) {
        SemanticVersion parsed = SemanticVersion.tryParse(version);
        return parsed != null && matches(parsed);
    }

    public boolean matches(SemanticVersion version) {
        if (version == null) {
            return false;
        }
        for (List<Comparator> alternative : alternatives) {
            boolean all = true;
            for (Comparator comparator : alternative) {
                if (!comparator.test(version)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return true;
            }
        }
        return false;
    }

    public String raw() {
        return raw;
    }

    @Override
    public String toString() {
        return raw;
    }

    private enum Op { EQ, NE, GT, GE, LT, LE, ANY }

    private static final class Comparator {

        private final Op op;
        private final SemanticVersion operand;

        Comparator(Op op, SemanticVersion operand) {
            this.op = op;
            this.operand = operand;
        }

        static Comparator always() {
            return new Comparator(Op.ANY, null);
        }

        boolean test(SemanticVersion version) {
            if (op == Op.ANY) {
                return true;
            }
            // Opaque identifiers (snapshots) only ever satisfy an exact match.
            if (!version.isNumeric() || !operand.isNumeric()) {
                boolean equal = version.raw().equals(operand.raw());
                return op == Op.NE ? !equal : (op == Op.EQ && equal);
            }
            int cmp = version.compareTo(operand);
            switch (op) {
                case EQ: return cmp == 0;
                case NE: return cmp != 0;
                case GT: return cmp > 0;
                case GE: return cmp >= 0;
                case LT: return cmp < 0;
                case LE: return cmp <= 0;
                default: return true;
            }
        }
    }
}
