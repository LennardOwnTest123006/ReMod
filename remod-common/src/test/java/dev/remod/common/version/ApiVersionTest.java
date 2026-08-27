package dev.remod.common.version;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiVersionTest {

    @Test
    void parsesSeriesAndBaseline() {
        ApiVersion version = ApiVersion.parse("1.21-1.0.0");
        assertEquals("1.21", version.minecraftSeries());
        assertEquals("1.0.0", version.baseline().raw());
        assertEquals("1.21-1.0.0", version.toString());
    }

    @Test
    void aDifferentMinecraftSeriesIsNeverSubstitutable() {
        ApiVersion installed = ApiVersion.parse("1.21-1.0.0");
        assertFalse(installed.satisfies(ApiVersion.parse("1.20-1.0.0")));
        assertTrue(installed.satisfies(ApiVersion.parse("1.21-1.0.0")));
    }

    @Test
    void withinASeriesTheBaselineFollowsSemver() {
        ApiVersion installed = ApiVersion.parse("1.21-1.4.0");
        assertTrue(installed.satisfies(ApiVersion.parse("1.21-1.0.0")));
        assertTrue(installed.satisfies(ApiVersion.parse("1.21-1.4.0")));
        // Newer than installed: the mod needs API features we do not have.
        assertFalse(installed.satisfies(ApiVersion.parse("1.21-1.5.0")));
        // A different major is a breaking API change.
        assertFalse(installed.satisfies(ApiVersion.parse("1.21-2.0.0")));
        assertFalse(ApiVersion.parse("1.21-2.0.0").satisfies(ApiVersion.parse("1.21-1.0.0")));
    }

    @Test
    void rejectsMalformedInputWithAnActionableMessage() {
        InvalidVersionException error =
                assertThrows(InvalidVersionException.class, () -> ApiVersion.parse("1.21"));
        assertTrue(error.getMessage().contains("1.21-1.0.0"), error.getMessage());
        assertThrows(InvalidVersionException.class, () -> ApiVersion.parse(""));
        org.junit.jupiter.api.Assertions.assertNull(ApiVersion.tryParse("nonsense"));
    }
}
