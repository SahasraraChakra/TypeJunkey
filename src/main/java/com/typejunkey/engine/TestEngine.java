package com.typejunkey.engine;

import com.typejunkey.data.Content;
import com.typejunkey.data.History;
import com.typejunkey.data.Settings;
import com.typejunkey.model.Language;
import com.typejunkey.model.Quote;
import com.typejunkey.model.QuoteSet;
import com.typejunkey.model.TestResult;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Typing-test state machine: word generation (with funbox), input rules (difficulty, stop/delete on error,
 * confidence, freedom, strict space ...), pace caret speed and Monkeytype-style statistics.
 * No Swing code in here so it can be tested headless.
 */
public final class TestEngine {
    public enum Outcome { IGNORED, CORRECT, WRONG, COMMIT }

    private final Settings cfg;
    private final Random rnd = new Random();

    public final List<String> targets = new ArrayList<>();
    private final List<StringBuilder> typed = new ArrayList<>();
    private final List<Long> wordStart = new ArrayList<>();
    private final List<Double> bursts = new ArrayList<>();
    private List<String> initial;
    private Language lang;
    private Quote forcedQuote, lastQuote;
    private boolean keepQuote;

    public int cur;
    public String mode = "time";
    public int timeLimit = 30;
    public String quoteSource;

    private boolean started, finished, failed;
    private String failReason = "";
    private long startNs, endNs, lastKeyNs;
    private int kCorrect, kIncorrect, secChars, secErr, secIndex;
    private final List<Double> wpmSeries = new ArrayList<>(), rawSeries = new ArrayList<>();
    private final List<Integer> errSeries = new ArrayList<>();
    private TestResult result;
    private String rawPrev = "";
    private boolean capNext, lastWasNumber;
    private double lastRunWpm;
    private Runnable onWordsChanged = () -> { };
    private boolean fetchFailed;
    private int warnedAt = -1;

    // snapshot of the configuration the running test was created with
    private String tLang, tParam, difficulty, stopOnError, deleteOnError, confidence, minWpm, minAcc, minBurst;
    private boolean tPunct, tNum, freedom, strictSpace, quickEnd, lazy, nospace, british;
    private int minWpmVal, minAccVal, minBurstVal;
    private Funbox funbox;
    private List<String> fluid = new ArrayList<>();
    private double paceWpm;

    public TestEngine(Settings cfg) { this.cfg = cfg; funbox = new Funbox(cfg); }

    // ------------------------------------------------------------------ setup

    public void setOnWordsChanged(Runnable r) { onWordsChanged = r == null ? () -> { } : r; }
    public void forceQuote(Quote q) { forcedQuote = q; }

    public void reset(boolean repeat) {
        mode = cfg.mode; timeLimit = cfg.time;
        tLang = cfg.language; tParam = cfg.param(); tPunct = cfg.punctuation; tNum = cfg.numbers;
        difficulty = cfg.str("difficulty"); stopOnError = cfg.str("stopOnError"); deleteOnError = cfg.str("deleteOnError");
        confidence = cfg.str("confidenceMode"); freedom = cfg.bool("freedomMode"); strictSpace = cfg.bool("strictSpace");
        quickEnd = cfg.bool("quickEnd"); lazy = cfg.bool("lazyMode"); british = cfg.bool("britishEnglish");
        minWpm = cfg.str("minWpm"); minWpmVal = cfg.num("minWpmCustomSpeed");
        minAcc = cfg.str("minAcc"); minAccVal = cfg.num("minAccCustom");
        minBurst = cfg.str("minBurst"); minBurstVal = cfg.num("minBurstCustomSpeed");
        funbox = new Funbox(cfg);
        nospace = funbox.nospace();
        fluid = new ArrayList<>();
        if (funbox.on("layoutfluid")) for (String s : cfg.str("customLayoutfluid").split("#")) if (!s.isBlank()) fluid.add(s.trim());
        if (fluid.isEmpty()) fluid.add("qwerty");
        if (funbox.on("layoutfluid") && !mode.equals("time") && !mode.equals("words")) fluid = List.of(fluid.get(0));

        if (keepQuote && lastQuote != null && mode.equals("quote")) { repeat = true; forcedQuote = null; }
        keepQuote = false;
        started = finished = failed = false; failReason = ""; cur = 0; kCorrect = kIncorrect = 0; secChars = secErr = secIndex = 0;
        wpmSeries.clear(); rawSeries.clear(); errSeries.clear(); bursts.clear(); result = null; quoteSource = null; fetchFailed = false;
        warnedAt = -1;
        typed.clear(); wordStart.clear();
        targets.clear();
        paceWpm = computePace(repeat);
        if (repeat && initial != null) {
            targets.addAll(initial);
            if (mode.equals("quote") && lastQuote != null) quoteSource = lastQuote.source;
        } else {
            lang = Content.language(cfg.language);
            generate();
            initial = new ArrayList<>(targets);
        }
        ensureTyped();
        String kind = funbox.fetchKind();
        if (kind != null && !repeat && !mode.equals("zen") && !mode.equals("quote") && !mode.equals("custom")) startFetch(kind);
    }

    private void startFetch(String kind) {
        Funbox.fetch(kind).thenAccept(text -> {
            if (started) return;
            if (text == null || text.isBlank()) { fetchFailed = true; onWordsChanged.run(); return; }
            List<String> words = new ArrayList<>();
            String[] parts = text.trim().split("\\s+");
            for (int i = 0; i < parts.length && i < 90; i++) words.add(parts[i]);
            synchronized (targets) {
                if (started) return;
                targets.clear();
                for (int i = 0; i < words.size(); i++) targets.add(funbox.alter(words.get(i), rnd, i == words.size() - 1));
                initial = new ArrayList<>(targets);
                typed.clear(); ensureTyped();
            }
            onWordsChanged.run();
        });
    }

    public boolean fetchFailed() { return fetchFailed; }

    private void generate() {
        rawPrev = ""; capNext = true; lastWasNumber = false;
        switch (mode) {
            case "words":
                addWords(cfg.words);
                if (cfg.punctuation && !funbox.ignoresPunctuation() && !targets.isEmpty()) {
                    int last = targets.size() - 1;
                    String w = targets.get(last);
                    if (!w.isEmpty() && Character.isLetterOrDigit(w.charAt(w.length() - 1))) targets.set(last, w + ".");
                }
                break;
            case "quote": {
                Quote q = forcedQuote;
                if (q == null) {
                    QuoteSet quoteSet = Content.quotes(cfg.language);
                    List<Quote> pool = new ArrayList<>();
                    if (cfg.quoteLen >= 0 && cfg.quoteLen < quoteSet.groups.length) {
                        int lo = quoteSet.groups[cfg.quoteLen][0], hi = quoteSet.groups[cfg.quoteLen][1];
                        for (Quote x : quoteSet.quotes) if (x.length >= lo && x.length <= hi) pool.add(x);
                    }
                    if (pool.isEmpty()) pool = quoteSet.quotes;
                    q = pool.get(rnd.nextInt(pool.size()));
                } else {
                    forcedQuote = null;
                }
                lastQuote = q;
                quoteSource = q.source;
                splitInto(q.text);
                break;
            }
            case "custom":
                splitInto(cfg.customText);
                if (targets.isEmpty()) addWords(25);
                break;
            case "zen":
                targets.add("");
                break;
            default:
                addWords(70);
        }
    }

    private void splitInto(String text) {
        List<String> ws = new ArrayList<>();
        for (String w : text.trim().split("\\s+")) if (!w.isEmpty()) ws.add(w);
        if (funbox.on("backwards")) java.util.Collections.reverse(ws);
        for (int i = 0; i < ws.size(); i++) {
            String w = ws.get(i);
            if (british && tLang.startsWith("english")) w = BritishEnglish.apply(w);
            targets.add(funbox.alter(w, rnd, i == ws.size() - 1));
        }
    }

    private void addWords(int n) {
        String gen = funbox.fetchKind() != null ? null : funbox.generatorName();
        for (int i = 0; i < n; i++) {
            String w;
            if (gen != null) {
                w = funbox.generate(gen, rnd, lang);
            } else {
                String[] ws = lang.words;
                int guard = 0;
                do { w = ws[rnd.nextInt(ws.length)]; } while (ws.length > 1 && w.equals(rawPrev) && guard++ < 6);
                rawPrev = w;
                if (british && tLang.startsWith("english")) w = BritishEnglish.apply(w);
            }
            boolean isNumber = false;
            boolean plain = gen == null || !funbox.ignoresPunctuation();
            if (plain && cfg.numbers && !lastWasNumber && rnd.nextDouble() < 0.1) { w = randomNumber(); isNumber = true; }
            lastWasNumber = isNumber;
            if (plain && cfg.punctuation && !isNumber) w = punctuate(w);
            targets.add(funbox.alter(w, rnd, false));
        }
    }

    private String randomNumber() {
        int digits = 1 + rnd.nextInt(4);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < digits; i++) b.append((char) ('0' + (i == 0 && digits > 1 ? 1 + rnd.nextInt(9) : rnd.nextInt(10))));
        return b.toString();
    }

    private String punctuate(String w) {
        if (w.isEmpty()) return w;
        if (capNext) { w = Character.toUpperCase(w.charAt(0)) + w.substring(1); capNext = false; }
        double r = rnd.nextDouble();
        if (r < 0.07) return w + ",";
        if (r < 0.17) { capNext = true; return w + "."; }
        if (r < 0.19) { capNext = true; return w + "?"; }
        if (r < 0.20) { capNext = true; return w + "!"; }
        if (r < 0.22) return "\"" + w + "\"";
        if (r < 0.235) return "(" + w + ")";
        if (r < 0.25) return w + ";";
        if (r < 0.265) return w + ":";
        return w;
    }

    private void extend() {
        if (cur > targets.size() - 35) addWords(40);
    }

    // ------------------------------------------------------------------ pace caret

    private double computePace(boolean repeat) {
        String src = cfg.str("paceCaret");
        if (src.equals("off")) return 0;
        if (repeat && cfg.bool("repeatedPace") && lastRunWpm > 0) return lastRunWpm;
        String key = TestResult.key(cfg.mode, cfg.param(), cfg.language, cfg.punctuation, cfg.numbers);
        List<Double> same = new ArrayList<>();
        LocalDate today = LocalDate.now();
        List<Double> day = new ArrayList<>();
        for (TestResult r : History.all()) {
            if (!r.key().equals(key)) continue;
            same.add(r.wpm);
            if (LocalDate.ofInstant(java.time.Instant.ofEpochMilli(r.when), ZoneId.systemDefault()).equals(today)) day.add(r.wpm);
        }
        switch (src) {
            case "custom": return cfg.num("paceCaretCustomSpeed");
            case "pb": { double b = 0; for (double v : same) b = Math.max(b, v); return b; }
            case "last": return same.isEmpty() ? 0 : same.get(same.size() - 1);
            case "daily": return avg(day, day.size());
            case "average": return avg(same, 10);
            default: return 0;
        }
    }

    private static double avg(List<Double> l, int lastN) {
        if (l.isEmpty()) return 0;
        int from = Math.max(0, l.size() - lastN);
        double s = 0;
        for (int i = from; i < l.size(); i++) s += l.get(i);
        return s / (l.size() - from);
    }

    public double paceWpm() { return paceWpm; }

    /** Characters the pace caret has covered so far. */
    public int paceChars() {
        if (paceWpm <= 0 || !started) return 0;
        return (int) Math.floor(elapsed() * paceWpm * 5.0 / 60.0);
    }

    /** Maps a position in the character stream to {word index, offset in word}; null past the end. */
    public int[] locate(int chars) {
        int c = chars;
        synchronized (targets) {
            for (int i = 0; i < targets.size(); i++) {
                int len = targets.get(i).length() + (nospace ? 0 : 1);
                if (c < len) return new int[] {i, Math.min(c, targets.get(i).length())};
                c -= len;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ accessors

    public boolean isStarted() { return started; }
    public boolean isFinished() { return finished; }
    public boolean isFailed() { return failed; }
    public String failReason() { return failReason; }
    public TestResult result() { return result; }
    public String typedWord(int i) { return i < typed.size() ? typed.get(i).toString() : ""; }
    public int totalWords() { return targets.size(); }
    public Funbox funbox() { return funbox; }
    public boolean nospace() { return nospace; }
    /** The character the user has to type next: a letter, ' ' at the end of a word, or 0 when none. */
    public char nextChar() {
        if (finished || cur >= targets.size()) return 0;
        String t = targets.get(cur);
        int l = typed.size() > cur ? typed.get(cur).length() : 0;
        if (l < t.length()) return t.charAt(l);
        return nospace ? 0 : ' ';
    }

    public double lastBurst() { return bursts.isEmpty() ? 0 : bursts.get(bursts.size() - 1); }
    public double burstOf(int i) { return i >= 0 && i < bursts.size() ? bursts.get(i) : -1; }

    /** Name of the layout currently emulated (layoutfluid changes it during the test). */
    public String fluidLayout() {
        if (!funbox.on("layoutfluid") || fluid.isEmpty()) return null;
        int n = fluid.size(), idx;
        if (mode.equals("time")) idx = (int) Math.min(n - 1, Math.floor(elapsed() / (timeLimit / (double) n)));
        else idx = (int) Math.min(n - 1, Math.floor(cur * (double) n / Math.max(1, targets.size())));
        return fluid.get(Math.max(0, idx));
    }

    /** Whether the funbox rules allow a word to be seen (visual effects such as simon_says are in the pane). */
    public boolean wordVisible(int i) {
        int fv = funbox.futureVisible();
        if (fv >= 0 && i > cur + fv) return false;
        int ha = funbox.hideAhead();
        if (ha >= 0 && i >= cur && i <= cur + ha) return false;
        if (funbox.memory() && started && i >= cur) return false;
        return true;
    }

    private StringBuilder cw() { ensureTyped(); return typed.get(cur); }
    private void ensureTyped() { while (typed.size() <= cur) typed.add(new StringBuilder()); while (wordStart.size() <= cur) wordStart.add(0L); }

    public double elapsed() {
        if (!started) return 0;
        double e = ((finished ? endNs : System.nanoTime()) - startNs) / 1e9;
        return mode.equals("time") ? Math.min(e, timeLimit) : e;
    }

    public int timeLeft() {
        if (!mode.equals("time")) return 0;
        return (int) Math.max(0, Math.ceil(timeLimit - elapsed()));
    }

    // ------------------------------------------------------------------ input

    private void start() {
        if (!started) { started = true; startNs = lastKeyNs = System.nanoTime(); }
    }

    private void count(boolean ok, char expected) {
        if (ok) kCorrect++; else { kIncorrect++; secErr++; }
        secChars++;
        long now = System.nanoTime();
        if (expected != 0) LetterStats.add(expected, ok, (now - lastKeyNs) / 1_000_000);
        lastKeyNs = now;
    }

    private static char stripAccent(char c) {
        String n = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
        return n.isEmpty() ? c : n.charAt(0);
    }

    public Outcome typeChar(char c, boolean badShift) {
        if (finished) return Outcome.IGNORED;
        boolean zen = mode.equals("zen");
        StringBuilder w = cw();
        String t = targets.get(cur);
        if (!zen && w.length() >= t.length() + 20) return Outcome.IGNORED;
        start();
        if (w.length() == 0) wordStart.set(cur, System.nanoTime());
        char expected = !zen && w.length() < t.length() ? t.charAt(w.length()) : 0;
        if (lazy && expected != 0 && expected != c && stripAccent(expected) == c) c = expected;
        boolean ok = zen || (expected != 0 && expected == c && !badShift);
        if (!ok && !zen) {
            if (difficulty.equals("master")) { count(false, expected); fail("master: a mistake was made"); return Outcome.WRONG; }
            if (stopOnError.equals("letter")) { count(false, expected); return Outcome.WRONG; }
            if (deleteOnError.startsWith("letter")) {
                count(false, expected);
                if (deleteOnError.equals("letter_hard")) w.setLength(0);
                checkMinAcc();
                return Outcome.WRONG;
            }
        }
        w.append(c);
        if (zen) targets.set(cur, w.toString());
        count(ok, expected);
        if (!ok && checkMinAcc()) return Outcome.WRONG;
        if (!zen && nospace && w.length() >= t.length()) {
            commit(true);
            return ok ? Outcome.CORRECT : Outcome.WRONG;
        }
        if (!zen && !mode.equals("time") && cur == targets.size() - 1) {
            if (w.toString().equals(t) || (quickEnd && w.length() >= t.length())) finish();
        }
        return ok ? Outcome.CORRECT : Outcome.WRONG;
    }

    private boolean checkMinAcc() {
        if (minAcc.equals("custom") && kCorrect + kIncorrect >= 10 && liveAcc() < minAccVal) { fail("accuracy fell below " + minAccVal + "%"); return true; }
        return false;
    }

    public Outcome space() {
        if (finished) return Outcome.IGNORED;
        StringBuilder w = cw();
        boolean zen = mode.equals("zen");
        if (nospace && !zen) return Outcome.IGNORED;
        if (w.length() == 0) return Outcome.IGNORED;
        start();
        String t = targets.get(cur);
        boolean correct = zen || w.toString().equals(t);
        if (!zen && strictSpace && w.length() < t.length()) { count(false, ' '); return Outcome.WRONG; }
        if (!zen && !correct) {
            if (stopOnError.equals("word")) { count(false, ' '); return Outcome.WRONG; }
            if (difficulty.equals("expert")) { count(false, ' '); fail("expert: a word was submitted incorrectly"); return Outcome.WRONG; }
            if (deleteOnError.startsWith("word")) {
                count(false, ' ');
                w.setLength(0);
                return Outcome.WRONG;
            }
        }
        count(correct, ' ');
        commit(false);
        return correct ? Outcome.COMMIT : Outcome.WRONG;
    }

    private void commit(boolean auto) {
        boolean zen = mode.equals("zen");
        double sec = Math.max(0.05, (System.nanoTime() - Math.max(wordStart.get(cur), startNs)) / 1e9);
        double burst = (typed.get(cur).length() + (nospace ? 0 : 1)) / 5.0 * 60.0 / sec;
        bursts.add(burst);
        if (!zen && minBurst.equals("fixed") && burst < minBurstVal) { fail("a word was slower than " + minBurstVal + " wpm"); cur++; return; }
        if (!zen && minBurst.equals("flex") && started && elapsed() > 2) {
            double limit = Math.min(minBurstVal, liveWpm() * 0.75);
            if (burst < limit) { fail("a word was much slower than your average"); cur++; return; }
        }
        cur++;
        if (zen) targets.add("");
        else if (cur >= targets.size()) { finish(); return; }
        ensureTyped();
        if (mode.equals("time")) extend();
    }

    public void backspace(boolean wholeWord) {
        if (finished) return;
        if (confidence.equals("max")) return;
        boolean zen = mode.equals("zen");
        StringBuilder w = cw();
        if (w.length() == 0) {
            if (cur == 0 || confidence.equals("on")) return;
            boolean canBack = zen || freedom || !typedWord(cur - 1).equals(targets.get(cur - 1));
            if (!canBack) return;
            if (zen) targets.remove(cur);
            cur--;
            if (!bursts.isEmpty() && bursts.size() > cur) bursts.remove(bursts.size() - 1);
            if (wholeWord) { cw().setLength(0); if (zen) targets.set(cur, ""); }
            return;
        }
        if (wholeWord) w.setLength(0); else w.deleteCharAt(w.length() - 1);
        if (zen) targets.set(cur, w.toString());
    }

    /** Zen mode ends manually (Shift+Enter). */
    public void finishZen() { if (started) finish(); }

    /** Ends the test now as failed (for example when Esc is pressed in a test that cannot restart this way). */
    public void fail(String reason) {
        if (finished) return;
        failed = true;
        failReason = reason;
        finish();
    }

    // ------------------------------------------------------------------ stats

    private static final class Counts { int wordChars, spaces, correct, incorrect, extra, missed, allTyped; }

    private Counts counts(boolean fin) {
        Counts c = new Counts();
        int last = Math.min(typed.size() - 1, cur);
        for (int i = 0; i <= last && i < targets.size(); i++) {
            String t = targets.get(i), w = typed.get(i).toString();
            boolean committed = i < cur;
            if (!committed && w.isEmpty()) continue;
            boolean ok = w.equals(t);
            int sp = nospace ? 0 : 1;
            if (committed) {
                c.allTyped += w.length() + sp;
                if (ok) { c.wordChars += t.length(); c.spaces += sp; }
                if (w.length() < t.length()) c.missed += t.length() - w.length();
            } else {
                c.allTyped += w.length();
                if (ok || (!fin && t.startsWith(w))) c.wordChars += w.length();
            }
            int n = Math.min(w.length(), t.length());
            for (int k = 0; k < n; k++) { if (w.charAt(k) == t.charAt(k)) c.correct++; else c.incorrect++; }
            if (w.length() > t.length()) c.extra += w.length() - t.length();
        }
        return c;
    }

    public double liveWpm() {
        if (!started) return 0;
        Counts c = counts(false);
        return (c.wordChars + c.spaces) / 5.0 * 60.0 / Math.max(elapsed(), 0.5);
    }

    public double liveRaw() {
        if (!started) return 0;
        Counts c = counts(false);
        return c.allTyped / 5.0 * 60.0 / Math.max(elapsed(), 0.5);
    }

    public double liveAcc() {
        int total = kCorrect + kIncorrect;
        return total == 0 ? 100 : 100.0 * kCorrect / total;
    }

    /** Called by the UI timer. Returns true if the test just ended (finished or failed). */
    public boolean tick() {
        if (!started || finished) return false;
        double e = (System.nanoTime() - startNs) / 1e9;
        while (secIndex + 1 <= Math.floor(e) && secIndex < (mode.equals("time") ? timeLimit : 100000)) sample();
        if (minWpm.equals("custom") && e > 3 && liveWpm() < minWpmVal) { fail("speed fell below " + minWpmVal + " wpm"); return true; }
        if (mode.equals("time") && e >= timeLimit) { finish(); return true; }
        return false;
    }

    /** Seconds left at which a time warning sound should play, once. */
    public boolean timeWarningDue(int seconds) {
        if (!mode.equals("time") || !started || finished || seconds <= 0) return false;
        int left = timeLeft();
        if (left == seconds && warnedAt != left) { warnedAt = left; return true; }
        return false;
    }

    private void sample() {
        secIndex++;
        Counts c = counts(false);
        wpmSeries.add((c.wordChars + c.spaces) / 5.0 * 60.0 / secIndex);
        rawSeries.add(secChars * 12.0);
        errSeries.add(secErr);
        secChars = 0; secErr = 0;
    }

    private void finish() {
        if (finished) return;
        finished = true;
        endNs = System.nanoTime();
        double e = (endNs - startNs) / 1e9;
        if (mode.equals("time") && !failed) e = timeLimit;
        if (mode.equals("zen") && !targets.isEmpty() && targets.get(targets.size() - 1).isEmpty() && cur == targets.size() - 1) {
            targets.remove(targets.size() - 1);
        }
        double secs = Math.max(e, 0.01);
        Counts c = counts(true);
        double wpm = (c.wordChars + c.spaces) / 5.0 * 60.0 / secs;
        double raw = c.allTyped / 5.0 * 60.0 / secs;
        if (e - secIndex >= 0.5) {
            wpmSeries.add(wpm);
            rawSeries.add(secChars * 12.0 / Math.max(e - secIndex, 0.5));
            errSeries.add(secErr);
        }
        TestResult r = new TestResult();
        r.when = System.currentTimeMillis();
        r.mode = mode; r.param = tParam; r.language = tLang; r.punctuation = tPunct; r.numbers = tNum;
        r.wpm = round2(wpm); r.raw = round2(raw);
        int total = kCorrect + kIncorrect;
        r.acc = round2(total == 0 ? 0 : 100.0 * kCorrect / total);
        r.consistency = round2(consistency(rawSeries));
        r.correct = c.correct; r.incorrect = c.incorrect; r.extra = c.extra; r.missed = c.missed;
        r.seconds = round2(e);
        r.wpmSeries = wpmSeries.stream().mapToDouble(Double::doubleValue).toArray();
        r.rawSeries = rawSeries.stream().mapToDouble(Double::doubleValue).toArray();
        r.errSeries = errSeries.stream().mapToInt(Integer::intValue).toArray();
        r.failed = failed; r.failReason = failReason;
        r.funbox = String.join(", ", funbox.names());
        r.paceWpm = paceWpm;
        r.bursts = bursts.stream().mapToDouble(Double::doubleValue).toArray();
        int upto = Math.min(cur, targets.size() - 1);
        for (int i = 0; i <= upto && i < typed.size(); i++) {
            String w = typed.get(i).toString();
            if (!w.isEmpty() || i < cur) r.words.add(new String[] {targets.get(i), w});
        }
        result = r;
        if (!failed) lastRunWpm = r.wpm;
        LetterStats.save();
        if (mode.equals("quote") && cfg.str("repeatQuotes").equals("typing") && (failed || r.acc < 100)) keepQuote = true;
    }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }

    /** Monkeytype's consistency: 100 * (1 - tanh(cov + cov^3/3 + cov^5/5)). */
    static double consistency(List<Double> values) {
        if (values.size() < 2) return 100;
        double mean = 0;
        for (double v : values) mean += v;
        mean /= values.size();
        if (mean <= 0) return 0;
        double var = 0;
        for (double v : values) var += (v - mean) * (v - mean);
        double cov = Math.sqrt(var / values.size()) / mean;
        double x = cov + Math.pow(cov, 3) / 3 + Math.pow(cov, 5) / 5;
        return 100 * (1 - Math.tanh(x));
    }
}
