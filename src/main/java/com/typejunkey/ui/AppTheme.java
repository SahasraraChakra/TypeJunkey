package com.typejunkey.ui;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLaf;
import com.typejunkey.data.Content;
import com.typejunkey.data.Settings;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.UIManager;

/** Installs and switches the FlatLaf look: IntelliJ (light) or Darcula (dark). */
public final class AppTheme {
    private AppTheme() {}

    private static Boolean dark;

    /** Call once before any window exists. */
    public static void prepare() {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        boolean square = Settings.get().str("frameStyle").equals("square");
        // Without the native library FlatLaf draws its own undecorated, square-cornered frame.
        System.setProperty("flatlaf.useNativeLibrary", square ? "false" : "true");
        JFrame.setDefaultLookAndFeelDecorated(true);
        JDialog.setDefaultLookAndFeelDecorated(true);
        Fonts.init();
    }

    /** Which window theme the settings ask for right now. */
    public static boolean desiredDark() {
        Settings c = Settings.get();
        return c.bool("autoAppTheme") ? com.typejunkey.data.ThemeManager.active().isDark() : c.str("appTheme").equals("dark");
    }

    public static boolean isDark() { return dark != null && dark; }

    /** Switches the whole application to the dark or light FlatLaf theme (no-op if already active). */
    public static void apply(boolean wantDark) {
        if (dark != null && dark == wantDark) return;
        boolean first = dark == null;
        dark = wantDark;
        UIManager.put("TabbedPane.tabHeight", 34);
        UIManager.put("Component.arc", 6);
        UIManager.put("Button.arc", 6);
        UIManager.put("TextComponent.arc", 6);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("TitlePane.unifiedBackground", true);
        if (wantDark) FlatDarculaLaf.setup(); else FlatIntelliJLaf.setup();
        Fonts.applyUiDefaults();
        if (!first) FlatLaf.updateUI();
    }

    /** Re-applies the interface font after a settings change. */
    public static void refreshFonts() {
        Fonts.applyUiDefaults();
        FlatLaf.updateUI();
    }
}
