package dev.remod.adapter;

import dev.remod.loader.adapter.MinecraftVersionAdapter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionSupportTableTest {

    @Test
    void supportsTheModernReleaseSeries() {
        assertEquals(MinecraftVersionAdapter.Support.FULL,
                VersionSupportTable.supportFor("1.21.4"));
        assertEquals(MinecraftVersionAdapter.Support.FULL,
                VersionSupportTable.supportFor("1.20.1"));
        assertEquals(MinecraftVersionAdapter.Support.FULL,
                VersionSupportTable.supportFor("1.19"));
    }

    @Test
    void marksOlderModernSeriesAsPartial() {
        assertEquals(MinecraftVersionAdapter.Support.PARTIAL,
                VersionSupportTable.supportFor("1.18.2"));
        assertEquals(MinecraftVersionAdapter.Support.PARTIAL,
                VersionSupportTable.supportFor("1.17.1"));
    }

    @Test
    void refusesVersionsOlderThanTheSupportedFloor() {
        assertEquals(MinecraftVersionAdapter.Support.UNSUPPORTED,
                VersionSupportTable.supportFor("1.16.5"));
        assertEquals(MinecraftVersionAdapter.Support.UNSUPPORTED,
                VersionSupportTable.supportFor("1.12.2"));
        assertEquals(MinecraftVersionAdapter.Support.UNSUPPORTED,
                VersionSupportTable.supportFor("1.7.10"));
        assertFalse(VersionSupportTable.isInstallable("1.8.9"));
        assertTrue(VersionSupportTable.describe("1.8.9").contains("1.17 or newer"));
    }

    @Test
    void refusesWeeklySnapshotsRatherThanGuessingTheirSeries() {
        assertEquals(MinecraftVersionAdapter.Support.UNSUPPORTED,
                VersionSupportTable.supportFor("24w14a"));
        assertTrue(VersionSupportTable.describe("24w14a").contains("Weekly snapshots"));
    }

    @Test
    void treatsAFutureSeriesAsPartialRatherThanClaimingOrRefusing() {
        assertEquals(MinecraftVersionAdapter.Support.PARTIAL,
                VersionSupportTable.supportFor("1.99.0"));
        assertTrue(VersionSupportTable.describe("1.99.0").contains("Newer than any Minecraft"));
    }

    @Test
    void handlesPreReleasesByTheirSeries() {
        assertEquals(MinecraftVersionAdapter.Support.FULL,
                VersionSupportTable.supportFor("1.21-pre1"));
        assertEquals(MinecraftVersionAdapter.Support.FULL,
                VersionSupportTable.supportFor("1.20.2-rc2"));
    }

    @Test
    void refusesNullAndEmptyInput() {
        assertEquals(MinecraftVersionAdapter.Support.UNSUPPORTED,
                VersionSupportTable.supportFor(null));
        assertEquals(MinecraftVersionAdapter.Support.UNSUPPORTED,
                VersionSupportTable.supportFor(""));
    }

    @Test
    void everyDescriptionIsUsefulToAUser() {
        for (String version : new String[]{"1.21.4", "1.18.2", "1.8.9", "24w14a", "1.99.0"}) {
            String description = VersionSupportTable.describe(version);
            assertTrue(description.length() > 30, version + ": " + description);
            assertTrue(description.endsWith("."), version + ": " + description);
        }
    }
}
