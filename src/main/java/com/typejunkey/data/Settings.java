package com.typejunkey.data;

import com.typejunkey.util.Resources;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * User settings, persisted to ~/.typejunkey/settings.properties.
 * Core test options are plain fields; everything shown in the Settings tab is declared once in the
 * registry below and read with {@link #bool}, {@link #str} and {@link #num}. A Def with an empty
 * category is stored but not shown (lists edited elsewhere).
 */
public final class Settings {
    public enum Type { BOOL, ENUM, INT, FAMILY, STYLE, LAYOUT, THEME, FILE, TEXT, FUNBOX, LANGS }

    public static final class Def {
        public final String key, cat, label, tip, def;
        public final Type type;
        public final String[] options;
        public final int min, max;
        public final String linked; // for STYLE: the FAMILY key it belongs to

        Def(String key, String cat, String label, String tip, Type type, String def, String[] options, int min, int max, String linked) {
            this.key = key; this.cat = cat; this.label = label; this.tip = tip; this.type = type; this.def = def;
            this.options = options; this.min = min; this.max = max; this.linked = linked;
        }
    }

    public static final Path DIR = Path.of(System.getProperty("user.home"), ".typejunkey");
    private static final Path FILE = DIR.resolve("settings.properties");
    public static final String[] QUOTE_LABELS = {"short", "medium", "long", "very long"};
    private static final int VERSION = 2;

    // ------------------------------------------------------------------ registry
    private static final List<Def> DEFS = new ArrayList<>();
    private static final Map<String, Def> BY_KEY = new HashMap<>();

    private static void def(Def d) { DEFS.add(d); BY_KEY.put(d.key, d); }
    private static void bool(String k, String cat, String label, boolean d, String tip) { def(new Def(k, cat, label, tip, Type.BOOL, "" + d, null, 0, 0, null)); }
    private static void en(String k, String cat, String label, String d, String tip, String... opts) { def(new Def(k, cat, label, tip, Type.ENUM, d, opts, 0, 0, null)); }
    private static void num(String k, String cat, String label, int d, int min, int max, String tip) { def(new Def(k, cat, label, tip, Type.INT, "" + d, null, min, max, null)); }
    private static void kind(String k, String cat, String label, String d, String tip, Type t) { def(new Def(k, cat, label, tip, t, d, null, 0, 0, null)); }
    private static void style(String k, String cat, String label, String d, String linked) { def(new Def(k, cat, label, "", Type.STYLE, d, null, 0, 0, linked)); }

    public static final String APPEARANCE = "Appearance", THEMES = "Theme", CARET = "Caret and animation", BEHAVIOR = "Test behavior",
            LIVE = "Live stats and timer", LAYOUT = "Keyboard layout and keymap", SOUND = "Sound", FUNBOX = "Funbox", TUTOR = "Tutor",
            CONTENT = "Content folders";

    private static String[] soundOptions() {
        TreeSet<Integer> n = new TreeSet<>();
        for (String f : Resources.bundledUnder("sounds")) {
            if (f.startsWith("click") && f.contains("/")) {
                try { n.add(Integer.parseInt(f.substring(5, f.indexOf('/')))); } catch (NumberFormatException ignored) { }
            }
        }
        if (n.isEmpty()) for (int i = 1; i <= 7; i++) n.add(i);
        List<String> o = new ArrayList<>();
        o.add("off");
        for (int i : n) o.add("" + i);
        return o.toArray(new String[0]);
    }

    static {
        // ---- appearance
        bool("autoAppTheme", APPEARANCE, "Auto Theme App With Type Theme", true,
                "A dark type theme switches the window to the dark theme, a light type theme to the light theme.");
        en("appTheme", APPEARANCE, "App theme", "dark", "Window theme used when the auto option above is off.", "dark", "light");
        en("frameStyle", APPEARANCE, "Window frame", "square",
                "square: FlatLaf-drawn title bar with square corners. native: the operating system frame. Restart to apply.", "square", "native");
        kind("uiFontFamily", APPEARANCE, "Interface font", "Inter", "Font for menus, tabs, buttons and other regular text.", Type.FAMILY);
        style("uiFontStyle", APPEARANCE, "Interface font style", "Regular", "uiFontFamily");
        num("uiFontSize", APPEARANCE, "Interface font size", 12, 10, 24, "Size of regular interface text, in points.");
        kind("typeFontFamily", APPEARANCE, "Type font", "Cascadia Mono", "Monospaced font for the typing test, the tutor and results.", Type.FAMILY);
        style("typeFontStyle", APPEARANCE, "Type font style", "Regular", "typeFontFamily");
        num("fontSize", APPEARANCE, "Type font size", 24, 12, 64, "Size of the typing text, in points.");
        en("textRendering", APPEARANCE, "Text rendering", "smooth",
                "smooth: grayscale anti-aliasing (default). sharp: LCD sub-pixel anti-aliasing. off: no anti-aliasing.", "smooth", "sharp", "off");
        bool("flipTestColors", APPEARANCE, "Flip test colors", false, "Untyped text uses the text color and typed text uses the sub color.");
        bool("colorfulMode", APPEARANCE, "Colorful mode", false, "Typed letters use the main color and mistakes use the colorful error colors.");
        kind("customBackground", APPEARANCE, "Custom background", "", "Image file or web address shown behind the typing test.", Type.FILE);
        en("customBackgroundSize", APPEARANCE, "Background size", "cover", "How the background image fills the window.", "cover", "contain", "max");
        num("bgBlur", APPEARANCE, "Background blur", 0, 0, 30, "Blur radius in pixels.");
        num("bgBrightness", APPEARANCE, "Background brightness (%)", 100, 0, 200, "100 is unchanged.");
        num("bgSaturate", APPEARANCE, "Background saturation (%)", 100, 0, 200, "100 is unchanged.");
        num("bgOpacity", APPEARANCE, "Background opacity (%)", 100, 0, 100, "100 is fully visible.");
        bool("showKeyTips", APPEARANCE, "Show key tips", true, "Show the restart hint under the text.");
        bool("showOutOfFocusWarning", APPEARANCE, "Out of focus warning", true, "Dim the text and show a message when the typing area has no focus.");
        bool("capsLockWarning", APPEARANCE, "Caps Lock warning", true, "Warn while Caps Lock is on.");
        bool("monkey", APPEARANCE, "Monkey", false, "A little monkey types along with you.");
        en("monkeyPowerLevel", APPEARANCE, "Monkey power", "off", "Sparks and screen shake while typing. Higher is stronger.", "off", "1", "2", "3", "4");

        // ---- theme
        en("randomTheme", THEMES, "Random theme", "off", "Pick a new theme for every test: any, favorites, light, dark, custom, or auto (follow the app).",
                "off", "on", "fav", "light", "dark", "custom", "auto");
        bool("autoSwitchTheme", THEMES, "Switch theme with the operating system", false, "Use the light or dark theme below, following the system setting.");
        kind("themeLight", THEMES, "Light theme", "serika", "Theme used when the system is light.", Type.THEME);
        kind("themeDark", THEMES, "Dark theme", "serika_dark", "Theme used when the system is dark.", Type.THEME);
        bool("customTheme", THEMES, "Use custom theme", false, "Use the colors you set in the Themes tab instead of a named theme.");
        def(new Def("favThemes", "", "", "", Type.TEXT, "", null, 0, 0, null));
        def(new Def("customThemeColors", "", "", "", Type.TEXT, "#323437,#e2b714,#e2b714,#646669,#2c2e31,#d1d0c5,#ca4754,#7e2a33,#ca4754,#7e2a33", null, 0, 0, null));

        // ---- caret and animation
        en("smoothCaret", CARET, "Smooth caret", "medium", "Glide the caret instead of jumping from letter to letter. Speed of the glide.", "off", "slow", "medium", "fast");
        en("caretStyle", CARET, "Caret style", "default", "Shape of the typing caret.", "off", "default", "block", "outline", "underline", "carrot", "banana", "monkey");
        en("paceCaret", CARET, "Pace caret", "off", "A second caret that moves at a steady speed: your average, personal best, last result, today's average or a custom speed.",
                "off", "average", "pb", "last", "daily", "custom");
        num("paceCaretCustomSpeed", CARET, "Pace caret custom speed (wpm)", 100, 0, 500, "Used when pace caret is set to custom.");
        en("paceCaretStyle", CARET, "Pace caret style", "default", "Shape of the pace caret.", "off", "default", "block", "outline", "underline", "carrot", "banana", "monkey");
        bool("repeatedPace", CARET, "Repeated pace", true, "When you repeat a test, the pace caret runs at the speed of the previous attempt.");
        bool("smoothLineScroll", CARET, "Smooth line scroll", true, "Slide the text up smoothly when a new line starts.");
        en("tapeMode", CARET, "Tape mode", "off", "Show the text as one scrolling line with the caret held in place.", "off", "letter", "word");
        num("tapeMargin", CARET, "Tape margin (%)", 50, 10, 90, "Where the caret rests across the tape, from the left edge.");
        en("highlightMode", CARET, "Highlight mode", "letter", "What to highlight while typing.", "off", "letter", "word", "next_word", "next_two_words", "next_three_words");
        en("typedEffect", CARET, "Typed text effect", "keep", "What happens to text you have already typed.", "keep", "hide", "fade", "dots");
        bool("showAllLines", CARET, "Show all lines", false, "Show every line that fits instead of three.");
        num("maxLineWidth", CARET, "Maximum line width (characters)", 0, 0, 1000, "0 uses the full width.");
        en("indicateTypos", CARET, "Indicate typos", "off", "Show the letter you actually typed: below the correct one, in its place, or both.", "off", "below", "replace", "both");
        bool("hideExtraLetters", CARET, "Hide extra letters", false, "Do not show letters typed past the end of a word.");
        bool("blindMode", CARET, "Blind mode", false, "Mistakes are not marked.");

        // ---- live stats and timer
        en("timerStyle", LIVE, "Timer style", "mini", "How the timer or word counter is shown.", "off", "bar", "text", "mini", "flash_text", "flash_mini");
        en("timerColor", LIVE, "Timer color", "main", "Color of the timer.", "black", "sub", "text", "main");
        en("timerOpacity", LIVE, "Timer opacity", "1", "Opacity of the timer.", "0.25", "0.5", "0.75", "1");
        en("liveSpeedStyle", LIVE, "Live speed", "mini", "Show your speed while typing.", "off", "text", "mini");
        en("liveAccStyle", LIVE, "Live accuracy", "mini", "Show your accuracy while typing.", "off", "text", "mini");
        en("liveBurstStyle", LIVE, "Live burst", "off", "Show the speed of the last word while typing.", "off", "text", "mini");
        en("showAverage", LIVE, "Show average", "off", "Show the average of your last 10 results while typing.", "off", "speed", "acc", "both");
        bool("showPb", LIVE, "Show personal best", false, "Show your personal best for this test while typing.");
        en("typingSpeedUnit", LIVE, "Speed unit", "wpm", "wpm words per minute, cpm characters per minute, wps words per second, cps characters per second, wph words per hour.",
                "wpm", "cpm", "wps", "cps", "wph");
        bool("alwaysShowDecimalPlaces", LIVE, "Always show decimal places", false, "Show results with two decimals.");
        bool("startGraphsAtZero", LIVE, "Start graphs at zero", true, "The result chart starts at zero instead of at your slowest second.");
        bool("burstHeatmap", LIVE, "Burst heatmap", false, "Color each word in the word history by its speed.");
        bool("alwaysShowWordsHistory", LIVE, "Always show words history", false, "Show the words history under the result chart.");

        // ---- behavior
        en("quickRestart", BEHAVIOR, "Quick restart", "off", "A single key that restarts the test: Tab, Esc or Enter. Off means Tab then Enter.", "off", "esc", "tab", "enter");
        en("difficulty", BEHAVIOR, "Difficulty", "normal", "normal; expert fails the test when a word is submitted incorrectly; master fails on any mistake.", "normal", "expert", "master");
        bool("freedomMode", BEHAVIOR, "Freedom mode", false, "Backspace into any earlier word, even a correct one.");
        en("confidenceMode", BEHAVIOR, "Confidence mode", "off", "on: cannot return to earlier words. max: backspace is disabled.", "off", "on", "max");
        en("stopOnError", BEHAVIOR, "Stop on error", "off", "letter: wrong letters are not accepted. word: you cannot leave a word until it is correct.", "off", "word", "letter");
        en("deleteOnError", BEHAVIOR, "Delete on error", "off", "Automatically delete a wrong letter (or the whole word). The hard variants also count a mistake.",
                "off", "letter", "letter_hard", "word", "word_hard");
        bool("strictSpace", BEHAVIOR, "Strict space", false, "Pressing space before a word is complete counts as a mistake.");
        bool("quickEnd", BEHAVIOR, "Quick end", false, "End a words test as soon as the last letter is typed, even if it is wrong.");
        bool("lazyMode", BEHAVIOR, "Lazy mode", false, "Accented letters can be typed as their plain letter.");
        en("oppositeShiftMode", BEHAVIOR, "Opposite shift mode", "off", "Require the Shift key on the opposite hand from the letter.", "off", "on", "keymap");
        en("repeatQuotes", BEHAVIOR, "Repeat quotes", "off", "typing: a quote repeats until you finish it without mistakes.", "off", "typing");
        bool("resultSaving", BEHAVIOR, "Save results", true, "Keep finished tests in the history and personal bests.");
        en("minWpm", BEHAVIOR, "Minimum speed", "off", "Fail the test if your speed drops below a value.", "off", "custom");
        num("minWpmCustomSpeed", BEHAVIOR, "Minimum speed value (wpm)", 100, 0, 500, "");
        en("minAcc", BEHAVIOR, "Minimum accuracy", "off", "Fail the test if your accuracy drops below a value.", "off", "custom");
        num("minAccCustom", BEHAVIOR, "Minimum accuracy value (%)", 90, 0, 100, "");
        en("minBurst", BEHAVIOR, "Minimum burst", "off", "Fail the test if a word is typed slower than a value. flex lowers the value when your average is lower.", "off", "fixed", "flex");
        num("minBurstCustomSpeed", BEHAVIOR, "Minimum burst value (wpm)", 100, 0, 500, "");
        bool("britishEnglish", BEHAVIOR, "British English", false, "Use British spellings in English word lists and quotes.");

        // ---- layout and keymap
        kind("layout", LAYOUT, "Layout emulator", "default", "Type as if your keyboard had this layout. default: use the keys as they are.", Type.LAYOUT);
        kind("keymapLayout", LAYOUT, "Keyboard layout shown", "sync", "Layout drawn by the tutor and the keymap. sync: follow the emulator, or qwerty.", Type.LAYOUT);
        en("keymapMode", LAYOUT, "Keymap", "off", "On-screen keyboard under the text. static: always shown. react: shows the key you press. next: shows the next key.", "off", "static", "react", "next");
        en("keymapStyle", LAYOUT, "Keymap style", "staggered", "Shape of the on-screen keyboard.", "staggered", "alice", "matrix", "split", "split_matrix", "steno", "steno_matrix");
        en("keymapLegendStyle", LAYOUT, "Keymap legend", "lowercase", "Key labels.", "lowercase", "uppercase", "blank", "dynamic");
        en("keymapKeys", LAYOUT, "Keymap keys", "minimal", "minimal: letters only. minimal_numrow: with the number row. full: with modifier keys.", "minimal", "minimal_numrow", "full");
        num("keymapSize", LAYOUT, "Keymap size (tenths)", 10, 5, 35, "10 is normal size.");

        // ---- sound
        num("soundVolume", SOUND, "Sound volume (%)", 50, 0, 100, "");
        def(new Def("playSoundOnClick", SOUND, "Click sound", "Sound played for every key.", Type.ENUM, "off", soundOptions(), 0, 0, null));
        en("playSoundOnError", SOUND, "Error sound", "off", "Sound played for a mistake.", "off", "1", "2", "3", "4");
        en("playTimeWarning", SOUND, "Time warning", "off", "Sound played when this many seconds are left in a timed test.", "off", "1", "3", "5", "7", "10");

        // ---- funbox
        def(new Def("funbox", FUNBOX, "Active funbox", "Fun modifiers that change the words, the rules or the look of the test.", Type.FUNBOX, "", null, 0, 0, null));
        kind("customLayoutfluid", FUNBOX, "Layoutfluid layouts", "qwerty#dvorak#colemak", "Layouts for the layoutfluid funbox, separated by #.", Type.TEXT);
        def(new Def("customPolyglot", FUNBOX, "Polyglot languages", "Languages for the polyglot funbox.", Type.LANGS, "english#spanish#french#german", null, 0, 0, null));

        // ---- tutor
        bool("tutorShowHands", TUTOR, "Show hands in the tutor", true, "Draw the two hands beside the keyboard.");
    }

    public static List<Def> defs() { return Collections.unmodifiableList(DEFS); }
    public static Def def(String key) { return BY_KEY.get(key); }

    // ------------------------------------------------------------------ core test options (plain fields)
    public String mode = "time";           // time | words | quote | zen | custom
    public int time = 30, words = 25, quoteLen = -1; // quoteLen: -1 = all, else group index
    public boolean punctuation, numbers;
    public String language = "english";
    public String theme = "serika_dark";
    public String customText = "";
    public String tutorFolder = "";        // empty = the "tutor" folder next to the jar
    public String tutorLevel = "basic", tutorLesson = "";

    private final Map<String, String> vals = new HashMap<>();
    private final Set<String> hidden = new HashSet<>(); // "kind:name"
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    // must be declared after DIR/FILE/registry: the constructor reads the settings file
    private static final Settings INSTANCE = new Settings();
    public static Settings get() { return INSTANCE; }

    private Settings() { load(); }

    public void onChange(Runnable r) { listeners.add(r); }
    public void fire() { for (Runnable r : listeners) r.run(); }

    // ------------------------------------------------------------------ typed access
    public String str(String key) {
        String v = vals.get(key);
        if (v != null) return v;
        Def d = BY_KEY.get(key);
        return d == null ? "" : d.def;
    }

    public boolean bool(String key) { return Boolean.parseBoolean(str(key)); }

    public int num(String key) {
        Def d = BY_KEY.get(key);
        try {
            int v = Integer.parseInt(str(key));
            return d == null ? v : Math.max(d.min, Math.min(d.max, v));
        } catch (NumberFormatException e) {
            return d == null ? 0 : Integer.parseInt(d.def);
        }
    }

    /** A '#'-separated list setting such as funbox. */
    public List<String> list(String key) {
        List<String> out = new ArrayList<>();
        for (String s : str(key).split("#")) if (!s.isBlank()) out.add(s.trim());
        return out;
    }

    public void setList(String key, List<String> v) { set(key, String.join("#", new LinkedHashSet<>(v))); }

    /** Stores a registry value, saves and notifies listeners. */
    public void set(String key, Object value) {
        vals.put(key, String.valueOf(value));
        save();
        fire();
    }

    /** Stores a registry value and saves, without notifying listeners. */
    public void setQuiet(String key, Object value) {
        vals.put(key, String.valueOf(value));
        save();
    }

    /** Mode parameter used for personal-best keys and labels. */
    public String param() {
        switch (mode) {
            case "time": return Integer.toString(time);
            case "words": return Integer.toString(words);
            case "quote": return quoteLen < 0 ? "all" : QUOTE_LABELS[Math.min(quoteLen, QUOTE_LABELS.length - 1)];
            default: return "";
        }
    }

    // ------------------------------------------------------------------ hidden bundled/folder content
    public boolean isHidden(String kind, String name) { return hidden.contains(kind + ":" + name); }

    public void setHidden(String kind, String name, boolean h) {
        if (h) hidden.add(kind + ":" + name); else hidden.remove(kind + ":" + name);
        save();
    }

    // ------------------------------------------------------------------ persistence
    private void load() {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            p.load(in);
        } catch (IOException e) {
            return;
        }
        mode = p.getProperty("mode", mode);
        time = n(p, "time", time); words = n(p, "words", words); quoteLen = n(p, "quoteLen", quoteLen);
        punctuation = Boolean.parseBoolean(p.getProperty("punctuation", "false"));
        numbers = Boolean.parseBoolean(p.getProperty("numbers", "false"));
        language = p.getProperty("language", language);
        theme = p.getProperty("theme", theme);
        customText = p.getProperty("customText", "");
        tutorFolder = p.getProperty("tutorFolder", "");
        tutorLevel = p.getProperty("tutorLevel", tutorLevel);
        tutorLesson = p.getProperty("tutorLesson", "");
        for (String h : p.getProperty("hidden", "").split("\n")) if (!h.isBlank()) hidden.add(h.trim());
        for (Def d : DEFS) {
            String v = p.getProperty(d.key);
            if (v != null) vals.put(d.key, v);
        }
        int version = n(p, "settingsVersion", 1);
        if (version < 2) migrate(p);
    }

    /** Settings written by version 0.1 of the app. */
    private void migrate(Properties p) {
        String oldLayout = p.getProperty("layout");
        vals.remove("layout");
        if (oldLayout != null && !oldLayout.equals("default")) vals.put("keymapLayout", oldLayout);
        String sc = p.getProperty("smoothCaret");
        if (sc != null) vals.put("smoothCaret", Boolean.parseBoolean(sc) ? "medium" : "off");
        String ls = p.getProperty("liveStats");
        if (ls != null) { vals.put("liveSpeedStyle", Boolean.parseBoolean(ls) ? "mini" : "off"); vals.put("liveAccStyle", Boolean.parseBoolean(ls) ? "mini" : "off"); }
        if ("13".equals(vals.get("uiFontSize"))) vals.remove("uiFontSize"); // old default, new default is 12
        if ("line".equals(vals.get("caretStyle"))) vals.put("caretStyle", "default");
    }

    private static int n(Properties p, String k, int d) {
        try { return Integer.parseInt(p.getProperty(k)); } catch (RuntimeException e) { return d; }
    }

    public void save() {
        Properties p = new Properties();
        p.setProperty("settingsVersion", "" + VERSION);
        p.setProperty("mode", mode); p.setProperty("time", "" + time); p.setProperty("words", "" + words);
        p.setProperty("quoteLen", "" + quoteLen); p.setProperty("punctuation", "" + punctuation);
        p.setProperty("numbers", "" + numbers); p.setProperty("language", language); p.setProperty("theme", theme);
        p.setProperty("customText", customText); p.setProperty("tutorFolder", tutorFolder);
        p.setProperty("tutorLevel", tutorLevel); p.setProperty("tutorLesson", tutorLesson);
        p.setProperty("hidden", String.join("\n", hidden));
        for (Map.Entry<String, String> e : vals.entrySet()) p.setProperty(e.getKey(), e.getValue());
        try {
            Files.createDirectories(DIR);
            try (OutputStream out = Files.newOutputStream(FILE)) { p.store(out, "TypeJunKey settings"); }
        } catch (IOException ignored) { }
    }
}
