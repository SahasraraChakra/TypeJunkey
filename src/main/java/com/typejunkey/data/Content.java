package com.typejunkey.data;

import com.typejunkey.model.KeyLayout;
import com.typejunkey.model.Language;
import com.typejunkey.model.Quote;
import com.typejunkey.model.QuoteSet;
import com.typejunkey.model.Theme;
import com.typejunkey.util.Json;
import com.typejunkey.util.Resources;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Languages, quotes, themes and keyboard layouts. Each kind is read from a side folder next to the jar
 * (languages/, quotes/, themes/, layouts/) first and from the bundled copy second, so a file in the folder
 * overrides the bundled one of the same name. Bundled content may be removed or hidden at will: every
 * lookup has a built-in fallback and nothing here throws.
 */
public final class Content {
    private Content() {}

    public static final String LANGUAGE = "language", THEME = "theme", LAYOUT = "layout", QUOTES = "quotes";

    /** One selectable item and where it comes from. */
    public static final class Item {
        public final String name;
        public final boolean fromFolder, overridesBundled;
        public final boolean hidden;
        Item(String name, boolean fromFolder, boolean overrides, boolean hidden) {
            this.name = name; this.fromFolder = fromFolder; this.overridesBundled = overrides; this.hidden = hidden;
        }
    }

    private static final Map<String, Language> langCache = new HashMap<>();
    private static final Map<String, QuoteSet> quoteCache = new HashMap<>();
    private static final Map<String, KeyLayout> layoutCache = new HashMap<>();
    private static List<Theme> themeCache;

    /** Forget everything that was loaded (after the folders or the hidden list changed). */
    public static synchronized void reload() {
        langCache.clear(); quoteCache.clear(); layoutCache.clear(); themeCache = null;
    }

    // ------------------------------------------------------------------ scanning
    private static TreeMap<String, boolean[]> scan(String dir, String ext) {
        TreeMap<String, boolean[]> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER); // {fromFolder, bundled}
        for (String f : Resources.bundledUnder(dir)) {
            if (f.indexOf('/') < 0 && f.toLowerCase().endsWith(ext)) out.put(f.substring(0, f.length() - ext.length()), new boolean[] {false, true});
        }
        for (String f : Resources.folderUnder(Resources.side(dir))) {
            if (f.indexOf('/') < 0 && f.toLowerCase().endsWith(ext)) {
                String n = f.substring(0, f.length() - ext.length());
                boolean[] had = out.get(n);
                out.put(n, new boolean[] {true, had != null && had[1]});
            }
        }
        return out;
    }

    public static List<Item> items(String kind, boolean includeHidden) {
        List<Item> out = new ArrayList<>();
        Settings cfg = Settings.get();
        TreeMap<String, boolean[]> m;
        switch (kind) {
            case LANGUAGE: m = scan("languages", ".json"); break;
            case LAYOUT: m = scan("layouts", ".json"); break;
            case QUOTES: m = scan("quotes", ".json"); break;
            default: {
                m = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                for (Theme t : allThemes()) m.put(t.name, new boolean[] {false, true});
                for (String n : folderThemes().keySet()) {
                    boolean[] had = m.get(n);
                    m.put(n, new boolean[] {true, had != null});
                }
            }
        }
        for (Map.Entry<String, boolean[]> e : m.entrySet()) {
            boolean h = cfg.isHidden(kind, e.getKey());
            if (h && !includeHidden) continue;
            out.add(new Item(e.getKey(), e.getValue()[0], e.getValue()[0] && e.getValue()[1], h));
        }
        return out;
    }

    private static byte[] read(String dir, String file) {
        byte[] b = Resources.readFile(Resources.side(dir).resolve(file));
        return b != null ? b : Resources.readBundled(dir + "/" + file);
    }

    // ------------------------------------------------------------------ languages
    public static List<String> languages() {
        List<String> out = new ArrayList<>();
        for (Item i : items(LANGUAGE, false)) out.add(i.name);
        if (out.isEmpty()) out.add("english");
        return out;
    }

    public static synchronized Language language(String name) {
        Language l = langCache.get(name);
        if (l != null) return l;
        try {
            String s = Resources.text(read("languages", name + ".json"));
            if (s != null) {
                List<Object> raw = Json.arr(Json.obj(Json.parse(s)).get("words"));
                List<String> words = new ArrayList<>(raw.size());
                for (Object o : raw) if (o instanceof String && !((String) o).isEmpty()) words.add((String) o);
                if (!words.isEmpty()) {
                    l = new Language(name, words.toArray(new String[0]));
                    if (langCache.size() > 4) langCache.clear();
                    langCache.put(name, l);
                    return l;
                }
            }
        } catch (RuntimeException ignored) { }
        // fall back to any other available language, then to a tiny built-in list
        for (String other : languages()) {
            if (!other.equals(name) && !other.equals("english")) { /* only try english-like first */ }
        }
        if (!name.equals("english") && languages().contains("english")) return language("english");
        return new Language("english", ("the of and to a in is it you that he was for on are with as his they be at one have this from "
                + "or had by not word but what some we can out other were all there when up use your how said an each she which do "
                + "their time if will way about many then them write would like so these her long make thing see him two has look "
                + "more day could go come did number sound no most people my over know water than call first who may down side been now find").split(" "));
    }

    // ------------------------------------------------------------------ quotes
    public static synchronized QuoteSet quotes(String lang) {
        String[] candidates = {lang, lang.contains("_") ? lang.substring(0, lang.indexOf('_')) : lang, "english"};
        for (String c : candidates) {
            QuoteSet q = quoteCache.get(c);
            if (q != null) return q;
            if (Settings.get().isHidden(QUOTES, c)) continue;
            String s = Resources.text(read("quotes", c + ".json"));
            if (s == null) continue;
            try {
                Map<String, Object> m = Json.obj(Json.parse(s));
                List<Quote> list = new ArrayList<>();
                for (Object o : Json.arr(m.get("quotes"))) {
                    Map<String, Object> qm = Json.obj(o);
                    String text = ((String) qm.get("text")).replace('\u00A0', ' ').trim();
                    list.add(new Quote(text, String.valueOf(qm.get("source")), text.length()));
                }
                List<Object> g = Json.arr(m.get("groups"));
                int[][] groups = new int[g.size()][2];
                for (int i = 0; i < g.size(); i++) {
                    List<Object> pair = Json.arr(g.get(i));
                    groups[i][0] = ((Double) pair.get(0)).intValue();
                    groups[i][1] = ((Double) pair.get(1)).intValue();
                }
                if (!list.isEmpty()) {
                    q = new QuoteSet(list, groups);
                    quoteCache.put(c, q);
                    return q;
                }
            } catch (RuntimeException ignored) { }
        }
        return new QuoteSet(List.of(new Quote("The quick brown fox jumps over the lazy dog.", "pangram", 44)),
                new int[][] {{0, 100}, {101, 300}, {301, 600}, {601, 9999}});
    }

    // ------------------------------------------------------------------ themes
    private static final Pattern CSS_VAR = Pattern.compile("--([a-z-]+)-color:\\s*([^;]+);");

    private static List<Theme> bundledThemes() {
        List<Theme> out = new ArrayList<>();
        try {
            String s = Resources.text(Resources.readBundled("themes/themes.json"));
            if (s != null) {
                for (Object o : Json.arr(Json.parse(s))) {
                    Theme t = fromMap(Json.obj(o));
                    if (t != null) out.add(t);
                }
            }
        } catch (RuntimeException ignored) { }
        return out;
    }

    private static Theme fromMap(Map<String, Object> m) {
        Object n = m.get("name");
        if (n == null) return null;
        Theme d = Theme.fallback();
        Color bg = col(m.get("bg"), d.bg), main = col(m.get("main"), d.main);
        Color err = col(m.get("error"), d.error);
        return new Theme(String.valueOf(n), bg, main, col(m.get("caret"), main), col(m.get("sub"), d.sub),
                col(m.get("subAlt"), bg), col(m.get("text"), d.text), err, col(m.get("errorExtra"), err),
                col(m.get("colorfulError"), err), col(m.get("colorfulErrorExtra"), err));
    }

    private static Color col(Object o, Color d) {
        Color c = o instanceof String ? parseColor((String) o) : null;
        return c != null ? c : d;
    }

    private static Map<String, Theme> folderThemes() {
        Map<String, Theme> out = new LinkedHashMap<>();
        Path dir = Resources.side("themes");
        for (String f : Resources.folderUnder(dir)) {
            if (f.indexOf('/') >= 0) continue;
            String lower = f.toLowerCase();
            try {
                String s = Resources.text(Resources.readFile(dir.resolve(f)));
                if (s == null) continue;
                if (lower.endsWith(".css")) {
                    String name = f.substring(0, f.length() - 4);
                    Map<String, Object> m = new HashMap<>();
                    m.put("name", name);
                    Matcher mt = CSS_VAR.matcher(s);
                    while (mt.find()) {
                        String k = mt.group(1);
                        String key = k.equals("sub-alt") ? "subAlt" : k.equals("error-extra") ? "errorExtra" : k;
                        m.putIfAbsent(key, mt.group(2).trim());
                    }
                    Theme t = fromMap(m);
                    if (t != null) out.put(name, t);
                } else if (lower.endsWith(".json")) {
                    Object parsed = Json.parse(s);
                    List<Object> list = parsed instanceof List ? Json.arr(parsed) : List.of(parsed);
                    for (Object o : list) {
                        Map<String, Object> m = Json.obj(o);
                        m.putIfAbsent("name", f.substring(0, f.length() - 5));
                        Theme t = fromMap(m);
                        if (t != null) out.put(t.name, t);
                    }
                }
            } catch (RuntimeException ignored) { }
        }
        return out;
    }

    private static synchronized List<Theme> allThemes() {
        Map<String, Theme> m = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Theme t : bundledThemes()) m.put(t.name, t);
        m.putAll(folderThemes());
        return new ArrayList<>(m.values());
    }

    public static synchronized List<Theme> themes() {
        if (themeCache == null) {
            List<Theme> out = new ArrayList<>();
            Settings cfg = Settings.get();
            for (Theme t : allThemes()) if (!cfg.isHidden(THEME, t.name)) out.add(t);
            if (out.isEmpty()) out.add(Theme.fallback());
            themeCache = out;
        }
        return themeCache;
    }

    public static Theme theme(String name) {
        for (Theme t : themes()) if (t.name.equals(name)) return t;
        for (Theme t : themes()) if (t.name.equals("serika_dark")) return t;
        return themes().get(0);
    }

    static Color parseColor(String raw) {
        String v = raw.trim();
        try {
            if (v.startsWith("#")) {
                String h = v.substring(1);
                if (h.length() == 3 || h.length() == 4) {
                    StringBuilder b = new StringBuilder();
                    for (char c : h.toCharArray()) b.append(c).append(c);
                    h = b.toString();
                }
                if (h.length() == 6) return new Color(Integer.parseInt(h, 16));
                if (h.length() == 8) {
                    return new Color(Integer.parseInt(h.substring(0, 2), 16), Integer.parseInt(h.substring(2, 4), 16),
                            Integer.parseInt(h.substring(4, 6), 16), Integer.parseInt(h.substring(6, 8), 16));
                }
            } else if (v.startsWith("rgb")) {
                Matcher m = Pattern.compile("[\\d.]+").matcher(v);
                int[] n = new int[3];
                for (int i = 0; i < 3 && m.find(); i++) n[i] = (int) Math.round(Double.parseDouble(m.group()));
                return new Color(n[0], n[1], n[2]);
            }
        } catch (RuntimeException ignored) { }
        return null;
    }

    // ------------------------------------------------------------------ keyboard layouts
    public static List<String> layouts() {
        List<String> out = new ArrayList<>();
        for (Item i : items(LAYOUT, false)) out.add(i.name);
        if (out.isEmpty()) out.add("qwerty");
        return out;
    }

    public static synchronized KeyLayout layout(String name) {
        KeyLayout l = layoutCache.get(name);
        if (l != null) return l;
        try {
            String s = Resources.text(read("layouts", name + ".json"));
            if (s != null) {
                Map<String, Object> m = Json.obj(Json.parse(s));
                Map<String, Object> keys = Json.obj(m.get("keys"));
                List<List<String[]>> rows = new ArrayList<>();
                for (int r = 1; r <= 5; r++) {
                    List<String[]> row = new ArrayList<>();
                    Object ro = keys.get("row" + r);
                    if (ro != null) {
                        for (Object ko : Json.arr(ro)) {
                            List<Object> k = Json.arr(ko);
                            String[] pair = new String[k.size()];
                            for (int i = 0; i < pair.length; i++) pair[i] = String.valueOf(k.get(i));
                            row.add(pair);
                        }
                    }
                    rows.add(row);
                }
                l = new KeyLayout(name, String.valueOf(m.get("type")), rows, Boolean.TRUE.equals(m.get("keymapShowTopRow")));
                layoutCache.put(name, l);
                return l;
            }
        } catch (RuntimeException ignored) { }
        return builtinQwerty();
    }

    private static KeyLayout builtinQwerty() {
        String[] r = {"`~1!2@3#4$5%6^7&8*9(0)-_=+", "qQwWeErRtTyYuUiIoOpP[{]}\\|", "aAsSdDfFgGhHjJkKlL;:'\"", "zZxXcCvVbBnNmM,<.>/?", " "};
        List<List<String[]>> rows = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            List<String[]> row = new ArrayList<>();
            for (int k = 0; k + 1 < r[i].length() || (i == 4 && k == 0); k += 2) {
                row.add(i == 4 ? new String[] {" "} : new String[] {"" + r[i].charAt(k), "" + r[i].charAt(k + 1)});
                if (i == 4) break;
            }
            rows.add(row);
        }
        return new KeyLayout("qwerty", "ansi", rows);
    }

    public static boolean folderExists(String sub) { return Files.isDirectory(Resources.side(sub)); }
}
