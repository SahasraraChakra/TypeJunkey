package com.typejunkey.ui;

import com.typejunkey.data.Content;
import com.typejunkey.data.History;
import com.typejunkey.data.Settings;
import com.typejunkey.engine.Emulator;
import com.typejunkey.engine.Sounds;
import com.typejunkey.engine.TestEngine;
import com.typejunkey.model.KeyLayout;
import com.typejunkey.model.TestResult;
import com.typejunkey.model.Theme;
import com.typejunkey.tutor.FingerMap;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * The typing area: counters, the words, carets, pace caret, effects and the restart hint. Painted by hand so
 * every character can be coloured and positioned individually. Implements all the display settings.
 */
public final class TypingPane extends JComponent {
    /** Test hook for off-screen rendering where no component can hold real keyboard focus. */
    public static volatile boolean assumeFocus;

    private final Settings cfg = Settings.get();
    private final TestEngine engine = new TestEngine(cfg);
    private final Random rnd = new Random();
    private Theme theme = Theme.fallback();
    private Consumer<TestResult> onFinish = r -> { };
    private Runnable onRestart, onPalette;
    private Consumer<Character> keyHook = c -> { };

    private Font font, bigFont, midFont, smallFont, tinyFont;
    private FontMetrics fm, bigFm, midFm, smallFm;
    private String lastFontKey = "";
    private int lineH, charW;

    private int[] wx = new int[0], wl = new int[0];
    private int laidOut, curLine, lastStart, visLines = 3;
    private double tapeOffset, scrollOff;
    private final Caret main = new Caret(), pace = new Caret();
    private boolean blinkOn = true, tabPending, reported, shiftL, shiftR;
    private long lastInput, flashUntil, wordShownAt;
    private int lastCur = -1;
    private String spokenFor = "";

    // effects
    private final List<double[]> particles = new ArrayList<>(); // x, y, vx, vy, life, hue
    private long shakeUntil;
    private double shakeAmp;
    private double fxPhase;

    private final Timer anim = new Timer(12, e -> step());
    private final Timer tick = new Timer(100, e -> onTick());
    private final Timer blink = new Timer(520, e -> {
        if (System.currentTimeMillis() - lastInput > 600 && focused()) { blinkOn = !blinkOn; repaint(); }
    });

    private static final class Caret {
        double x, y, w, tx, ty, tw;
        boolean init;
        boolean moving() { return Math.abs(tx - x) > 0.4 || Math.abs(ty - y) > 0.4 || Math.abs(tw - w) > 0.4; }
        void snap() { x = tx; y = ty; w = tw; init = true; }
        void stepTo(double k) { x += (tx - x) * k; y += (ty - y) * k; w += (tw - w) * k; if (!moving()) snap(); }
    }

    public TypingPane() {
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        setOpaque(true);
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { handlePressed(e); }
            @Override public void keyReleased(KeyEvent e) { handleReleased(e); }
            @Override public void keyTyped(KeyEvent e) { handleTyped(e); }
        });
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { requestFocusInWindow(); }
        });
        addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { blinkOn = true; repaint(); }
            @Override public void focusLost(FocusEvent e) { repaint(); }
        });
        engine.setOnWordsChanged(() -> SwingUtilities.invokeLater(() -> { resetView(); }));
        tick.start();
        blink.start();
        engine.reset(false);
    }

    // ------------------------------------------------------------------ API

    public TestEngine engine() { return engine; }
    public void setTheme(Theme t) { theme = t; repaint(); }
    public void setOnFinish(Consumer<TestResult> c) { onFinish = c; }
    public void setOnRestart(Runnable r) { onRestart = r; }
    public void setOnPalette(Runnable r) { onPalette = r; }
    public void setKeyHook(Consumer<Character> c) { keyHook = c == null ? x -> { } : c; }

    public boolean canRestart() { return !(engine.funbox().noQuit() && engine.isStarted() && !engine.isFinished()); }

    public void newTest() { if (!canRestart()) return; engine.reset(false); resetView(); }
    public void repeatTest() { if (!canRestart()) return; engine.reset(true); resetView(); }

    private void resetView() {
        main.init = false; pace.init = false; scrollOff = 0; lastStart = 0; tabPending = false; reported = false;
        tapeOffset = 0; particles.clear(); lastCur = -1; spokenFor = "";
        revalidate();
        repaint();
    }

    private boolean focused() { return assumeFocus || hasFocus(); }

    // ------------------------------------------------------------------ input

    private void restart() {
        if (!canRestart()) return;
        if (onRestart != null) onRestart.run(); else newTest();
    }

    private void handlePressed(KeyEvent e) {
        int k = e.getKeyCode();
        String quick = cfg.str("quickRestart");
        if (k == KeyEvent.VK_SHIFT) {
            if (e.getKeyLocation() == KeyEvent.KEY_LOCATION_RIGHT) shiftR = true; else shiftL = true;
            return;
        }
        if (k == KeyEvent.VK_P && e.isControlDown() && e.isShiftDown()) { if (onPalette != null) onPalette.run(); e.consume(); return; }
        if (engine.funbox().arrows() && !engine.isFinished()) {
            char arrow = k == KeyEvent.VK_LEFT ? '\u2190' : k == KeyEvent.VK_UP ? '\u2191' : k == KeyEvent.VK_DOWN ? '\u2193' : k == KeyEvent.VK_RIGHT ? '\u2192' : 0;
            if (arrow != 0) { handleChar(arrow, false); e.consume(); return; }
        }
        if (k == KeyEvent.VK_TAB) {
            if (quick.equals("tab")) restart(); else tabPending = true;
            e.consume();
            return;
        }
        if (k == KeyEvent.VK_ENTER) {
            if (quick.equals("enter") && !(e.isShiftDown() && engine.mode.equals("zen"))) restart();
            else if (tabPending) { tabPending = false; restart(); }
            else if (e.isShiftDown() && engine.mode.equals("zen")) { engine.finishZen(); afterInput(); }
            e.consume();
            return;
        }
        if (k == KeyEvent.VK_ESCAPE) {
            if (quick.equals("esc")) restart(); else if (onPalette != null) onPalette.run();
            e.consume();
            return;
        }
        if (k == KeyEvent.VK_BACK_SPACE) {
            engine.backspace(e.isControlDown() || e.isAltDown());
            Sounds.click();
            afterInput();
            e.consume();
            return;
        }
        if (k != KeyEvent.VK_CONTROL && k != KeyEvent.VK_ALT) tabPending = false;
    }

    private void handleReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SHIFT) {
            if (e.getKeyLocation() == KeyEvent.KEY_LOCATION_RIGHT) shiftR = false; else shiftL = false;
        }
    }

    private void handleTyped(KeyEvent e) {
        char ch = e.getKeyChar();
        if (e.isControlDown() && !e.isAltDown()) return;
        if (ch < 32 || ch == 127 || ch == KeyEvent.CHAR_UNDEFINED) return;
        handleChar(ch, true);
    }

    private void handleChar(char raw, boolean emulate) {
        if (engine.isFinished()) return;
        char c = raw;
        if (emulate) {
            String fluid = engine.fluidLayout();
            String layout = fluid != null ? fluid : cfg.str("layout");
            c = Emulator.map(layout, c);
            if (engine.funbox().on("layout_mirror")) c = Emulator.mirror(c);
        }
        TestEngine.Outcome o;
        if (c == ' ') {
            o = engine.space();
        } else {
            boolean bad = false;
            if (cfg.str("oppositeShiftMode").equals("on")) {
                FingerMap.Target t = FingerMap.find(Content.layout(displayLayoutName()), c);
                if (t != null && t.shift && (shiftL || shiftR)) {
                    boolean keyLeft = t.finger.startsWith("l");
                    bad = keyLeft ? shiftL && !shiftR : shiftR && !shiftL;
                }
            }
            o = engine.typeChar(c, bad);
        }
        if (o != TestEngine.Outcome.IGNORED) {
            keyHook.accept(c);
            Sounds.click();
            if (o == TestEngine.Outcome.WRONG) { Sounds.error(); flashUntil = System.currentTimeMillis() + 160; }
            effectsOnKey(o == TestEngine.Outcome.WRONG);
        }
        afterInput();
    }

    private void effectsOnKey(boolean wrong) {
        int lvl = parseLevel(cfg.str("monkeyPowerLevel"));
        if (lvl <= 0) return;
        shakeAmp = lvl * 1.5;
        shakeUntil = System.currentTimeMillis() + 110;
        if (!wrong) {
            int n = lvl * 3;
            for (int i = 0; i < n; i++) {
                double a = rnd.nextDouble() * Math.PI * 2, sp = 1.5 + rnd.nextDouble() * (2 + lvl);
                particles.add(new double[] {main.x, main.y + lineH / 2.0, Math.cos(a) * sp, Math.sin(a) * sp - 1.5, 1.0, rnd.nextDouble()});
            }
        }
        startAnim();
    }

    private static int parseLevel(String s) { try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; } }

    public String displayLayoutName() {
        String k = cfg.str("keymapLayout");
        String fluid = engine.fluidLayout();
        if (fluid != null) return fluid;
        if (!k.equals("sync")) return k;
        String l = cfg.str("layout");
        return l.equals("default") ? "qwerty" : l;
    }

    private void afterInput() {
        lastInput = System.currentTimeMillis();
        blinkOn = true;
        checkFinished();
        repaint();
    }

    private void checkFinished() {
        if (engine.isFinished() && engine.result() != null && !reported) {
            reported = true;
            onFinish.accept(engine.result());
        }
        if (!engine.isFinished()) reported = false;
    }

    private void onTick() {
        if (engine.isStarted() && !engine.isFinished()) {
            engine.tick();
            String tw = cfg.str("playTimeWarning");
            if (!tw.equals("off")) { try { if (engine.timeWarningDue(Integer.parseInt(tw))) Sounds.timeWarning(); } catch (NumberFormatException ignored) { } }
            checkFinished();
            repaint();
        }
        if (engine.funbox().on("tts") && engine.cur != lastCur) { lastCur = engine.cur; speakCurrent(); }
        if (engine.funbox().any() || cfg.bool("monkey")) { fxPhase += 0.1; if (needsContinuousFx()) repaint(); }
    }

    private boolean needsContinuousFx() {
        for (String n : new String[] {"nausea", "round_round_baby", "choo_choo", "earthquake", "space_balls", "crt", "simon_says"}) if (engine.funbox().on(n)) return true;
        return cfg.bool("monkey");
    }

    private void speakCurrent() {
        if (engine.cur >= engine.targets.size()) return;
        String w = engine.targets.get(engine.cur);
        if (w.equals(spokenFor)) return;
        spokenFor = w;
        final String word = w.replaceAll("[^\\p{L}\\p{N} ]", " ");
        Thread t = new Thread(() -> {
            try {
                String os = System.getProperty("os.name", "").toLowerCase();
                ProcessBuilder pb;
                if (os.contains("win")) pb = new ProcessBuilder("powershell", "-NoProfile", "-Command",
                        "Add-Type -AssemblyName System.Speech; (New-Object System.Speech.Synthesis.SpeechSynthesizer).Speak('" + word.replace("'", "") + "')");
                else if (os.contains("mac")) pb = new ProcessBuilder("say", word);
                else pb = new ProcessBuilder("espeak", word);
                pb.redirectErrorStream(true);
                pb.start();
            } catch (Exception ignored) { }
        }, "tts");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------------ fonts & layout

    private void ensureFonts() {
        if (font != null && lastFontKey.equals(Fonts.typeKey())) return;
        lastFontKey = Fonts.typeKey();
        font = Fonts.type(cfg.num("fontSize"));
        bigFont = font.deriveFont(Font.PLAIN, cfg.num("fontSize") * 1.45f);
        midFont = font.deriveFont(Font.PLAIN, cfg.num("fontSize") * 0.95f);
        smallFont = font.deriveFont(Font.PLAIN, Math.max(12f, cfg.num("fontSize") * 0.58f));
        tinyFont = font.deriveFont(Font.PLAIN, Math.max(9f, cfg.num("fontSize") * 0.42f));
        fm = getFontMetrics(font);
        bigFm = getFontMetrics(bigFont);
        midFm = getFontMetrics(midFont);
        smallFm = getFontMetrics(smallFont);
        lineH = (int) Math.round(fm.getHeight() * 1.45);
        charW = fm.charWidth('M');
    }

    private boolean tape() { return !cfg.str("tapeMode").equals("off"); }

    private int visibleLines() {
        if (tape()) return 1;
        return cfg.bool("showAllLines") ? 6 : 3;
    }

    private int statsH() { return bigFm.getHeight() + 14; }

    @Override
    public Dimension getPreferredSize() {
        ensureFonts();
        return new Dimension(700, statsH() + 12 + lineH * visibleLines() + 60);
    }

    private int spaceW() { return engine.nospace() ? 0 : fm.charWidth(' '); }

    private int wordWidth(int i) {
        String t = engine.targets.get(i), w = engine.typedWord(i);
        int width = 0;
        for (int k = 0; k < t.length(); k++) width += fm.charWidth(t.charAt(k));
        if (!cfg.bool("hideExtraLetters")) for (int k = t.length(); k < w.length(); k++) width += fm.charWidth(w.charAt(k));
        return width;
    }

    private void layoutWords(int availW) {
        int n;
        synchronized (engine.targets) { n = engine.targets.size(); }
        if (wx.length < n) { wx = new int[n + 64]; wl = new int[n + 64]; }
        int space = spaceW();
        int x = 0, line = 0;
        boolean seenCur = false;
        boolean tp = tape();
        for (int i = 0; i < n; i++) {
            int w = wordWidth(i);
            if (!tp && x > 0 && x + w > availW) { line++; x = 0; }
            wx[i] = x; wl[i] = line;
            x += w + space;
            if (i == engine.cur) { curLine = line; seenCur = true; }
            else if (seenCur && !tp && line > curLine + visLines) { laidOut = i + 1; return; }
            else if (seenCur && tp && x > wx[engine.cur] + 3 * availW) { laidOut = i + 1; return; }
        }
        if (!seenCur) curLine = n == 0 ? 0 : wl[n - 1];
        laidOut = n;
    }

    // ------------------------------------------------------------------ animation

    private void startAnim() { if (!anim.isRunning()) anim.start(); }

    private double smoothK() {
        switch (cfg.str("smoothCaret")) {
            case "slow": return 0.12;
            case "fast": return 0.5;
            case "off": return 1.0;
            default: return 0.27;
        }
    }

    private void step() {
        double k = smoothK();
        main.stepTo(k);
        pace.stepTo(k);
        scrollOff *= (1 - 0.3);
        if (Math.abs(scrollOff) < 0.5) scrollOff = 0;
        boolean active = main.moving() || pace.moving() || scrollOff != 0;
        for (int i = particles.size() - 1; i >= 0; i--) {
            double[] p = particles.get(i);
            p[0] += p[2]; p[1] += p[3]; p[3] += 0.18; p[4] -= 0.045;
            if (p[4] <= 0) particles.remove(i);
        }
        if (!particles.isEmpty() || System.currentTimeMillis() < shakeUntil) active = true;
        if (!active) anim.stop();
        repaint();
    }

    // ------------------------------------------------------------------ painting

    private Color text() { return cfg.bool("flipTestColors") ? theme.sub : theme.text; }
    private Color untyped() { return cfg.bool("flipTestColors") ? theme.text : theme.sub; }

    private static Color alpha(Color c, double a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) Math.round(255 * Math.max(0, Math.min(1, a)))); }

    private double unitFactor() {
        switch (cfg.str("typingSpeedUnit")) {
            case "cpm": return 5;
            case "wps": return 1 / 60.0;
            case "cps": return 5 / 60.0;
            case "wph": return 60;
            default: return 1;
        }
    }

    private String fmt(double v) {
        return cfg.bool("alwaysShowDecimalPlaces") ? String.format("%.2f", v) : Long.toString(Math.round(v));
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Render.hints(g);
        ensureFonts();
        int W = getWidth(), H = getHeight();
        if (!Backdrop.paint(g, this, theme.bg)) { g.setColor(theme.bg); g.fillRect(0, 0, W, H); }
        if (engine.funbox().on("space_balls")) paintStars(g, W, H);

        visLines = visibleLines();
        int availW = cfg.num("maxLineWidth") > 0 ? Math.min(W - 40, cfg.num("maxLineWidth") * charW) : Math.min(W - 80, 1000);
        if (availW < 160) availW = Math.max(80, W - 16);
        int ox = (W - availW) / 2;
        int textTop = statsH() + 12;

        layoutWords(availW);
        int startLine = tape() ? 0 : Math.max(0, curLine - (visLines > 3 ? visLines / 3 : 1));
        if (startLine != lastStart) {
            if (cfg.bool("smoothLineScroll")) { scrollOff += (startLine - lastStart) * lineH; startAnim(); }
            lastStart = startLine;
        }
        if (!cfg.bool("smoothLineScroll")) scrollOff = 0;

        paintStats(g, ox, availW);

        Rectangle text = new Rectangle(ox - 6, textTop, availW + 12, lineH * visLines);
        Graphics2D tg = (Graphics2D) g.create();
        tg.clip(new Rectangle(0, textTop - 4, W, lineH * visLines + 8));
        applyFunboxTransform(tg, text);
        long now = System.currentTimeMillis();
        if (now < shakeUntil) tg.translate((rnd.nextDouble() - 0.5) * 2 * shakeAmp, (rnd.nextDouble() - 0.5) * 2 * shakeAmp);
        tg.setFont(font);

        // tape offset
        if (tape()) {
            int cur = Math.min(engine.cur, Math.max(0, engine.targets.size() - 1));
            int caretX = wx[cur] + caretOffsetInWord(cur);
            if (cfg.str("tapeMode").equals("word")) caretX = wx[cur];
            double target = caretX - availW * cfg.num("tapeMargin") / 100.0;
            if (cfg.str("smoothCaret").equals("off") || tapeOffset == 0 && !main.init) tapeOffset = target;
            else { tapeOffset += (target - tapeOffset) * Math.max(0.2, smoothK()); if (Math.abs(target - tapeOffset) > 0.5) startAnim(); }
        }

        int shift = (int) Math.round(scrollOff);
        boolean rtl = engine.funbox().reverseDirection();
        for (int i = 0; i < laidOut; i++) {
            int line = wl[i];
            if (!tape() && (line < startLine - 1 || line > startLine + visLines)) continue;
            int y = textTop + (line - startLine) * lineH + shift;
            int base = y + (lineH - fm.getHeight()) / 2 + fm.getAscent();
            int px = tape() ? (int) Math.round(ox + wx[i] - tapeOffset) : ox + wx[i];
            if (rtl && !tape()) px = ox + availW - wx[i] - wordWidth(i);
            if (tape() && (px > W + 200 || px < -400)) continue;
            paintWord(tg, i, px, base, y);
        }
        updateCaretTargets(ox, availW, textTop, startLine, rtl);
        if (!engine.isFinished() && focused() && blinkOn) paintCaret(tg, main, cfg.str("caretStyle"), theme.caret);
        if (engine.paceWpm() > 0 && engine.isStarted() && !engine.isFinished() && !cfg.str("paceCaretStyle").equals("off")) {
            paintCaret(tg, pace, cfg.str("paceCaretStyle"), alpha(theme.sub, 0.9));
        }
        for (double[] p : particles) {
            tg.setColor(alpha(Color.getHSBColor((float) p[5], 0.6f, 1f), p[4]));
            tg.fill(new Ellipse2D.Double(p[0] - 2, p[1] - 2, 4, 4));
        }
        tg.dispose();

        if (cfg.bool("showKeyTips")) {
            g.setFont(smallFont);
            g.setColor(theme.sub);
            String hint = hintText();
            g.drawString(hint, (W - smallFm.stringWidth(hint)) / 2f, textTop + lineH * visLines + 30 + smallFm.getAscent() / 2f);
        }
        if (engine.fetchFailed()) {
            g.setFont(smallFont);
            g.setColor(theme.error);
            String m = "no internet connection: using normal words";
            g.drawString(m, (W - smallFm.stringWidth(m)) / 2f, textTop + lineH * visLines + 54);
        }
        if (cfg.bool("capsLockWarning") && capsOn()) {
            g.setFont(smallFont);
            g.setColor(theme.main);
            String m = "Caps Lock";
            g.drawString(m, W - ox - smallFm.stringWidth(m), bigFm.getAscent() + 4);
        }
        if (engine.funbox().on("crt")) paintCrt(g, W, H);
        if (cfg.bool("monkey")) paintMonkey(g, W, H);
        if (!focused() && cfg.bool("showOutOfFocusWarning")) {
            g.setComposite(AlphaComposite.SrcOver.derive(0.78f));
            g.setColor(theme.bg);
            g.fillRect(text.x - 4, text.y - 4, text.width + 8, text.height + 8);
            g.setComposite(AlphaComposite.SrcOver);
            g.setFont(smallFont.deriveFont(Font.PLAIN, smallFont.getSize2D() + 2f));
            g.setColor(theme.text);
            String msg = "Click here or press Tab to focus";
            FontMetrics mf = g.getFontMetrics();
            g.drawString(msg, (W - mf.stringWidth(msg)) / 2f, text.y + text.height / 2f);
        }
        g.dispose();
    }

    private String hintText() {
        switch (cfg.str("quickRestart")) {
            case "tab": return "tab \u2014 restart test";
            case "esc": return "esc \u2014 restart test";
            case "enter": return "enter \u2014 restart test";
            default: return "tab + enter \u2014 restart test";
        }
    }

    private boolean capsOn() {
        try { return Toolkit.getDefaultToolkit().getLockingKeyState(KeyEvent.VK_CAPS_LOCK); } catch (RuntimeException e) { return false; }
    }

    private int caretOffsetInWord(int i) {
        String t = engine.targets.get(i), w = engine.typedWord(i);
        int off = 0;
        for (int k = 0; k < w.length(); k++) off += fm.charWidth(k < t.length() ? t.charAt(k) : w.charAt(k));
        return off;
    }

    // ---- stats line
    private void paintStats(Graphics2D g, int ox, int availW) {
        int base = 4 + bigFm.getAscent();
        String timerStyle = cfg.str("timerStyle");
        String big;
        switch (engine.mode) {
            case "time": big = Integer.toString(engine.isStarted() ? engine.timeLeft() : engine.timeLimit); break;
            case "zen": big = Integer.toString(engine.cur); break;
            default: big = engine.cur + "/" + engine.totalWords();
        }
        boolean flash = timerStyle.startsWith("flash");
        boolean show = !timerStyle.equals("off");
        if (flash) show = engine.isStarted() && (!engine.mode.equals("time") || engine.timeLeft() <= 10);
        Color tc;
        switch (cfg.str("timerColor")) {
            case "black": tc = Color.BLACK; break;
            case "sub": tc = theme.sub; break;
            case "text": tc = theme.text; break;
            default: tc = theme.main;
        }
        double op = Double.parseDouble(cfg.str("timerOpacity"));
        int x = ox;
        if (timerStyle.equals("bar")) {
            double frac = engine.mode.equals("time") ? (engine.isStarted() ? 1 - engine.elapsed() / engine.timeLimit : 1)
                    : (engine.totalWords() == 0 ? 0 : (double) engine.cur / engine.totalWords());
            g.setColor(alpha(theme.subAlt, 1));
            g.fill(new Rectangle2D.Double(ox, 6, availW, 6));
            g.setColor(alpha(tc, op));
            g.fill(new Rectangle2D.Double(ox, 6, availW * Math.max(0, Math.min(1, frac)), 6));
            x = ox;
        } else if (show) {
            boolean mini = timerStyle.endsWith("mini");
            g.setFont(mini ? midFont : bigFont);
            g.setColor(alpha(tc, op));
            g.drawString(big, ox, base);
            x = ox + (mini ? midFm.stringWidth(big) : bigFm.stringWidth(big)) + 28;
        }
        g.setFont(smallFont);
        double u = unitFactor();
        String label = cfg.str("typingSpeedUnit");
        List<String[]> items = new ArrayList<>();
        if (engine.isStarted()) {
            if (!cfg.str("liveSpeedStyle").equals("off")) items.add(new String[] {label, fmt(engine.liveWpm() * u), cfg.str("liveSpeedStyle")});
            if (!cfg.str("liveAccStyle").equals("off")) items.add(new String[] {"acc", Math.round(engine.liveAcc()) + "%", cfg.str("liveAccStyle")});
            if (!cfg.str("liveBurstStyle").equals("off")) items.add(new String[] {"burst", fmt(engine.lastBurst() * u), cfg.str("liveBurstStyle")});
        }
        String avg = cfg.str("showAverage");
        if (!avg.equals("off")) {
            double[] a = averages();
            if (a != null) {
                if (avg.equals("speed") || avg.equals("both")) items.add(new String[] {"avg " + label, fmt(a[0] * u), "mini"});
                if (avg.equals("acc") || avg.equals("both")) items.add(new String[] {"avg acc", Math.round(a[1]) + "%", "mini"});
            }
        }
        if (cfg.bool("showPb")) {
            double pb = History.best(TestResult.key(cfg.mode, cfg.param(), cfg.language, cfg.punctuation, cfg.numbers));
            if (pb > 0) items.add(new String[] {"pb", fmt(pb * u), "mini"});
        }
        for (String[] it : items) {
            Font f = it[2].equals("text") ? midFont : smallFont;
            FontMetrics m = getFontMetrics(f);
            g.setFont(f);
            g.setColor(theme.sub);
            g.drawString(it[0], x, base);
            x += m.stringWidth(it[0] + " ");
            g.setColor(theme.main);
            g.drawString(it[1], x, base);
            x += m.stringWidth(it[1]) + 22;
        }
    }

    private double[] cachedAvg;
    private long cachedAvgAt;

    private double[] averages() {
        long now = System.currentTimeMillis();
        if (cachedAvg != null && now - cachedAvgAt < 2000) return cachedAvg;
        String key = TestResult.key(cfg.mode, cfg.param(), cfg.language, cfg.punctuation, cfg.numbers);
        List<TestResult> same = new ArrayList<>();
        for (TestResult r : History.all()) if (r.key().equals(key)) same.add(r);
        if (same.isEmpty()) { cachedAvg = null; return null; }
        int from = Math.max(0, same.size() - 10);
        double w = 0, a = 0;
        for (int i = from; i < same.size(); i++) { w += same.get(i).wpm; a += same.get(i).acc; }
        cachedAvg = new double[] {w / (same.size() - from), a / (same.size() - from)};
        cachedAvgAt = now;
        return cachedAvg;
    }

    // ---- words
    private boolean simonVisible(int i) {
        if (!engine.funbox().on("simon_says")) return true;
        if (i < engine.cur) return true;
        if (i != engine.cur) return false;
        if (engine.cur != lastCur) { lastCur = engine.cur; wordShownAt = System.currentTimeMillis(); }
        return System.currentTimeMillis() - wordShownAt < 1800 || engine.typedWord(i).length() == 0 && !engine.isStarted();
    }

    private void paintWord(Graphics2D g, int i, int x, int base, int lineTop) {
        String t = engine.targets.get(i), w = engine.typedWord(i);
        boolean committed = i < engine.cur, zen = engine.mode.equals("zen");
        boolean blind = cfg.bool("blindMode"), colorful = cfg.bool("colorfulMode");
        boolean funVisible = engine.wordVisible(i) && simonVisible(i) && !engine.funbox().on("tts");
        String typedEffect = cfg.str("typedEffect");
        double a = 1;
        if (committed) {
            if (typedEffect.equals("hide")) return;
            if (typedEffect.equals("fade")) a = Math.max(0, 1 - (engine.cur - i) / 6.0);
            if (a <= 0) return;
        }
        // highlight backgrounds
        String hm = cfg.str("highlightMode");
        int width = wordWidth(i);
        int rel = i - engine.cur;
        if (hm.equals("word") && rel == 0 || (hm.equals("next_word") && rel == 1) || (hm.equals("next_two_words") && rel >= 1 && rel <= 2)
                || (hm.equals("next_three_words") && rel >= 1 && rel <= 3)) {
            g.setColor(alpha(theme.main, rel == 0 ? 0.16 : 0.1));
            g.fill(new RoundRectangle2D.Double(x - 3, base - fm.getAscent() - 2, width + 6, fm.getHeight() + 4, 6, 6));
        }
        int x0 = x;
        String indicate = cfg.str("indicateTypos");
        for (int k = 0; k < t.length(); k++) {
            char c = t.charAt(k);
            int cw = fm.charWidth(c);
            Color col;
            boolean wrong = false;
            if (zen) col = text();
            else if (k < w.length()) {
                wrong = w.charAt(k) != c;
                col = wrong ? (blind ? text() : (colorful ? theme.colorfulError : theme.error)) : (colorful ? theme.main : text());
            } else col = untyped();
            if (!funVisible && !committed) { x += cw; continue; }
            if (i == engine.cur && k == w.length() && hm.equals("letter") && !engine.isFinished()) {
                g.setColor(alpha(theme.main, 0.2));
                g.fill(new RoundRectangle2D.Double(x - 1, base - fm.getAscent() - 1, cw + 2, fm.getHeight() + 2, 5, 5));
            }
            g.setColor(alpha(col, a));
            String shown = String.valueOf(c);
            if (wrong && !blind && (indicate.equals("replace") || indicate.equals("both"))) shown = String.valueOf(w.charAt(k));
            if (committed && typedEffect.equals("dots") && k < w.length()) shown = "\u2022";
            if (!shown.equals(" ")) drawChar(g, shown, x, base, cw, i, k);
            if (wrong && !blind && (indicate.equals("below") || indicate.equals("both"))) {
                g.setFont(tinyFont);
                g.setColor(alpha(theme.error, a));
                g.drawString(String.valueOf(w.charAt(k)), x + (cw - getFontMetrics(tinyFont).charWidth(w.charAt(k))) / 2f, base + fm.getDescent() + getFontMetrics(tinyFont).getAscent() * 0.9f);
                g.setFont(font);
            }
            if (committed && !zen && k >= w.length() && !blind) {
                g.setColor(alpha(theme.error, a));
                g.fillRect(x, base + 3, cw, 1);
            }
            x += cw;
        }
        if (!cfg.bool("hideExtraLetters")) {
            for (int k = t.length(); k < w.length(); k++) {
                char c = w.charAt(k);
                int cw = fm.charWidth(c);
                if (!blind) {
                    g.setColor(alpha(colorful ? theme.colorfulErrorExtra : theme.errorExtra, a));
                    drawChar(g, String.valueOf(c), x, base, cw, i, k);
                }
                x += cw;
            }
        }
        if (committed && !zen && !w.equals(t) && !blind) {
            g.setColor(alpha(theme.error, a));
            g.fillRect(x0, base + 4, x - x0, 2);
        }
    }

    private void drawChar(Graphics2D g, String s, int x, int base, int cw, int wordIdx, int k) {
        if (engine.funbox().on("choo_choo") && wordIdx >= engine.cur - 1) {
            AffineTransform old = g.getTransform();
            double ang = (fxPhase * 2 + wordIdx * 0.7 + k * 0.9) % (Math.PI * 2);
            g.rotate(ang, x + cw / 2.0, base - fm.getAscent() / 2.0);
            g.drawString(s, x, base);
            g.setTransform(old);
        } else {
            g.drawString(s, x, base);
        }
    }

    // ---- carets
    private void updateCaretTargets(int ox, int availW, int textTop, int startLine, boolean rtl) {
        int n = engine.targets.size();
        if (engine.cur < n) {
            setCaret(main, engine.cur, caretOffsetInWord(engine.cur), ox, availW, textTop, startLine, rtl);
        }
        if (engine.paceWpm() > 0 && engine.isStarted()) {
            int[] loc = engine.locate(engine.paceChars());
            if (loc != null && loc[0] < laidOut) {
                String t = engine.targets.get(loc[0]);
                int off = 0;
                for (int k = 0; k < loc[1] && k < t.length(); k++) off += fm.charWidth(t.charAt(k));
                setCaret(pace, loc[0], off, ox, availW, textTop, startLine, rtl);
            }
        }
    }

    private void setCaret(Caret c, int wordIdx, int off, int ox, int availW, int textTop, int startLine, boolean rtl) {
        String t = engine.targets.get(wordIdx);
        int line = wl[wordIdx];
        double px = tape() ? ox + wx[wordIdx] - tapeOffset + off : ox + wx[wordIdx] + off;
        if (rtl && !tape()) px = ox + availW - wx[wordIdx] - wordWidth(wordIdx) + off;
        c.tx = px;
        c.ty = textTop + (tape() ? 0 : (line - startLine) * lineH) + (lineH - caretH()) / 2.0;
        String w = engine.typedWord(wordIdx);
        int idx = c == main ? w.length() : off == 0 ? 0 : 0;
        char under = c == main ? (idx < t.length() ? t.charAt(idx) : ' ') : 'M';
        c.tw = fm.charWidth(under);
        if (!c.init || cfg.str("smoothCaret").equals("off")) c.snap();
        else if (c.moving()) startAnim();
    }

    private int caretH() { return fm.getHeight() + 2; }

    private void paintCaret(Graphics2D g, Caret c, String style, Color color) {
        if (style.equals("off")) return;
        double h = caretH();
        g.setColor(color);
        switch (style) {
            case "block": {
                g.fill(new RoundRectangle2D.Double(c.x, c.y, c.w, h, 3, 3));
                if (c == main && engine.cur < engine.targets.size()) {
                    String t = engine.targets.get(engine.cur);
                    int l = engine.typedWord(engine.cur).length();
                    if (l < t.length()) {
                        g.setColor(theme.bg);
                        g.setFont(font);
                        g.drawString(String.valueOf(t.charAt(l)), (float) c.x, (float) (c.y + (h - fm.getHeight()) / 2 + fm.getAscent()));
                    }
                }
                break;
            }
            case "outline":
                g.setStroke(new BasicStroke(2f));
                g.draw(new RoundRectangle2D.Double(c.x + 1, c.y + 1, c.w - 2, h - 2, 3, 3));
                break;
            case "underline":
                g.fill(new Rectangle2D.Double(c.x, c.y + h - 2, c.w, 2));
                break;
            case "carrot": paintCarrot(g, c.x, c.y, h); break;
            case "banana": paintBanana(g, c.x, c.y, h); break;
            case "monkey": paintMonkeyCaret(g, c.x, c.y, h); break;
            default:
                g.fill(new Rectangle2D.Double(c.x - 1, c.y, 2, h));
        }
    }

    private void paintCarrot(Graphics2D g, double x, double y, double h) {
        double s = h * 0.9;
        Path2D body = new Path2D.Double();
        body.moveTo(x - s * 0.22, y + h * 0.28); body.quadTo(x, y + h * 0.18, x + s * 0.22, y + h * 0.28);
        body.lineTo(x + s * 0.02, y + h * 0.98); body.closePath();
        g.setColor(new Color(0xff8a1f)); g.fill(body);
        g.setColor(new Color(0x3fa34d));
        g.fill(new Ellipse2D.Double(x - s * 0.3, y + h * 0.02, s * 0.26, s * 0.3));
        g.fill(new Ellipse2D.Double(x + s * 0.04, y + h * 0.02, s * 0.26, s * 0.3));
        g.fill(new Ellipse2D.Double(x - s * 0.1, y - h * 0.04, s * 0.2, s * 0.34));
    }

    private void paintBanana(Graphics2D g, double x, double y, double h) {
        g.setStroke(new BasicStroke((float) (h * 0.2), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0xf5d000));
        g.draw(new Arc2D.Double(x - h * 0.5, y + h * 0.05, h * 0.9, h * 0.9, 200, 140, Arc2D.OPEN));
        g.setColor(new Color(0x7a5a14));
        g.fill(new Ellipse2D.Double(x - h * 0.42, y + h * 0.18, h * 0.12, h * 0.12));
    }

    private void paintMonkeyCaret(Graphics2D g, double x, double y, double h) {
        double r = h * 0.42;
        g.setColor(new Color(0x7a4b25));
        g.fill(new Ellipse2D.Double(x - r * 1.35, y + h * 0.16, r * 0.8, r * 0.8));
        g.fill(new Ellipse2D.Double(x + r * 0.55, y + h * 0.16, r * 0.8, r * 0.8));
        g.fill(new Ellipse2D.Double(x - r, y + h * 0.18, r * 2, r * 2));
        g.setColor(new Color(0xe8c39e));
        g.fill(new Ellipse2D.Double(x - r * 0.65, y + h * 0.5, r * 1.3, r * 1.0));
        g.setColor(Color.BLACK);
        g.fill(new Ellipse2D.Double(x - r * 0.45, y + h * 0.42, r * 0.2, r * 0.2));
        g.fill(new Ellipse2D.Double(x + r * 0.25, y + h * 0.42, r * 0.2, r * 0.2));
    }

    // ---- funbox visuals
    private void applyFunboxTransform(Graphics2D g, Rectangle text) {
        double cx = text.getCenterX(), cy = text.getCenterY();
        if (engine.funbox().on("mirror")) { g.translate(cx, 0); g.scale(-1, 1); g.translate(-cx, 0); }
        if (engine.funbox().on("upside_down")) g.rotate(Math.PI, cx, cy);
        if (engine.funbox().on("round_round_baby")) g.rotate(Math.sin(fxPhase * 0.35) * 0.5, cx, cy);
        if (engine.funbox().on("nausea")) {
            g.rotate(Math.sin(fxPhase * 0.7) * 0.05, cx, cy);
            g.shear(Math.sin(fxPhase * 0.5) * 0.12, 0);
            g.scale(1 + Math.sin(fxPhase * 0.6) * 0.06, 1 + Math.cos(fxPhase * 0.8) * 0.06);
        }
        if (engine.funbox().on("earthquake")) g.translate(rnd.nextGaussian() * 3, rnd.nextGaussian() * 3);
    }

    private final double[][] stars = new double[60][3];
    private boolean starsInit;

    private void paintStars(Graphics2D g, int W, int H) {
        if (!starsInit) { for (double[] s : stars) { s[0] = rnd.nextDouble(); s[1] = rnd.nextDouble(); s[2] = 0.4 + rnd.nextDouble(); } starsInit = true; }
        g.setColor(alpha(theme.text, 0.55));
        for (double[] s : stars) {
            s[0] -= 0.0008 * s[2];
            if (s[0] < 0) { s[0] = 1; s[1] = rnd.nextDouble(); }
            double r = 1 + s[2] * 1.5;
            g.fill(new Ellipse2D.Double(s[0] * W, s[1] * H, r, r));
        }
    }

    private void paintCrt(Graphics2D g, int W, int H) {
        g.setColor(new Color(0, 0, 0, 38));
        for (int y = 0; y < H; y += 3) g.fillRect(0, y, W, 1);
        java.awt.RadialGradientPaint rg = new java.awt.RadialGradientPaint(W / 2f, H / 2f, Math.max(W, H) * 0.7f,
                new float[] {0.6f, 1f}, new Color[] {new Color(0, 0, 0, 0), new Color(0, 0, 0, 120)});
        g.setPaint(rg);
        g.fillRect(0, 0, W, H);
        g.setColor(new Color(theme.main.getRed(), theme.main.getGreen(), theme.main.getBlue(), 10));
        g.fillRect(0, 0, W, H);
    }

    private void paintMonkey(Graphics2D g, int W, int H) {
        double s = 64, x = W - s - 24, y = H - s - 20;
        boolean typing = System.currentTimeMillis() - lastInput < 180;
        double hand = typing ? Math.sin(fxPhase * 8) * 6 : 0;
        g.setColor(new Color(0x7a4b25));
        g.fill(new Ellipse2D.Double(x - 4, y + 6, 18, 18));
        g.fill(new Ellipse2D.Double(x + s - 14, y + 6, 18, 18));
        g.fill(new Ellipse2D.Double(x, y, s, s * 0.9));
        g.setColor(new Color(0xe8c39e));
        g.fill(new Ellipse2D.Double(x + s * 0.18, y + s * 0.36, s * 0.64, s * 0.48));
        g.setColor(Color.BLACK);
        g.fill(new Ellipse2D.Double(x + s * 0.3, y + s * 0.28, 7, typing ? 4 : 7));
        g.fill(new Ellipse2D.Double(x + s * 0.6, y + s * 0.28, 7, typing ? 4 : 7));
        g.setStroke(new BasicStroke(2f));
        g.draw(new Arc2D.Double(x + s * 0.36, y + s * 0.52, s * 0.28, 12, 200, 140, Arc2D.OPEN));
        g.setColor(new Color(0x7a4b25));
        g.fill(new Ellipse2D.Double(x + 4, y + s * 0.85 + hand, 18, 14));
        g.fill(new Ellipse2D.Double(x + s - 22, y + s * 0.85 - hand, 18, 14));
    }
}
