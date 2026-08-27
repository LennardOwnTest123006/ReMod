package dev.remod.installer.gui;

import dev.remod.installer.manifest.MinecraftVersionEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The version list's filtering is tested without a display, because "the list
 * showed the wrong versions" is not a bug users can easily report.
 */
class VersionTableModelTest {

    private VersionTableModel model;

    private static MinecraftVersionEntry entry(String id, MinecraftVersionEntry.Type type) {
        return new MinecraftVersionEntry(id, type, "https://example.invalid/" + id, "sha",
                Instant.parse("2024-01-01T00:00:00Z"));
    }

    @BeforeEach
    void setUp() {
        model = new VersionTableModel();
        model.setVersions(List.of(
                entry("25w02a", MinecraftVersionEntry.Type.SNAPSHOT),
                entry("1.21.4", MinecraftVersionEntry.Type.RELEASE),
                entry("1.20.1", MinecraftVersionEntry.Type.RELEASE),
                entry("1.18.2", MinecraftVersionEntry.Type.RELEASE),
                entry("1.12.2", MinecraftVersionEntry.Type.RELEASE),
                entry("b1.7.3", MinecraftVersionEntry.Type.OLD_BETA)));
    }

    @Test
    void showsInstallableReleasesByDefault() {
        // 1.12.2 and b1.7.3 are below ReMod's floor and hidden by default.
        assertEquals(3, model.getRowCount());
        assertEquals("1.21.4", model.getValueAt(0, 0));
        assertEquals("1.18.2", model.getValueAt(2, 0));
    }

    @Test
    void showsUnsupportedVersionsWhenAskedAndLabelsThem() {
        model.setHideUnsupported(false);

        // Four releases; the snapshot and the beta are excluded by the filter.
        assertEquals(4, model.getRowCount());
        assertEquals("Not supported", model.getValueAt(model.rowOf("1.12.2"), 3));
        assertEquals("Supported", model.getValueAt(model.rowOf("1.21.4"), 3));
        assertEquals("Partial", model.getValueAt(model.rowOf("1.18.2"), 3));
    }

    @Test
    void filtersBySnapshotAndAll() {
        model.setHideUnsupported(false);
        model.setFilter(VersionTableModel.Filter.SNAPSHOTS);
        assertEquals(1, model.getRowCount());
        assertEquals("25w02a", model.getValueAt(0, 0));

        model.setFilter(VersionTableModel.Filter.ALL);
        assertEquals(6, model.getRowCount());
    }

    @Test
    void searchNarrowsTheList() {
        model.setSearch("1.2");
        assertEquals(2, model.getRowCount());

        model.setSearch("1.21");
        assertEquals(1, model.getRowCount());
        assertEquals("1.21.4", model.getValueAt(0, 0));

        model.setSearch("");
        assertEquals(3, model.getRowCount());
    }

    @Test
    void searchAndFilterCombine() {
        model.setFilter(VersionTableModel.Filter.ALL);
        model.setHideUnsupported(false);
        model.setSearch("w02");

        assertEquals(1, model.getRowCount());
        assertEquals("25w02a", model.getValueAt(0, 0));
    }

    @Test
    void rowLookupReportsHiddenVersionsAsAbsent() {
        assertTrue(model.rowOf("1.21.4") >= 0);
        assertEquals(-1, model.rowOf("1.12.2"));
        assertEquals(-1, model.rowOf("does-not-exist"));
    }

    @Test
    void exposesEveryColumnTheUiNeeds() {
        assertEquals(4, model.getColumnCount());
        assertEquals("Version", model.getColumnName(0));
        assertEquals("ReMod support", model.getColumnName(3));
        assertEquals("Release", model.getValueAt(0, 1));
        assertFalse(String.valueOf(model.getValueAt(0, 2)).isEmpty());
    }

    @Test
    void entryLookupIsBoundsSafe() {
        org.junit.jupiter.api.Assertions.assertNull(model.entryAt(-1));
        org.junit.jupiter.api.Assertions.assertNull(model.entryAt(999));
        assertEquals("1.21.4", model.entryAt(0).id());
    }
}
