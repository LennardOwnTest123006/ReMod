package dev.remod.installer.gui;

import dev.remod.adapter.VersionSupportTable;
import dev.remod.installer.manifest.MinecraftVersionEntry;
import dev.remod.loader.adapter.MinecraftVersionAdapter;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The version list's data model: filtering, searching and support labelling.
 *
 * <p>Kept apart from the window so the filtering logic is unit-testable without
 * a display -- which matters, because "the list showed the wrong versions" is a
 * bug users cannot easily report.</p>
 */
public final class VersionTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    private static final String[] COLUMNS = {"Version", "Type", "Released", "ReMod support"};

    private final List<MinecraftVersionEntry> all = new ArrayList<>();
    private final List<MinecraftVersionEntry> visible = new ArrayList<>();
    private String search = "";
    private Filter filter = Filter.RELEASES;
    private boolean hideUnsupported = true;

    /** Which kinds of version to show. */
    public enum Filter {
        RELEASES("Releases"),
        SNAPSHOTS("Snapshots"),
        ALL("All versions");

        private final String label;

        Filter(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Replaces the backing list and reapplies the current filters. */
    public void setVersions(List<MinecraftVersionEntry> versions) {
        all.clear();
        all.addAll(versions);
        reapply();
    }

    public void setSearch(String value) {
        this.search = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        reapply();
    }

    public void setFilter(Filter value) {
        this.filter = value == null ? Filter.RELEASES : value;
        reapply();
    }

    /** When true, versions ReMod cannot install are omitted entirely. */
    public void setHideUnsupported(boolean value) {
        this.hideUnsupported = value;
        reapply();
    }

    public boolean hideUnsupported() {
        return hideUnsupported;
    }

    private void reapply() {
        visible.clear();
        for (MinecraftVersionEntry entry : all) {
            if (!matchesFilter(entry) || !matchesSearch(entry)) {
                continue;
            }
            if (hideUnsupported && !VersionSupportTable.isInstallable(entry.id())) {
                continue;
            }
            visible.add(entry);
        }
        fireTableDataChanged();
    }

    private boolean matchesFilter(MinecraftVersionEntry entry) {
        switch (filter) {
            case RELEASES:
                return entry.type() == MinecraftVersionEntry.Type.RELEASE;
            case SNAPSHOTS:
                return entry.type() == MinecraftVersionEntry.Type.SNAPSHOT;
            default:
                return true;
        }
    }

    private boolean matchesSearch(MinecraftVersionEntry entry) {
        return search.isEmpty() || entry.id().toLowerCase(Locale.ROOT).contains(search);
    }

    /** The entries currently shown. */
    public List<MinecraftVersionEntry> visibleEntries() {
        return Collections.unmodifiableList(new ArrayList<>(visible));
    }

    public MinecraftVersionEntry entryAt(int row) {
        return row >= 0 && row < visible.size() ? visible.get(row) : null;
    }

    /** The row showing {@code versionId}, or -1 when it is not visible. */
    public int rowOf(String versionId) {
        for (int i = 0; i < visible.size(); i++) {
            if (visible.get(i).id().equals(versionId)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getRowCount() {
        return visible.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int row, int column) {
        MinecraftVersionEntry entry = visible.get(row);
        switch (column) {
            case 0: return entry.id();
            case 1: return entry.type().label();
            case 2: return entry.releaseDate();
            default: return supportLabel(entry.id());
        }
    }

    /** The short support label shown in the last column. */
    public static String supportLabel(String versionId) {
        MinecraftVersionAdapter.Support support = VersionSupportTable.supportFor(versionId);
        switch (support) {
            case EXACT:
            case FULL:
                return "Supported";
            case PARTIAL:
                return "Partial";
            default:
                return "Not supported";
        }
    }
}
