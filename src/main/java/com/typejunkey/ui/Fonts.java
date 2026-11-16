package com.typejunkey.ui;

import com.typejunkey.data.Settings;
import com.typejunkey.util.Resources;

import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Font catalog: the bundled families (Inter, Cascadia) from the jar, any TTF/OTF dropped into the "fonts"
 * folder next to the jar, and the system fonts. Missing fonts fall back gracefully.
 */
public final class Fonts {
    private Fonts() {}

    private static final String[] WEIGHTS = {"Thin", "ExtraLight", "Light", "Regular", "Medium", "SemiBold", "Bold", "ExtraBold", "Black"};
    private static final Map<String, Map<String, Font>> BUNDLED = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private static Set<String> system;
    private static boolean inited;

    public static synchronized void init() {
        if (inited) return;
        inited = true;
        List<String[]> sources = new ArrayList<>(); // {name, "bundled"|"folder"}
        for (String e : Resources.bundledUnder("fonts")) sources.add(new String[] {e, "bundled"});
        java.nio.file.Path dir = Resources.side("fonts");
        for (String e : Resources.folderUnder(dir)) sources.add(new String[] {e, "folder"});
        for (String[] s : sources) {
            String n = s[0].toLowerCase();
            if (s[0].contains("/") || !(n.endsWith(".ttf") || n.endsWith(".otf"))) continue;
            byte[] b = s[1].equals("bundled") ? Resources.readBundled("fonts/" + s[0]) : Resources.readFile(dir.resolve(s[0]));
            if (b == null) continue;
            try {
                Font f = Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(b));
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(f);
                classify(s[0], f);
            } catch (FontFormatException | IOException | RuntimeException ignored) { }
        }
        if (!BUNDLED.containsKey("Cascadia Mono") && BUNDLED.containsKey("Cascadia Code")) {
            BUNDLED.put("Cascadia Mono", BUNDLED.get("Cascadia Code")); // same metrics; Java applies no ligatures
        }
    }

    private static String spaced(String s) { return s.replaceAll("(?<=[a-z])(?=[A-Z])", " "); }

    private static void classify(String file, Font f) {
        String base = file.substring(0, file.lastIndexOf('.'));
        String style = "Regular";
        int dash = base.indexOf('-');
        if (dash > 0) {
            String part = base.substring(dash + 1);
            base = base.substring(0, dash);
            boolean italic = part.endsWith("Italic");
            String weight = italic ? part.substring(0, part.length() - 6) : part;
            style = weight.isEmpty() ? (italic ? "Italic" : "Regular") : weight + (italic ? " Italic" : "");
        } else {
            base = f.getFamily();
        }
        base = spaced(base);
        Map<String, Font> m = BUNDLED.computeIfAbsent(base, k -> new LinkedHashMap<>());
        m.put(style, f);
    }

    private static synchronized Set<String> system() {
        if (system == null) {
            system = new HashSet<>(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        }
        return system;
    }

    /** All selectable families: bundled first, then system fonts. */
    public static List<String> families() {
        init();
        List<String> out = new ArrayList<>(BUNDLED.keySet());
        List<String> sys = new ArrayList<>();
        outer:
        for (String s : system()) {
            for (String b : BUNDLED.keySet()) if (s.equals(b) || s.startsWith(b + " ")) continue outer;
            sys.add(s);
        }
        sys.sort(String.CASE_INSENSITIVE_ORDER);
        out.addAll(sys);
        return out;
    }

    public static List<String> styles(String family) {
        init();
        Map<String, Font> m = BUNDLED.get(family);
        if (m == null) return List.of("Regular", "Bold", "Italic", "Bold Italic");
        List<String> out = new ArrayList<>(m.keySet());
        out.sort(Comparator.comparingInt((String s) -> s.contains("Italic") ? 1 : 0).thenComparingInt(s -> {
            String w = s.replace(" Italic", "").replace("Italic", "Regular");
            for (int i = 0; i < WEIGHTS.length; i++) if (WEIGHTS[i].equals(w)) return i;
            return 99;
        }));
        return out;
    }

    public static Font create(String family, String style, float size) {
        init();
        Map<String, Font> m = BUNDLED.get(family);
        if (m != null) {
            Font f = m.get(style);
            if (f == null) f = m.get("Regular");
            if (f == null) f = m.values().iterator().next();
            return f.deriveFont(size);
        }
        int st = style.contains("Bold") ? Font.BOLD : Font.PLAIN;
        if (style.contains("Italic")) st |= Font.ITALIC;
        String fam = system().contains(family) ? family : null;
        return new Font(fam == null ? Font.SANS_SERIF : fam, st, 12).deriveFont(size);
    }

    /** Interface font. A BOLD request picks the family's Bold style, otherwise the chosen style is used. */
    public static Font ui(int awtStyle, float size) {
        Settings c = Settings.get();
        String style = (awtStyle & Font.BOLD) != 0 ? "Bold" : c.str("uiFontStyle");
        return create(c.str("uiFontFamily"), style, size);
    }

    /** Monospaced font for typing text. */
    public static Font type(float size) {
        Settings c = Settings.get();
        Font f = create(c.str("typeFontFamily"), c.str("typeFontStyle"), size);
        if (f.getFamily().equals(Font.SANS_SERIF) || f.getFamily().equals("SansSerif")) {
            f = new Font(Font.MONOSPACED, Font.PLAIN, 12).deriveFont(size);
        }
        return f;
    }

    public static Font mono(int size) { return type(size); }

    /** A cache key that changes whenever the type font choice changes. */
    public static String typeKey() {
        Settings c = Settings.get();
        return c.str("typeFontFamily") + "|" + c.str("typeFontStyle") + "|" + c.num("fontSize");
    }

    /** Makes every Swing component use the chosen interface font. Call after the look and feel is set. */
    public static void applyUiDefaults() {
        Font base = ui(Font.PLAIN, Settings.get().num("uiFontSize"));
        FontUIResource res = new FontUIResource(base);
        UIManager.put("defaultFont", res);
        Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object k = keys.nextElement();
            if (k.toString().endsWith(".font") && UIManager.get(k) instanceof Font) UIManager.put(k, res);
        }
    }
}
