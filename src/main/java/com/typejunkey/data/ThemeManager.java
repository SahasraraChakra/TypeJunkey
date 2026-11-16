package com.typejunkey.data;

import com.typejunkey.model.Theme;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Decides which theme is active: custom colours, a random pick, the operating-system choice, or the chosen name. */
public final class ThemeManager {
    private ThemeManager() {}

    private static Theme override;
    private static final Random RND = new Random();
    public static final String[] COLOR_NAMES = {"background", "main", "caret", "sub", "sub background", "text", "error", "extra error", "colorful error", "colorful extra error"};

    public static synchronized Theme active() {
        Settings c = Settings.get();
        if (c.bool("customTheme")) return custom();
        if (override != null) return override;
        if (c.bool("autoSwitchTheme")) return Content.theme(OsTheme.isDark() ? c.str("themeDark") : c.str("themeLight"));
        return Content.theme(c.theme);
    }

    public static Color[] customColors() {
        String[] p = Settings.get().str("customThemeColors").split(",");
        Color[] out = new Color[10];
        Color[] d = defaults();
        for (int i = 0; i < 10; i++) {
            try { out[i] = Color.decode(p[i].trim()); } catch (RuntimeException e) { out[i] = d[i]; }
        }
        return out;
    }

    private static Color[] defaults() {
        String[] h = Settings.def("customThemeColors").def.split(",");
        Color[] out = new Color[10];
        for (int i = 0; i < 10; i++) out[i] = Color.decode(h[i]);
        return out;
    }

    public static Theme custom() {
        Color[] c = customColors();
        return new Theme("custom", c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8], c[9]);
    }

    public static void saveCustom(Color[] c) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < c.length; i++) b.append(i > 0 ? "," : "").append(String.format("#%02x%02x%02x", c[i].getRed(), c[i].getGreen(), c[i].getBlue()));
        Settings.get().setQuiet("customThemeColors", b.toString());
    }

    /** Picks the theme for the next test according to the random theme setting. */
    public static synchronized void randomize() {
        Settings c = Settings.get();
        String mode = c.str("randomTheme");
        if (mode.equals("off")) { override = null; return; }
        List<Theme> pool = new ArrayList<>();
        List<String> favs = c.list("favThemes");
        boolean wantDark = mode.equals("auto") ? OsTheme.isDark() : mode.equals("dark");
        for (Theme t : Content.themes()) {
            switch (mode) {
                case "fav": if (favs.contains(t.name)) pool.add(t); break;
                case "light": if (!t.isDark()) pool.add(t); break;
                case "dark": if (t.isDark()) pool.add(t); break;
                case "auto": if (t.isDark() == wantDark) pool.add(t); break;
                case "custom": break;
                default: pool.add(t);
            }
        }
        if (mode.equals("custom")) { override = custom(); return; }
        if (pool.isEmpty()) pool = Content.themes();
        override = pool.get(RND.nextInt(pool.size()));
    }

    public static synchronized void clearOverride() { override = null; }
}
