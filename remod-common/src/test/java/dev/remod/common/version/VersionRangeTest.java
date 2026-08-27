package dev.remod.common.version;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionRangeTest {

    @Test
    void wildcardMatchesEverything() {
        VersionRange any = VersionRange.parse("*");
        assertTrue(any.matches("1.21.4"));
        assertTrue(any.matches("0.0.1"));
        assertTrue(any.matches("24w14a"));
    }

    @Test
    void exactVersionMatchesOnlyItself() {
        VersionRange exact = VersionRange.parse("1.21.4");
        assertTrue(exact.matches("1.21.4"));
        assertFalse(exact.matches("1.21.3"));
        assertFalse(exact.matches("1.21.5"));
    }

    @Test
    void seriesWildcardCoversThePatchRange() {
        VersionRange series = VersionRange.parse("1.21.x");
        assertTrue(series.matches("1.21"));
        assertTrue(series.matches("1.21.0"));
        assertTrue(series.matches("1.21.9"));
        assertFalse(series.matches("1.20.9"));
        assertFalse(series.matches("1.22.0"));

        // The star spelling behaves identically.
        assertTrue(VersionRange.parse("1.21.*").matches("1.21.4"));
    }

    @Test
    void majorWildcardCoversTheWholeMajor() {
        VersionRange major = VersionRange.parse("1.x");
        assertTrue(major.matches("1.0.0"));
        assertTrue(major.matches("1.21.4"));
        assertFalse(major.matches("2.0.0"));
    }

    @Test
    void comparatorsCombineWithAnd() {
        VersionRange range = VersionRange.parse(">=1.20 <1.22");
        assertTrue(range.matches("1.20"));
        assertTrue(range.matches("1.21.4"));
        assertFalse(range.matches("1.19.4"));
        assertFalse(range.matches("1.22"));
    }

    @Test
    void supportsTildeAndCaret() {
        VersionRange tilde = VersionRange.parse("~1.21.2");
        assertTrue(tilde.matches("1.21.2"));
        assertTrue(tilde.matches("1.21.9"));
        assertFalse(tilde.matches("1.21.1"));
        assertFalse(tilde.matches("1.22.0"));

        VersionRange caret = VersionRange.parse("^1.21.2");
        assertTrue(caret.matches("1.21.2"));
        assertTrue(caret.matches("1.30.0"));
        assertFalse(caret.matches("2.0.0"));

        // Under SemVer, 0.x minor bumps are breaking.
        VersionRange zero = VersionRange.parse("^0.3.1");
        assertTrue(zero.matches("0.3.9"));
        assertFalse(zero.matches("0.4.0"));
    }

    @Test
    void supportsMavenStyleIntervals() {
        VersionRange halfOpen = VersionRange.parse("[1.20,1.22)");
        assertTrue(halfOpen.matches("1.20"));
        assertTrue(halfOpen.matches("1.21.4"));
        assertFalse(halfOpen.matches("1.22"));

        VersionRange closed = VersionRange.parse("[1.20,1.22]");
        assertTrue(closed.matches("1.22"));

        VersionRange openLower = VersionRange.parse("(1.20,1.22]");
        assertFalse(openLower.matches("1.20"));

        VersionRange unbounded = VersionRange.parse("[1.20,)");
        assertTrue(unbounded.matches("9.9.9"));
    }

    @Test
    void supportsUnions() {
        VersionRange union = VersionRange.parse("1.20.x || 1.21.x");
        assertTrue(union.matches("1.20.4"));
        assertTrue(union.matches("1.21.1"));
        assertFalse(union.matches("1.19.4"));
        assertFalse(union.matches("1.22.0"));
    }

    @Test
    void snapshotsOnlySatisfyAnExactMatch() {
        assertTrue(VersionRange.parse("24w14a").matches("24w14a"));
        assertFalse(VersionRange.parse(">=1.20").matches("24w14a"));
        assertTrue(VersionRange.parse("*").matches("24w14a"));
    }

    @Test
    void aTypoIsAHardErrorRatherThanASilentNoMatch() {
        assertThrows(InvalidVersionException.class, () -> VersionRange.parse(">="));
        assertThrows(InvalidVersionException.class, () -> VersionRange.parse("[1.20]"));
        assertThrows(InvalidVersionException.class, () -> VersionRange.parse(""));
        assertThrows(InvalidVersionException.class, () -> VersionRange.parse("1.20 || "));
    }
}
