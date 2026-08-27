package dev.remod.common.version;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticVersionTest {

    @Test
    void parsesStrictSemver() {
        SemanticVersion version = SemanticVersion.parse("1.2.3-beta.1+build.7");
        assertEquals(1, version.major());
        assertEquals(2, version.minor());
        assertEquals(3, version.patch());
        assertEquals("beta.1", version.preRelease());
        assertEquals("build.7", version.build());
        assertTrue(version.isPreRelease());
    }

    @Test
    void parsesMinecraftShapedVersions() {
        assertEquals("1.21", SemanticVersion.parse("1.21").series());
        assertEquals("1.21", SemanticVersion.parse("1.21.4").series());
        assertEquals(0, SemanticVersion.parse("1.21").patch());
        // 1.21 and 1.21.0 are the same release.
        assertEquals(SemanticVersion.parse("1.21"), SemanticVersion.parse("1.21.0"));
    }

    @Test
    void treatsSnapshotIdentifiersAsOpaque() {
        SemanticVersion snapshot = SemanticVersion.parse("24w14a");
        assertFalse(snapshot.isNumeric());
        assertEquals("24w14a", snapshot.series());
        assertEquals(snapshot, SemanticVersion.parse("24w14a"));
        assertFalse(snapshot.equals(SemanticVersion.parse("24w15a")));
    }

    @Test
    void ordersVersionsCorrectly() {
        assertTrue(SemanticVersion.parse("1.21.4").compareTo(SemanticVersion.parse("1.21.3")) > 0);
        assertTrue(SemanticVersion.parse("1.21.0").compareTo(SemanticVersion.parse("1.9.9")) > 0);
        assertTrue(SemanticVersion.parse("2.0.0").compareTo(SemanticVersion.parse("1.99.99")) > 0);
    }

    @Test
    void releaseOutranksItsOwnPreRelease() {
        assertTrue(SemanticVersion.parse("1.0.0").compareTo(SemanticVersion.parse("1.0.0-rc1")) > 0);
        assertTrue(SemanticVersion.parse("1.0.0-rc2").compareTo(SemanticVersion.parse("1.0.0-rc1")) > 0);
        assertTrue(SemanticVersion.parse("1.0.0-alpha").compareTo(SemanticVersion.parse("1.0.0-beta")) < 0);
        // Numeric pre-release identifiers sort below alphanumeric ones, per SemVer.
        assertTrue(SemanticVersion.parse("1.0.0-1").compareTo(SemanticVersion.parse("1.0.0-alpha")) < 0);
    }

    @Test
    void rejectsEmptyInput() {
        assertThrows(InvalidVersionException.class, () -> SemanticVersion.parse(""));
        assertThrows(InvalidVersionException.class, () -> SemanticVersion.parse(null));
        org.junit.jupiter.api.Assertions.assertNull(SemanticVersion.tryParse(" "));
    }
}
