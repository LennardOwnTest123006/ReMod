package dev.remod.installer.gui;

import dev.remod.adapter.VersionSupportTable;
import dev.remod.common.io.Platform;
import dev.remod.common.log.LogLevel;
import dev.remod.common.log.ReModLog;
import dev.remod.common.log.ReModLogger;
import dev.remod.installer.install.InstallException;
import dev.remod.installer.install.InstallRequest;
import dev.remod.installer.install.InstallResult;
import dev.remod.installer.install.InstalledVersions;
import dev.remod.installer.install.ReModInstaller;
import dev.remod.installer.install.ReModUninstaller;
import dev.remod.installer.manifest.ManifestException;
import dev.remod.installer.manifest.MinecraftVersionEntry;
import dev.remod.installer.manifest.MinecraftVersionManifest;
import dev.remod.installer.manifest.VersionManifestService;
import dev.remod.loader.ReModPaths;
import dev.remod.loader.ReModVersions;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * The window a user sees when they double-click {@code ReMod.jar}.
 *
 * <p>Everything the task list asks for is here -- version search, the version
 * list with a release/snapshot filter, the selected version, an Install button,
 * a status area, installed versions, uninstall, shortcuts to the mods and game
 * folders, developer tools and an about panel -- and nothing requires the user
 * to edit a JSON file.</p>
 *
 * <p>Every long operation (fetching the manifest, installing, uninstalling)
 * runs on a {@link SwingWorker}, so the window never freezes and the status
 * area stays live.</p>
 */
public final class InstallerWindow extends JFrame {

    private static final long serialVersionUID = 1L;
    private static final ReModLogger LOG = ReModLog.get("ReMod/Installer");

    private final VersionManifestService manifestService;
    private final ReModInstaller installer;

    private final VersionTableModel versionModel = new VersionTableModel();
    private final JTable versionTable = new JTable(versionModel);
    private final JTextField searchField = new JTextField();
    private final JComboBox<VersionTableModel.Filter> filterBox =
            new JComboBox<>(VersionTableModel.Filter.values());
    private final JCheckBox hideUnsupported = new JCheckBox("Hide versions ReMod cannot install");
    private final JLabel selectedLabel = new JLabel("No version selected");
    private final JLabel supportLabel = new JLabel(" ");
    private final JTextField directoryField = new JTextField();
    private final JButton installButton = new JButton("Install ReMod");
    private final JButton uninstallButton = new JButton("Uninstall");
    private final JLabel installedLabel = new JLabel(" ");
    private final StatusPanel status = new StatusPanel();

    public InstallerWindow(VersionManifestService manifestService, ReModInstaller installer) {
        super("ReMod " + ReModVersions.loaderVersion() + " Installer");
        this.manifestService = manifestService;
        this.installer = installer;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(760, 620));
        getContentPane().setBackground(Theme.BACKGROUND);
        setLayout(new BorderLayout());

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        ReModLog.addSink(status.sink());
        wireEvents();
        directoryField.setText(defaultDirectory().toString());
        refreshInstalled();
        pack();
        setLocationRelativeTo(null);
    }

    private static Path defaultDirectory() {
        Path existing = Platform.findExistingMinecraftDirectory();
        return existing != null ? existing : Platform.defaultMinecraftDirectory();
    }

    // --- layout -----------------------------------------------------------

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.ACCENT);
        header.setBorder(Theme.padding(18, 24, 18, 24));

        JLabel title = new JLabel("ReMod");
        title.setFont(Theme.titleFont());
        title.setForeground(Color.WHITE);

        JLabel subtitle = new JLabel("A mod loader for Minecraft: Java Edition  ·  Loader "
                + ReModVersions.loaderVersion() + "  ·  API baseline "
                + ReModVersions.apiBaseline());
        subtitle.setFont(Theme.smallFont());
        subtitle.setForeground(new Color(0xD6EBE0));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(title);
        text.add(Box.createVerticalStrut(4));
        text.add(subtitle);
        header.add(text, BorderLayout.WEST);
        return header;
    }

    private JComponent buildBody() {
        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.setBorder(Theme.padding(16, 24, 8, 24));
        body.add(buildVersionSection(), BorderLayout.CENTER);
        body.add(buildTargetSection(), BorderLayout.SOUTH);
        return body;
    }

    private JComponent buildVersionSection() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(Theme.PANEL);
        panel.setBorder(Theme.card());

        JLabel heading = new JLabel("Minecraft version");
        heading.setFont(Theme.headingFont());

        searchField.setFont(Theme.bodyFont());
        searchField.setToolTipText("Type to filter the version list");
        searchField.putClientProperty("JTextField.placeholderText", "Search version...");
        searchField.setPreferredSize(new Dimension(220, 28));

        filterBox.setFont(Theme.bodyFont());
        filterBox.setSelectedItem(VersionTableModel.Filter.RELEASES);
        hideUnsupported.setSelected(true);
        hideUnsupported.setFont(Theme.smallFont());
        hideUnsupported.setOpaque(false);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.setOpaque(false);
        controls.add(new JLabel("Search:"));
        controls.add(searchField);
        controls.add(new JLabel("Show:"));
        controls.add(filterBox);
        controls.add(hideUnsupported);

        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.setOpaque(false);
        top.add(heading, BorderLayout.NORTH);
        top.add(controls, BorderLayout.SOUTH);

        versionTable.setFont(Theme.bodyFont());
        versionTable.setRowHeight(24);
        versionTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        versionTable.setAutoCreateRowSorter(false);
        versionTable.getTableHeader().setFont(Theme.smallFont());
        versionTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        versionTable.getColumnModel().getColumn(1).setPreferredWidth(90);
        versionTable.getColumnModel().getColumn(2).setPreferredWidth(110);
        versionTable.getColumnModel().getColumn(3).setPreferredWidth(160);
        versionTable.getColumnModel().getColumn(3).setCellRenderer(new SupportRenderer());

        JScrollPane scroll = new JScrollPane(versionTable);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.DIVIDER));
        scroll.setPreferredSize(new Dimension(0, 220));

        panel.add(top, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(buildSelectionSummary(), BorderLayout.SOUTH);
        return panel;
    }

    private JComponent buildSelectionSummary() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(Theme.padding(8, 0, 0, 0));
        selectedLabel.setFont(Theme.headingFont());
        Theme.muted(supportLabel);
        selectedLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        supportLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(selectedLabel);
        panel.add(Box.createVerticalStrut(2));
        panel.add(supportLabel);
        return panel;
    }

    private JComponent buildTargetSection() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(Theme.PANEL);
        panel.setBorder(Theme.card());

        JLabel heading = new JLabel("Minecraft folder");
        heading.setFont(Theme.headingFont());
        directoryField.setFont(Theme.bodyFont());

        JButton browse = new JButton("Browse...");
        browse.addActionListener(event -> chooseDirectory());

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.add(directoryField, BorderLayout.CENTER);
        row.add(browse, BorderLayout.EAST);

        Theme.muted(installedLabel);

        JPanel content = new JPanel(new BorderLayout(0, 6));
        content.setOpaque(false);
        content.add(heading, BorderLayout.NORTH);
        content.add(row, BorderLayout.CENTER);
        content.add(installedLabel, BorderLayout.SOUTH);
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildFooter() {
        JPanel footer = new JPanel(new BorderLayout(0, 10));
        footer.setOpaque(false);
        footer.setBorder(Theme.padding(0, 24, 18, 24));

        status.setBorder(Theme.padding(0, 0, 8, 0));
        footer.add(status, BorderLayout.CENTER);

        installButton.setFont(Theme.headingFont());
        installButton.setBackground(Theme.ACCENT);
        installButton.setForeground(Color.WHITE);
        installButton.setOpaque(true);
        installButton.setBorderPainted(false);
        installButton.setEnabled(false);
        installButton.setPreferredSize(new Dimension(160, 34));

        uninstallButton.setEnabled(false);

        JButton modsFolder = new JButton("Open mods folder");
        modsFolder.addActionListener(event -> open(currentPaths().modsDirectory()));
        JButton gameFolder = new JButton("Open game folder");
        gameFolder.addActionListener(event -> open(currentPaths().gameDirectory()));
        JButton developer = new JButton("Developer tools");
        developer.addActionListener(event -> showDeveloperTools());
        JButton about = new JButton("About");
        about.addActionListener(event -> showAbout());

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(modsFolder);
        left.add(gameFolder);
        left.add(developer);
        left.add(about);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(uninstallButton);
        right.add(installButton);

        JPanel buttons = new JPanel(new BorderLayout());
        buttons.setOpaque(false);
        buttons.add(left, BorderLayout.WEST);
        buttons.add(right, BorderLayout.EAST);
        footer.add(buttons, BorderLayout.SOUTH);
        return footer;
    }

    // --- behaviour --------------------------------------------------------

    private void wireEvents() {
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                versionModel.setSearch(searchField.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                versionModel.setSearch(searchField.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                versionModel.setSearch(searchField.getText());
            }
        });
        filterBox.addActionListener(event ->
                versionModel.setFilter((VersionTableModel.Filter) filterBox.getSelectedItem()));
        hideUnsupported.addActionListener(event ->
                versionModel.setHideUnsupported(hideUnsupported.isSelected()));
        versionTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                onSelectionChanged();
            }
        });
        directoryField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refreshInstalled();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refreshInstalled();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refreshInstalled();
            }
        });
        installButton.addActionListener(event -> runInstall());
        uninstallButton.addActionListener(event -> runUninstall());
    }

    /** Loads the version list. Called once the window is on screen. */
    public void loadVersions() {
        status.status("Fetching the Minecraft version list...", LogLevel.INFO);
        status.busy(true);
        new SwingWorker<MinecraftVersionManifest, Void>() {
            @Override
            protected MinecraftVersionManifest doInBackground() {
                return manifestService.get();
            }

            @Override
            protected void done() {
                status.busy(false);
                try {
                    MinecraftVersionManifest manifest = get();
                    versionModel.setVersions(manifest.versions());
                    status.status("Loaded " + manifest.size() + " Minecraft versions.",
                            LogLevel.INFO);
                    manifest.latestRelease().ifPresent(InstallerWindow.this::selectVersion);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    String suggestion = cause instanceof ManifestException
                            ? ((ManifestException) cause).suggestion()
                            : "Check your internet connection and try again.";
                    status.status("Could not load the version list.", LogLevel.ERROR);
                    status.append(String.valueOf(cause == null ? e : cause.getMessage()));
                    status.append(suggestion);
                    LOG.error("Version list could not be loaded", cause);
                }
            }
        }.execute();
    }

    private void selectVersion(String versionId) {
        int row = versionModel.rowOf(versionId);
        if (row >= 0) {
            versionTable.setRowSelectionInterval(row, row);
            versionTable.scrollRectToVisible(versionTable.getCellRect(row, 0, true));
        }
    }

    private MinecraftVersionEntry selectedEntry() {
        return versionModel.entryAt(versionTable.getSelectedRow());
    }

    private void onSelectionChanged() {
        MinecraftVersionEntry entry = selectedEntry();
        if (entry == null) {
            selectedLabel.setText("No version selected");
            supportLabel.setText(" ");
            installButton.setEnabled(false);
            uninstallButton.setEnabled(false);
            return;
        }
        selectedLabel.setText("Selected: Minecraft " + entry.id()
                + "   ·   ReMod API " + apiLabel(entry.id()));
        supportLabel.setText("<html><body style='width:640px'>"
                + VersionSupportTable.describe(entry.id()) + "</body></html>");
        installButton.setEnabled(VersionSupportTable.isInstallable(entry.id()));
        refreshInstalled();
    }

    private static String apiLabel(String versionId) {
        return java.util.Optional.ofNullable(ReModVersions.apiVersionFor(versionId))
                .map(Object::toString).orElse("unavailable");
    }

    private ReModPaths currentPaths() {
        return new ReModPaths(java.nio.file.Paths.get(directoryField.getText().trim()));
    }

    private void refreshInstalled() {
        try {
            ReModPaths paths = currentPaths();
            List<InstalledVersions.Installed> installed = InstalledVersions.scan(paths);
            if (installed.isEmpty()) {
                installedLabel.setText("No ReMod installations found in this folder.");
            } else {
                StringBuilder sb = new StringBuilder("Installed: ");
                for (int i = 0; i < installed.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(installed.get(i).versionId());
                }
                installedLabel.setText(sb.toString());
            }
            MinecraftVersionEntry entry = selectedEntry();
            uninstallButton.setEnabled(entry != null
                    && InstalledVersions.isInstalled(paths, entry.id()));
        } catch (RuntimeException e) {
            installedLabel.setText("That folder could not be read.");
            uninstallButton.setEnabled(false);
        }
    }

    private void runInstall() {
        MinecraftVersionEntry entry = selectedEntry();
        if (entry == null) {
            return;
        }
        InstallRequest request = InstallRequest.builder(entry.id(),
                java.nio.file.Paths.get(directoryField.getText().trim())).build();

        installButton.setEnabled(false);
        uninstallButton.setEnabled(false);
        status.busy(true);
        status.status("Installing ReMod for Minecraft " + entry.id() + "...", LogLevel.INFO);

        new SwingWorker<InstallResult, Void>() {
            @Override
            protected InstallResult doInBackground() {
                return installer.install(request,
                        (what, done, total) -> status.append("  " + what));
            }

            @Override
            protected void done() {
                status.busy(false);
                try {
                    InstallResult result = get();
                    status.status("ReMod installed for Minecraft " + result.minecraftVersion()
                            + ".", LogLevel.INFO);
                    showTextDialog("Installation complete", result.summary());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    reportFailure("Install failed", e.getCause());
                } finally {
                    installButton.setEnabled(true);
                    refreshInstalled();
                }
            }
        }.execute();
    }

    private void runUninstall() {
        MinecraftVersionEntry entry = selectedEntry();
        if (entry == null) {
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "Remove the ReMod installation for Minecraft " + entry.id() + "?\n\n"
                        + "Your mods, mod settings and worlds are kept.",
                "Uninstall ReMod", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        status.busy(true);
        new SwingWorker<ReModUninstaller.Result, Void>() {
            @Override
            protected ReModUninstaller.Result doInBackground() {
                return new ReModUninstaller(currentPaths()).uninstall(entry.id());
            }

            @Override
            protected void done() {
                status.busy(false);
                try {
                    ReModUninstaller.Result result = get();
                    status.status("ReMod removed for Minecraft " + entry.id() + ".",
                            LogLevel.INFO);
                    showTextDialog("Uninstall complete", result.summary());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    reportFailure("Uninstall failed", e.getCause());
                } finally {
                    refreshInstalled();
                }
            }
        }.execute();
    }

    private void reportFailure(String title, Throwable cause) {
        String message = cause == null ? "Unknown error" : String.valueOf(cause.getMessage());
        String suggestion = cause instanceof InstallException
                ? ((InstallException) cause).suggestion()
                : "See the log below for details.";
        status.status(title + ": " + message, LogLevel.ERROR);
        status.append(suggestion);
        LOG.error(title, cause);
        showTextDialog(title, message + System.lineSeparator() + System.lineSeparator()
                + suggestion);
    }

    private void chooseDirectory() {
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
        chooser.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select your .minecraft folder");
        chooser.setCurrentDirectory(currentPaths().gameDirectory().toFile());
        if (chooser.showOpenDialog(this) == javax.swing.JFileChooser.APPROVE_OPTION) {
            directoryField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void open(Path path) {
        if (!Platform.openInFileManager(path)) {
            showTextDialog("Folder", "ReMod could not open a file manager here."
                    + System.lineSeparator() + System.lineSeparator() + path);
        }
    }

    private void showDeveloperTools() {
        ReModPaths paths = currentPaths();
        String text = "ReMod Developer Tools" + System.lineSeparator()
                + System.lineSeparator()
                + "Create a new mod project:" + System.lineSeparator()
                + "  java -jar ReMod.jar create MyMod" + System.lineSeparator()
                + System.lineSeparator()
                + "Build it:" + System.lineSeparator()
                + "  cd MyMod && ./gradlew build" + System.lineSeparator()
                + System.lineSeparator()
                + "Load your mods without starting Minecraft:" + System.lineSeparator()
                + "  java -jar ReMod.jar test --mods " + paths.modsDirectory()
                + System.lineSeparator()
                + System.lineSeparator()
                + "Every ReMod command:" + System.lineSeparator()
                + "  java -jar ReMod.jar help" + System.lineSeparator()
                + System.lineSeparator()
                + "ReMod API jars for compiling against:" + System.lineSeparator()
                + "  " + paths.apiDirectory() + System.lineSeparator()
                + System.lineSeparator()
                + "The full walkthrough is in tutorial.txt.";
        showTextDialog("Developer tools", text);
    }

    private void showAbout() {
        String text = "ReMod " + ReModVersions.loaderVersion() + System.lineSeparator()
                + "A mod loader for Minecraft: Java Edition" + System.lineSeparator()
                + System.lineSeparator()
                + "Loader version:   " + ReModVersions.loaderVersion() + System.lineSeparator()
                + "API baseline:     " + ReModVersions.apiBaseline() + System.lineSeparator()
                + "Supported:        Minecraft " + VersionSupportTable.OLDEST_SUPPORTED
                + " and newer" + System.lineSeparator()
                + "Java:             " + System.getProperty("java.version")
                + System.lineSeparator()
                + "Operating system: " + System.getProperty("os.name")
                + System.lineSeparator()
                + System.lineSeparator()
                + "ReMod installs a separate launcher profile and never modifies your"
                + System.lineSeparator()
                + "vanilla Minecraft files, other loaders' installations, or your worlds."
                + System.lineSeparator()
                + System.lineSeparator()
                + "Minecraft is a trademark of Mojang AB. ReMod is an independent project"
                + System.lineSeparator()
                + "and bundles no Minecraft code or assets.";
        showTextDialog("About ReMod", text);
    }

    private void showTextDialog(String title, String body) {
        JTextArea area = new JTextArea(body);
        area.setEditable(false);
        area.setFont(Theme.monospaceFont());
        area.setBackground(Theme.PANEL);
        area.setBorder(Theme.padding(8, 8, 8, 8));
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(640, 320));
        scroll.setBorder(BorderFactory.createLineBorder(Theme.DIVIDER));

        JDialog dialog = new JDialog(this, title, true);
        dialog.setLayout(new BorderLayout());
        dialog.add(scroll, BorderLayout.CENTER);
        JButton close = new JButton("Close");
        close.addActionListener(event -> dialog.dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(close);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    /** Colours the support column so unsupported versions are obvious at a glance. */
    private static final class SupportRenderer extends DefaultTableCellRenderer {

        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean selected, boolean focused,
                                                       int row, int column) {
            Component component = super.getTableCellRendererComponent(table, value, selected,
                    focused, row, column);
            if (!selected) {
                String text = String.valueOf(value);
                if (text.startsWith("Not")) {
                    component.setForeground(Theme.ERROR);
                } else if (text.startsWith("Partial")) {
                    component.setForeground(Theme.WARNING);
                } else {
                    component.setForeground(Theme.SUCCESS);
                }
            }
            component.setFont(component.getFont().deriveFont(Font.PLAIN));
            return component;
        }
    }

    /** Opens the installer window and starts loading the version list. */
    public static void launch() {
        Theme.install();
        SwingUtilities.invokeLater(() -> {
            InstallerWindow window = new InstallerWindow(
                    VersionManifestService.standard(), new ReModInstaller());
            window.setVisible(true);
            window.loadVersions();
        });
    }
}
