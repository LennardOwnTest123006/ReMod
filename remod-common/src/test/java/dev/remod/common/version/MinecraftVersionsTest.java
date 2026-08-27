package dev.remod.common.version;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftVersionsTest {

    @Test
    void classifiesReleaseIds() {
        assertTrue(MinecraftVersions.isStableRelease("1.21"));
        assertTrue(MinecraftVersions.isStableRelease("1.21.4"));
        assertFalse(MinecraftVersions.isStableRelease("24w14a"));
        assertFalse(MinecraftVersions.isStableRelease("1.21-rc1"));
    }

    @Test
    void classifiesSnapshotsAndPreReleases() {
        assertTrue(MinecraftVersions.isWeeklySnapshot("24w14a"));
        assertTrue(MinecraftVersions.isWeeklySnapshot("21w03a"));
        assertFalse(MinecraftVersions.isWeeklySnapshot("1.21"));

        assertTrue(MinecraftVersions.isPreRelease("1.21-pre1"));
        assertTrue(MinecraftVersions.isPreRelease("1.20.2-rc2"));
        assertFalse(MinecraftVersions.isPreRelease("1.20.2"));
    }

    @Test
    void derivesTheReleaseSeries() {
        assertEquals("1.21", MinecraftVersions.series("1.21.4"));
        assertEquals("1.21", MinecraftVersions.series("1.21"));
        assertEquals("1.20", MinecraftVersions.series("1.20.2-rc2"));
        assertEquals("1.21", MinecraftVersions.series("1.21-pre1"));
    }

    @Test
    void refusesToGuessTheSeriesOfASnapshot() {
        // 24w14a's target release is not derivable from its name, and guessing
        // would let ReMod claim support it cannot honour.
        assertNull(MinecraftVersions.series("24w14a"));
        assertNull(MinecraftVersions.series("rd-132211"));
        assertNull(MinecraftVersions.series("1.14.4 Combat Test"));
        assertNull(MinecraftVersions.series(null));
    }
}
