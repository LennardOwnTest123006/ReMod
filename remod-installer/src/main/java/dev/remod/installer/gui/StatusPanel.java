package dev.remod.installer.gui;

import dev.remod.common.log.LogFormat;
import dev.remod.common.log.LogLevel;
import dev.remod.common.log.LogRecord;
import dev.remod.common.log.MemoryLogSink;

import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;

/**
 * The status area: a progress bar, a one-line summary and a scrolling log.
 *
 * <p>Wired to ReMod's own logging, so what the user sees here is exactly what
 * goes into {@code remod/logs/} -- there is no second, prettier version of the
 * truth.</p>
 */
public final class StatusPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final int MAX_LOG_CHARACTERS = 200_000;

    private final JLabel summary = new JLabel(" ");
    private final JProgressBar progress = new JProgressBar();
    private final JTextArea log = new JTextArea();
    private final MemoryLogSink sink = new MemoryLogSink(1000);

    public StatusPanel() {
        super(new BorderLayout(0, 8));
        setOpaque(false);

        Theme.body(summary);
        summary.setFont(Theme.headingFont());

        progress.setIndeterminate(false);
        progress.setStringPainted(false);
        progress.setPreferredSize(new Dimension(0, 6));
        progress.setBorderPainted(false);
        progress.setForeground(Theme.ACCENT);

        log.setEditable(false);
        log.setFont(Theme.monospaceFont());
        log.setForeground(Theme.TEXT_MUTED);
        log.setBackground(Theme.PANEL);
        log.setLineWrap(true);
        log.setWrapStyleWord(true);

        JScrollPane scroll = new JScrollPane(log);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.DIVIDER));
        scroll.setPreferredSize(new Dimension(0, 150));

        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.setOpaque(false);
        top.add(summary, BorderLayout.NORTH);
        top.add(progress, BorderLayout.SOUTH);

        add(top, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);

        // Everything ReMod logs from now on appears here too.
        sink.addListener(this::appendRecord);
    }

    /** The sink to register with {@code ReModLog} so this panel receives output. */
    public MemoryLogSink sink() {
        return sink;
    }

    /** Sets the one-line summary, colour-coded by severity. */
    public void status(String message, LogLevel level) {
        SwingUtilities.invokeLater(() -> {
            summary.setText(message == null ? " " : message);
            summary.setForeground(colourFor(level));
        });
    }

    private static java.awt.Color colourFor(LogLevel level) {
        if (level == null) {
            return Theme.TEXT;
        }
        switch (level) {
            case ERROR: return Theme.ERROR;
            case WARN:  return Theme.WARNING;
            default:    return Theme.TEXT;
        }
    }

    /** Shows an indeterminate progress bar while a long operation runs. */
    public void busy(boolean running) {
        SwingUtilities.invokeLater(() -> {
            progress.setIndeterminate(running);
            if (!running) {
                progress.setValue(0);
            }
        });
    }

    /** Shows determinate progress. */
    public void progress(long done, long total) {
        SwingUtilities.invokeLater(() -> {
            if (total <= 0) {
                progress.setIndeterminate(true);
                return;
            }
            progress.setIndeterminate(false);
            progress.setMaximum(100);
            progress.setValue((int) Math.max(0, Math.min(100, done * 100 / total)));
        });
    }

    /** Appends a line to the log view. */
    public void append(String line) {
        SwingUtilities.invokeLater(() -> {
            log.append(line + System.lineSeparator());
            trim();
            log.setCaretPosition(log.getDocument().getLength());
        });
    }

    private void appendRecord(LogRecord record) {
        append(LogFormat.line(record));
    }

    /** Keeps the buffer bounded so a long session cannot grow without limit. */
    private void trim() {
        int length = log.getDocument().getLength();
        if (length > MAX_LOG_CHARACTERS) {
            try {
                log.getDocument().remove(0, length - MAX_LOG_CHARACTERS);
            } catch (javax.swing.text.BadLocationException e) {
                log.setText("");
            }
        }
    }

    /** The log text, for copying into a bug report. */
    public String logText() {
        return log.getText();
    }

    public void clear() {
        SwingUtilities.invokeLater(() -> log.setText(""));
    }
}
