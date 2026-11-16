package com.typejunkey;

import com.typejunkey.data.Settings;
import com.typejunkey.engine.TestEngine;
import com.typejunkey.model.TestResult;
import com.typejunkey.ui.AppTheme;
import com.typejunkey.ui.MainPanel;
import com.typejunkey.ui.TypingPane;

import javax.imageio.ImageIO;
import javax.swing.JMenuBar;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Dev-only harness: engine self-checks plus real screenshots under Xvfb. Not part of the shipped jar. */
public final class Shot {
    static void check(boolean ok, String msg) { System.out.println((ok ? "PASS " : "FAIL ") + msg); if (!ok) System.exit(1); }

    static void engineChecks() {
        Settings s = Settings.get();
        s.mode = "words"; s.words = 10; s.punctuation = false; s.numbers = false; s.language = "english";
        TestEngine e = new TestEngine(s);
        e.reset(false);
        check(e.targets.size() == 10, "words mode generates 10 words");
        List<String> t = new ArrayList<>(e.targets);
        for (int i = 0; i < t.size(); i++) {
            for (char c : t.get(i).toCharArray()) e.typeChar(c, false);
            if (i < t.size() - 1) e.space();
        }
        check(e.isFinished(), "finishes when last word typed correctly (no trailing space)");
        TestResult r = e.result();
        check(r.acc == 100.0, "accuracy 100 for perfect run, got " + r.acc);
        check(r.incorrect == 0 && r.missed == 0 && r.extra == 0, "no incorrect/missed/extra chars");

        // errors, backspace, word-skip behaviour
        e.reset(false);
        String w0 = e.targets.get(0);
        e.typeChar('#', false);
        check(e.typedWord(0).equals("#"), "wrong char is typed");
        e.backspace(false);
        check(e.typedWord(0).isEmpty(), "backspace removes it");
        for (char c : w0.toCharArray()) e.typeChar(c, false);
        e.typeChar('x', false); // extra letter
        e.space();
        check(e.cur == 1, "space commits the word even when it has an extra letter");
        e.backspace(false);
        check(e.cur == 0, "can go back into an incorrect word");
        e.backspace(false); // removes 'x'
        e.space();
        e.backspace(false);
        check(e.cur == 1, "cannot go back into a correct word");

        // zen
        s.mode = "zen";
        e.reset(false);
        for (char c : "hello".toCharArray()) e.typeChar(c, false);
        e.space();
        for (char c : "zen".toCharArray()) e.typeChar(c, false);
        e.finishZen();
        check(e.isFinished() && e.result().acc == 100, "zen finishes via finishZen with 100% accuracy");
        check(e.targets.size() == 2, "zen words mirror typed words");

        // quote
        s.mode = "quote"; s.quoteLen = 0;
        e.reset(false);
        check(e.quoteSource != null && !e.targets.isEmpty(), "quote mode picks a quote: " + e.targets.size() + " words");

        // custom
        s.mode = "custom"; s.customText = "alpha beta  gamma";
        e.reset(false);
        check(e.targets.equals(List.of("alpha", "beta", "gamma")), "custom text splits on whitespace");

        // punctuation + numbers
        s.mode = "words"; s.words = 100; s.punctuation = true; s.numbers = true;
        e.reset(false);
        boolean hasPunct = false, hasNum = false;
        for (String w : e.targets) { if (w.matches(".*[.,?!;:\"()].*")) hasPunct = true; if (w.matches("\\d+")) hasNum = true; }
        check(hasPunct && hasNum, "punctuation and numbers are applied");
        s.punctuation = false; s.numbers = false; s.mode = "time";
        System.out.println("engine checks done");
    }

    static void typeKey(Component c, char ch) {
        KeyEvent ev = new KeyEvent(c, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, ch);
        for (KeyListener l : c.getKeyListeners()) l.keyTyped(ev);
    }

    static <T extends Component> T find(Container c, Class<T> type) {
        for (Component k : c.getComponents()) {
            if (type.isInstance(k)) return type.cast(k);
            if (k instanceof Container) { T p = find((Container) k, type); if (p != null) return p; }
        }
        return null;
    }

    static void layoutTree(Container c) {
        c.doLayout();
        for (Component k : c.getComponents()) if (k instanceof Container) layoutTree((Container) k);
    }

    static void shot(MainPanel p, String name, int tab) throws Exception {
        final int W = 1120, H = 740;
        javax.swing.JTabbedPane tabs = find(p, javax.swing.JTabbedPane.class);
        SwingUtilities.invokeAndWait(() -> tabs.setSelectedIndex(tab));
        JMenuBar mb = p.menuBar();
        int mh = 28;
        BufferedImage img = new BufferedImage(W, H + mh, BufferedImage.TYPE_INT_RGB);
        for (int pass = 0; pass < 3; pass++) {
            SwingUtilities.invokeAndWait(() -> { p.setSize(W, H); layoutTree(p); mb.setSize(W, mh); layoutTree(mb); });
            Thread.sleep(150);
        }
        SwingUtilities.invokeAndWait(() -> {
            java.awt.Graphics2D g = img.createGraphics();
            g.setColor(p.getBackground());
            g.fillRect(0, 0, W, H + mh);
            mb.paint(g);
            g.translate(0, mh);
            p.paint(g);
            g.dispose();
        });
        ImageIO.write(img, "png", new File(name));
        System.out.println("wrote " + name);
    }

    /** The harness panel is not inside a window, so FlatLaf.updateUI() cannot reach it; refresh it by hand. */
    static void refreshLaf(MainPanel p) throws Exception {
        SwingUtilities.invokeAndWait(() -> { SwingUtilities.updateComponentTreeUI(p); SwingUtilities.updateComponentTreeUI(p.menuBar()); });
    }

    static void type(Component c, String text, int delay) throws Exception {
        for (char ch : text.toCharArray()) {
            final char cc = ch;
            SwingUtilities.invokeAndWait(() -> typeKey(c, cc));
            if (delay > 0) Thread.sleep(delay);
        }
    }

    static void contentChecks() {
        check(com.typejunkey.data.Content.themes().size() >= 150, "themes loaded: " + com.typejunkey.data.Content.themes().size());
        check(com.typejunkey.data.Content.layouts().size() >= 200, "layouts loaded: " + com.typejunkey.data.Content.layouts().size());
        check(com.typejunkey.data.Content.languages().contains("english"), "languages: " + com.typejunkey.data.Content.languages());
        check(com.typejunkey.data.Content.language("does_not_exist").words.length > 10, "unknown language falls back");
        check(com.typejunkey.data.Content.layout("does_not_exist") != null, "unknown layout falls back");
        check(com.typejunkey.tutor.TutorLibrary.levels().size() == 5, "tutor levels: " + com.typejunkey.tutor.TutorLibrary.levels());
        int n = 0;
        for (String l : com.typejunkey.tutor.TutorLibrary.levels()) n += com.typejunkey.tutor.TutorLibrary.lessons(l).size();
        check(n >= 46, "tutor lessons: " + n);
        com.typejunkey.model.KeyLayout q = com.typejunkey.data.Content.layout("qwerty");
        com.typejunkey.tutor.FingerMap.Target t = com.typejunkey.tutor.FingerMap.find(q, 'r');
        check(t != null && t.finger.equals("li") && !t.shift, "qwerty r -> left index");
        t = com.typejunkey.tutor.FingerMap.find(q, 'P');
        check(t != null && t.finger.equals("rp") && t.shift && t.shiftFinger.equals("lp"), "qwerty P -> right pinky + left shift pinky");
        t = com.typejunkey.tutor.FingerMap.find(q, ' ');
        check(t != null && t.finger.equals("rt"), "space -> thumb");
    }

    static void behaviorChecks() {
        Settings s = Settings.get();
        for (Settings.Def d : Settings.defs()) {
            if (d.type == Settings.Type.ENUM) {
                boolean ok = false;
                for (String o : d.options) if (o.equals(d.def)) ok = true;
                check(ok, "enum default valid: " + d.key);
            }
        }
        s.mode = "words"; s.words = 10; s.punctuation = false; s.numbers = false; s.language = "english";
        TestEngine e = new TestEngine(s);

        // master: any mistake fails
        s.setQuiet("difficulty", "master"); e.reset(false);
        e.typeChar(e.targets.get(0).charAt(0) == 'x' ? 'y' : 'x', false);
        check(e.isFailed() && e.result() != null && e.result().failed, "master difficulty fails on a mistake");
        s.setQuiet("difficulty", "expert"); e.reset(false);
        e.typeChar('#', false); check(!e.isFailed(), "expert: a wrong letter alone does not fail");
        e.space(); check(e.isFailed(), "expert: submitting a wrong word fails");
        s.setQuiet("difficulty", "normal");

        // stop on error letter
        s.setQuiet("stopOnError", "letter"); e.reset(false);
        e.typeChar('#', false);
        check(e.typedWord(0).isEmpty(), "stop on error (letter) blocks wrong letters");
        s.setQuiet("stopOnError", "word"); e.reset(false);
        e.typeChar('#', false); e.space();
        check(e.cur == 0, "stop on error (word) blocks space on a wrong word");
        s.setQuiet("stopOnError", "off");

        // delete on error
        s.setQuiet("deleteOnError", "letter"); e.reset(false);
        e.typeChar('#', false); check(e.typedWord(0).isEmpty(), "delete on error (letter) removes the wrong letter");
        s.setQuiet("deleteOnError", "off");

        // confidence / freedom
        s.setQuiet("confidenceMode", "max"); e.reset(false);
        char c0 = e.targets.get(0).charAt(0); e.typeChar(c0, false); e.backspace(false);
        check(e.typedWord(0).length() == 1, "confidence max disables backspace");
        s.setQuiet("confidenceMode", "off");
        s.setQuiet("freedomMode", "true"); e.reset(false);
        for (char ch : e.targets.get(0).toCharArray()) e.typeChar(ch, false);
        e.space(); e.backspace(false);
        check(e.cur == 0, "freedom mode: backspace returns to a correct word");
        s.setQuiet("freedomMode", "false");

        // strict space
        s.setQuiet("strictSpace", "true"); e.reset(false);
        e.typeChar(e.targets.get(0).charAt(0), false); e.space();
        check(e.cur == 0, "strict space: early space does not commit");
        s.setQuiet("strictSpace", "false");

        // lazy mode
        s.setQuiet("lazyMode", "true");
        s.mode = "custom"; s.customText = "caf\u00e9 na\u00efve"; e.reset(false);
        for (char ch : "cafe".toCharArray()) e.typeChar(ch, false);
        check(e.typedWord(0).equals("caf\u00e9") || e.cur == 1, "lazy mode accepts plain e for \u00e9: " + e.typedWord(0));
        s.setQuiet("lazyMode", "false");

        // nospace funbox
        s.mode = "words"; s.words = 10; s.setQuiet("funbox", "nospace"); e.reset(false);
        for (char ch : e.targets.get(0).toCharArray()) e.typeChar(ch, false);
        check(e.cur == 1, "nospace: a completed word advances without a space");
        s.setQuiet("funbox", "");

        // quick end
        s.setQuiet("quickEnd", "true"); e.reset(false);
        for (int i = 0; i < 9; i++) { for (char ch : e.targets.get(i).toCharArray()) e.typeChar(ch, false); e.space(); }
        for (int i = 0; i < e.targets.get(9).length(); i++) e.typeChar('#', false);
        check(e.isFinished() && !e.isFailed(), "quick end finishes on the last letter even when wrong");
        s.setQuiet("quickEnd", "false");

        // british english
        check(com.typejunkey.engine.BritishEnglish.apply("color").equals("colour"), "british english: color -> colour");
        check(com.typejunkey.engine.BritishEnglish.apply("Color,").equals("Colour,"), "british english keeps case and punctuation");

        // layout emulation
        check(com.typejunkey.engine.Emulator.map("dvorak", 'w') == ',', "dvorak emulation: w -> ,");
        check(com.typejunkey.engine.Emulator.map("default", 'w') == 'w', "default layout emulation is identity");
        check(com.typejunkey.engine.Emulator.mirror('a') == ';', "layout mirror: a -> ;");

        // every funbox generates words without errors (network ones are skipped)
        for (com.typejunkey.engine.Funbox.Info f : com.typejunkey.engine.Funbox.ALL) {
            if (f.name.equals("wikipedia") || f.name.equals("poetry")) continue;
            for (String mode : new String[] {"words", "time", "quote"}) {
                s.mode = mode; s.setQuiet("funbox", f.name);
                e.reset(false);
                boolean ok = !e.targets.isEmpty();
                for (int i = 0; i < Math.min(8, e.targets.get(0).length()); i++) e.typeChar(e.targets.get(0).charAt(i), false);
                e.space(); e.tick();
                if (!ok) { check(false, "funbox " + f.name + " in " + mode + " produced no words"); }
            }
        }
        check(true, "all " + com.typejunkey.engine.Funbox.ALL.size() + " funbox modifiers generate and accept input");
        s.setQuiet("funbox", "");
        check(com.typejunkey.engine.Funbox.ALL.size() == 48, "funbox catalogue size " + com.typejunkey.engine.Funbox.ALL.size());

        // pace caret custom speed
        s.mode = "time"; s.setQuiet("paceCaret", "custom"); s.setQuiet("paceCaretCustomSpeed", "120"); e.reset(false);
        check(Math.abs(e.paceWpm() - 120) < 0.01, "pace caret custom speed");
        s.setQuiet("paceCaret", "off");
        s.mode = "time";
    }

    static void set(String k, Object v) { Settings.get().setQuiet(k, v); }

    static void apply(MainPanel p) throws Exception {
        Settings.get().fire();
        refreshLaf(p);
        Thread.sleep(250);
    }

    public static void main(String[] a) throws Exception {
        System.setProperty("typejunkey.home", System.getProperty("java.io.tmpdir") + "/tj-side");
        engineChecks();
        contentChecks();
        behaviorChecks();
        if (a.length == 0) return;
        String dir = a[0];
        TypingPane.assumeFocus = true;
        Settings s = Settings.get();
        s.mode = "time"; s.time = 30; s.theme = "serika_dark";
        final MainPanel[] fr = new MainPanel[1];
        SwingUtilities.invokeAndWait(() -> { AppTheme.prepare(); AppTheme.apply(AppTheme.desiredDark()); fr[0] = new MainPanel(); });
        MainPanel p = fr[0];
        System.out.println("UI font: " + new javax.swing.JLabel("x").getFont().getFamily() + " " + new javax.swing.JLabel("x").getFont().getSize()
                + "; type font: " + com.typejunkey.ui.Fonts.type(20).getFamily());
        TypingPane pane = find(p, TypingPane.class);
        Thread.sleep(300);

        // 1. default typing test, toolbar inside the tab
        List<String> words = new ArrayList<>(pane.engine().targets);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) sb.append(words.get(i)).append(' ');
        type(pane, sb.toString(), 20);
        type(pane, "x", 0);
        type(pane, words.get(5).substring(0, 2), 0);
        Thread.sleep(700);
        shot(p, dir + "/20_test_toolbar_in_tab.png", 1);

        // 2. keymap next + word highlight + typo indicators + pace caret + bar timer + tape off
        set("keymapMode", "next"); set("highlightMode", "word"); set("indicateTypos", "both"); set("timerStyle", "bar");
        set("paceCaret", "custom"); set("paceCaretCustomSpeed", "90"); set("caretStyle", "block"); set("showAverage", "off");
        apply(p);
        SwingUtilities.invokeAndWait(pane::newTest);
        words = new ArrayList<>(pane.engine().targets);
        type(pane, words.get(0) + " " + words.get(1) + " ", 40);
        type(pane, "#" + words.get(2).substring(1, 2), 0);
        Thread.sleep(1500);
        shot(p, dir + "/21_keymap_pace_highlight.png", 1);

        // 3. tape mode + monkey caret + funbox mirror
        set("keymapMode", "off"); set("timerStyle", "mini"); set("paceCaret", "off"); set("tapeMode", "letter"); set("caretStyle", "monkey");
        set("funbox", "mirror"); set("highlightMode", "letter");
        apply(p);
        SwingUtilities.invokeAndWait(pane::newTest);
        words = new ArrayList<>(pane.engine().targets);
        type(pane, words.get(0) + " " + words.get(1) + " " + words.get(2).substring(0, 2), 30);
        Thread.sleep(700);
        shot(p, dir + "/22_tape_monkey_mirror.png", 1);
        set("funbox", ""); set("tapeMode", "off"); set("caretStyle", "default");

        // 4. master difficulty failure
        set("difficulty", "master");
        apply(p);
        SwingUtilities.invokeAndWait(pane::newTest);
        words = new ArrayList<>(pane.engine().targets);
        type(pane, words.get(0) + " ", 30);
        type(pane, "#", 0);
        Thread.sleep(400);
        shot(p, dir + "/23_failed_result.png", 2);
        set("difficulty", "normal");

        // 5. words test with heatmap/words history
        set("alwaysShowWordsHistory", "true"); set("burstHeatmap", "true");
        s.mode = "words"; s.words = 25; s.save(); apply(p);
        SwingUtilities.invokeAndWait(pane::newTest);
        words = new ArrayList<>(pane.engine().targets);
        for (int i = 0; i < words.size(); i++) {
            String w = words.get(i);
            for (int k = 0; k < w.length(); k++) {
                final char cc = (i % 7 == 3 && k == 1) ? '#' : w.charAt(k);
                SwingUtilities.invokeAndWait(() -> typeKey(pane, cc));
                Thread.sleep(45 + ((i * 7 + k * 3) % 5) * 22);
            }
            if (i < words.size() - 1) type(pane, " ", 0);
        }
        Thread.sleep(500);
        shot(p, dir + "/24_words_history_heatmap.png", 2);
        set("alwaysShowWordsHistory", "false"); set("burstHeatmap", "false");
        s.mode = "time"; s.save();

        // 6. settings pages
        javax.swing.JList<?> cats = find(find(p, com.typejunkey.ui.SettingsPanel.class), javax.swing.JList.class);
        SwingUtilities.invokeAndWait(() -> cats.setSelectedIndex(4));
        Thread.sleep(300);
        shot(p, dir + "/25_settings_behavior.png", 6);
        SwingUtilities.invokeAndWait(() -> cats.setSelectedIndex(2));
        Thread.sleep(300);
        shot(p, dir + "/26_settings_caret.png", 6);

        // 7. tutor + light theme
        s.theme = "paper"; apply(p);
        shot(p, dir + "/27_tutor_light.png", 0);
        s.theme = "nord"; apply(p);
        System.out.println("auto theme after nord: dark=" + AppTheme.isDark());
        System.exit(0);
    }
}
