package dev.remod.common.version;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiVersionTest {

    @Test
    void parsesAPortableBaseline() {
        ApiVersion version = ApiVersion.parse("1.0.0");

        assertTrue(version.isPortable());
        assertNull(version.minecraftSeries());
        assertEquals("1.0.0", version.baseline().raw());
        assertEquals("1.0.0", version.toString());
    }

    @Test
    void parsesAPinnedSeriesAndBaseline() {
        ApiVersion version = ApiVersion.parse("1.21-1.0.0");

        assertFalse(version.isPortable());
        assertEquals("1.21", version.minecraftSeries());
        assertEquals("1.0.0", version.baseline().raw());
        assertEquals("1.21-1.0.0", version.toString());
    }

    @Test
    void aPortableBaselineWithAPreReleaseIsNotMistakenForAPinnedVersion() {
        // "1.0.0-beta.1" has a dash but "1.0.0" is not a major.minor series.
        ApiVersion version = ApiVersion.parse("1.0.0-beta.1");

        assertTrue(version.isPortable());
        assertEquals("1.0.0-beta.1", version.baseline().raw());
    }

    // --- the point of the portable form ----------------------------------

    @Test
    void aPortableModRunsOnEveryMinecraftSeries() {
        ApiVersion required = ApiVersion.parse("1.0.0");

        assertTrue(ApiVersion.parse("1.21-1.0.0").satisfies(required));
        assertTrue(ApiVersion.parse("1.20-1.0.0").satisfies(required));
        assertTrue(ApiVersion.parse("1.19-1.0.0").satisfies(required));
        assertTrue(ApiVersion.parse("1.17-1.0.0").satisfies(required));
    }

    @Test
    void aPinnedModRunsOnlyOnItsOwnSeries() {
        ApiVersion required = ApiVersion.parse("1.21-1.0.0");

        assertTrue(ApiVersion.parse("1.21-1.0.0").satisfies(required));
        assertFalse(ApiVersion.parse("1.20-1.0.0").satisfies(required));
        assertFalse(ApiVersion.parse("1.19-1.0.0").satisfies(required));
    }

    // --- baseline compatibility, independent of the series ---------------

    @Test
    void theBaselineFollowsSemanticVersioning() {
        ApiVersion installed = ApiVersion.parse("1.21-1.4.0");

        assertTrue(installed.satisfies(ApiVersion.parse("1.0.0")));
        assertTrue(installed.satisfies(ApiVersion.parse("1.4.0")));
        // Newer than installed: the mod needs API features we do not have.
        assertFalse(installed.satisfies(ApiVersion.parse("1.5.0")));
        // A different major is a breaking API change, either way round.
        assertFalse(installed.satisfies(ApiVersion.parse("2.0.0")));
        assertFalse(ApiVersion.parse("1.21-2.0.0").satisfies(ApiVersion.parse("1.0.0")));
    }

    @Test
    void baselineRulesApplyToPinnedVersionsToo() {
        ApiVersion installed = ApiVersion.parse("1.21-1.4.0");

        assertTrue(installed.satisfies(ApiVersion.parse("1.21-1.0.0")));
        assertFalse(installed.satisfies(ApiVersion.parse("1.21-1.9.0")));
        assertFalse(installed.satisfies(ApiVersion.parse("1.20-1.0.0")));
    }

    // --- conversions ------------------------------------------------------

    @Test
    void convertsBetweenPinnedAndPortable() {
        ApiVersion portable = ApiVersion.parse("1.0.0");
        assertEquals("1.21-1.0.0", portable.pinnedTo("1.21").toString());

        ApiVersion pinned = ApiVersion.parse("1.21-1.0.0");
        assertEquals("1.0.0", pinned.asPortable().toString());
        assertTrue(pinned.asPortable().isPortable());
    }

    @Test
    void ofTreatsAnAbsentSeriesAsPortable() {
        assertTrue(ApiVersion.of(null, "1.0.0").isPortable());
        assertTrue(ApiVersion.of("  ", "1.0.0").isPortable());
        assertFalse(ApiVersion.of("1.21", "1.0.0").isPortable());
    }

    // --- errors -----------------------------------------------------------

    @Test
    void rejectsMalformedInputWithAnActionableMessage() {
        InvalidVersionException error =
                assertThrows(InvalidVersionException.class, () -> ApiVersion.parse("nonsense"));
        assertTrue(error.getMessage().contains("1.0.0"), error.getMessage());
        assertTrue(error.getMessage().contains("1.21-1.0.0"), error.getMessage());

        assertThrows(InvalidVersionException.class, () -> ApiVersion.parse(""));
        assertThrows(InvalidVersionException.class, () -> ApiVersion.parse("-1.0.0"));
        assertThrows(InvalidVersionException.class, () -> ApiVersion.parse("1.21-"));
        assertNull(ApiVersion.tryParse("nonsense"));
    }

    @Test
    void nullNeverSatisfiesAnything() {
        assertFalse(ApiVersion.parse("1.0.0").satisfies(null));
    }

    @Test
    void equalityDistinguishesPortableFromPinned() {
        assertEquals(ApiVersion.parse("1.0.0"), ApiVersion.parse("1.0.0"));
        assertEquals(ApiVersion.parse("1.21-1.0.0"), ApiVersion.parse("1.21-1.0.0"));
        org.junit.jupiter.api.Assertions.assertNotEquals(
                ApiVersion.parse("1.0.0"), ApiVersion.parse("1.21-1.0.0"));
    }
}
