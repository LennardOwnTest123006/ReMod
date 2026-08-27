package dev.remod.installer.gui;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;

/**
 * The installer's visual language, in one place.
 *
 * <p>Plain Swing with the system look and feel, deliberately: ReMod.jar has to
 * be a single self-contained download that a user double-clicks, and pulling in
 * a UI toolkit would multiply its size for a window with one list and three
 * buttons. The palette below is what makes it look considered rather than
 * default.</p>
 */
public final class Theme {

    /** ReMod's accent colour, used for the header and primary action. */
    public static final Color ACCENT = new Color(0x2E7D5B);
    public static final Color ACCENT_DARK = new Color(0x1F5A40);
    public static final Color BACKGROUND = new Color(0xF5F6F5);
    public static final Color PANEL = Color.WHITE;
    public static final Color TEXT = new Color(0x1C1F1D);
    public static final Color TEXT_MUTED = new Color(0x5F6B64);
    public static final Color DIVIDER = new Color(0xDCE1DE);
    public static final Color ERROR = new Color(0xB3261E);
    public static final Color WARNING = new Color(0x8A6100);
    public static final Color SUCCESS = new Color(0x2E7D5B);

    private Theme() {
    }

    /** Applies the system look and feel. Safe to call on a headless JVM. */
    public static void install() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            UIManager.put("Button.arc", 8);
            UIManager.put("Component.focusWidth", 1);
        } catch (Exception e) {
            // The cross-platform look and feel is a perfectly good fallback.
        }
    }

    /** True when a GUI cannot be shown, so callers can fall back to the CLI. */
    public static boolean isHeadless() {
        return GraphicsEnvironment.isHeadless();
    }

    public static Font titleFont() {
        return derive(Font.BOLD, 22);
    }

    public static Font headingFont() {
        return derive(Font.BOLD, 13);
    }

    public static Font bodyFont() {
        return derive(Font.PLAIN, 13);
    }

    public static Font smallFont() {
        return derive(Font.PLAIN, 11);
    }

    public static Font monospaceFont() {
        return new Font(Font.MONOSPACED, Font.PLAIN, 12);
    }

    private static Font derive(int style, int size) {
        Font base = UIManager.getFont("Label.font");
        if (base == null) {
            base = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        }
        return base.deriveFont(style, size);
    }

    /** Padding, in the amounts the installer's layout uses. */
    public static Border padding(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    /** A card: white panel, hairline border, inner padding. */
    public static Border card() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DIVIDER),
                padding(12, 12, 12, 12));
    }

    /** Applies the body font and standard foreground to a component. */
    public static <T extends JComponent> T body(T component) {
        component.setFont(bodyFont());
        component.setForeground(TEXT);
        return component;
    }

    /** Applies the muted small font, for secondary text. */
    public static <T extends JComponent> T muted(T component) {
        component.setFont(smallFont());
        component.setForeground(TEXT_MUTED);
        return component;
    }
}
