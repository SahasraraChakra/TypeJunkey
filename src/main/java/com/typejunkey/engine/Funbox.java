package com.typejunkey.engine;

import com.typejunkey.data.Content;
import com.typejunkey.data.Settings;
import com.typejunkey.model.Language;
import com.typejunkey.util.Json;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Monkeytype's funbox modifiers. This class holds the catalogue and the word-level behaviour: word
 * generators, text transforms and visibility rules. Visual effects are drawn by the typing pane.
 */
public final class Funbox {
    public static final class Info {
        public final String name, description;
        Info(String name, String description) { this.name = name; this.description = description; }
    }

    public static final List<Info> ALL = List.of(
            new Info("58008", "A special mode for accountants."), new Info("mirror", "Everything is mirrored!"),
            new Info("upside_down", "Everything is upside down!"), new Info("nausea", "I think I'm gonna be sick."),
            new Info("round_round_baby", "...right round, like a record baby."), new Info("simon_says", "Type what simon says."),
            new Info("tts", "Listen closely. (speaks each word; needs a speech engine)"), new Info("choo_choo", "All the letters are spinning!"),
            new Info("arrows", "Play it on a pad! Use the arrow keys."), new Info("rAnDoMcAsE", "raNdomIze ThE CApitaLizatIon Of EveRY LeTtEr."),
            new Info("sPoNgEcAsE", "I kInDa LiKe HoW iNeFfIcIeNt QwErTy Is."), new Info("capitals", "Capitalize Every Word."),
            new Info("layout_mirror", "Mirror the keyboard layout."), new Info("layoutfluid", "Switch between the layouts set in settings during the test."),
            new Info("earthquake", "Everybody get down! The words are shaking!"), new Info("space_balls", "In a galaxy far far away."),
            new Info("gibberish", "Anvbuefl dizzs eoos alsb?"), new Info("ascii", "Where was the ampersand again? Only ASCII characters."),
            new Info("specials", "!@#$%^&*. Only special characters."), new Info("plus_zero", "React quickly! Only the current word is visible."),
            new Info("plus_one", "Only one future word is visible."), new Info("plus_two", "Only two future words are visible."),
            new Info("plus_three", "Only three future words are visible."), new Info("read_ahead_easy", "Only the current word is invisible."),
            new Info("read_ahead", "Current and the next word are invisible!"), new Info("read_ahead_hard", "Current and the next two words are invisible!"),
            new Info("memory", "Test your memory. Remember the words and type them blind."), new Info("nospace", "Whoneedsspacesanyway?"),
            new Info("poetry", "Practice typing some beautiful prose. (needs internet)"), new Info("wikipedia", "Practice typing wikipedia sections. (needs internet)"),
            new Info("weakspot", "Focus on slow and mistyped letters."), new Info("pseudolang", "Nonsense words that look like the current language."),
            new Info("IPv4", "For sysadmins."), new Info("IPv6", "For sysadmins with a long beard."),
            new Info("binary", "01000010 01100101 01100101 01110000"), new Info("hexadecimal", "0x38 0x20 0x74 0x69 0x6D 0x65 0x73"),
            new Info("zipf", "Words are generated according to Zipf's law."), new Info("morse", "-.../././.--./ -.../---/---/.--./"),
            new Info("crt", "Go back to the 1980s."), new Info("backwards", "...sdrawkcab epyt ot yrt woN"),
            new Info("ddoouubblleedd", "TTyyppee eevveerryytthhiinngg ttwwiiccee.."), new Info("instant_messaging", "Who needs shift anyway?"),
            new Info("underscore_spaces", "Underscores_are_better."), new Info("ALL_CAPS", "WHY ARE WE SHOUTING?"),
            new Info("polyglot", "Use words from multiple languages in a single test."), new Info("asl", "Practice american sign language. (fingerspelling font not included)"),
            new Info("rot13", "Vg znl abg or frpher, ohg vg vf sha gb glcr!"), new Info("no_quit", "You can't restart the test."));

    private static final String[] GENERATORS = {"poetry", "wikipedia", "58008", "arrows", "gibberish", "ascii", "specials", "IPv4", "IPv6",
            "binary", "hexadecimal", "weakspot", "pseudolang", "polyglot", "zipf"};
    private static final Map<String, String> MORSE = new HashMap<>();
    static {
        String[] letters = {"a", ".-", "b", "-...", "c", "-.-.", "d", "-..", "e", ".", "f", "..-.", "g", "--.", "h", "....", "i", "..", "j", ".---",
                "k", "-.-", "l", ".-..", "m", "--", "n", "-.", "o", "---", "p", ".--.", "q", "--.-", "r", ".-.", "s", "...", "t", "-", "u", "..-",
                "v", "...-", "w", ".--", "x", "-..-", "y", "-.--", "z", "--..", "0", "-----", "1", ".----", "2", "..---", "3", "...--", "4", "....-",
                "5", ".....", "6", "-....", "7", "--...", "8", "---..", "9", "----.", ".", ".-.-.-", ",", "--..--", "?", "..--..", "'", ".----.",
                "!", "-.-.--", "/", "-..-.", "(", "-.--.", ")", "-.--.-", "&", ".-...", ":", "---...", ";", "-.-.-.", "=", "-...-", "+", ".-.-.",
                "-", "-....-", "_", "..--.-", "\"", ".-..-.", "$", "...-..-", "@", ".--.-."};
        for (int i = 0; i + 1 < letters.length; i += 2) MORSE.put(letters[i], letters[i + 1]);
    }

    private final Set<String> active = new LinkedHashSet<>();

    public Funbox(Settings cfg) {
        Set<String> valid = new LinkedHashSet<>();
        for (Info i : ALL) valid.add(i.name);
        for (String n : cfg.list("funbox")) if (valid.contains(n)) active.add(n);
    }

    public boolean on(String name) { return active.contains(name); }
    public Set<String> names() { return active; }
    public boolean any() { return !active.isEmpty(); }

    // ------------------------------------------------------------------ rules
    public boolean nospace() { return on("nospace") || on("arrows") || on("morse") || on("underscore_spaces"); }
    public boolean reverseDirection() { return on("backwards"); }
    public boolean noQuit() { return on("no_quit"); }
    public boolean arrows() { return on("arrows"); }

    /** Number of future words that stay visible, or -1 for no limit. */
    public int futureVisible() {
        if (on("plus_zero")) return 0;
        if (on("plus_one")) return 1;
        if (on("plus_two")) return 2;
        if (on("plus_three")) return 3;
        return -1;
    }

    /** How many words after the current one are hidden too (current is always hidden), or -1 for none. */
    public int hideAhead() {
        if (on("read_ahead_hard")) return 2;
        if (on("read_ahead")) return 1;
        if (on("read_ahead_easy")) return 0;
        return -1;
    }

    public boolean memory() { return on("memory"); }
    public boolean hasGenerator() { return generatorName() != null; }
    public boolean ignoresPunctuation() { return hasGenerator() && !on("weakspot") && !on("pseudolang") && !on("polyglot") && !on("zipf"); }

    public String generatorName() {
        for (String g : GENERATORS) if (on(g)) return g;
        return null;
    }

    public String fetchKind() {
        if (on("poetry")) return "poetry";
        if (on("wikipedia")) return "wikipedia";
        return null;
    }

    // ------------------------------------------------------------------ word generation
    private static final Map<String, Map<String, List<Character>>> MARKOV = new HashMap<>();

    public String generate(String gen, Random r, Language lang) {
        switch (gen) {
            case "58008": {
                String digits = "01234578";
                int n = 3 + r.nextInt(5);
                StringBuilder b = new StringBuilder();
                for (int i = 0; i < n; i++) b.append(digits.charAt(r.nextInt(digits.length())));
                return b.toString();
            }
            case "arrows": {
                String a = "\u2190\u2191\u2193\u2192";
                int n = 4 + r.nextInt(4);
                StringBuilder b = new StringBuilder();
                for (int i = 0; i < n; i++) b.append(a.charAt(r.nextInt(4)));
                return b.toString();
            }
            case "gibberish": {
                String v = "aeiou", c = "bcdfghjklmnpqrstvwxyz";
                int n = 3 + r.nextInt(6);
                StringBuilder b = new StringBuilder();
                boolean vowel = r.nextBoolean();
                for (int i = 0; i < n; i++) {
                    String set = vowel ? v : c;
                    b.append(set.charAt(r.nextInt(set.length())));
                    vowel = r.nextInt(4) == 0 ? vowel : !vowel;
                }
                return b.toString();
            }
            case "ascii": return randomChars(r, 33, 126, 3 + r.nextInt(6));
            case "specials": {
                String sp = "!@#$%^&*()-_=+[]{};:'\",.<>/?\\|`~";
                int n = 3 + r.nextInt(6);
                StringBuilder b = new StringBuilder();
                for (int i = 0; i < n; i++) b.append(sp.charAt(r.nextInt(sp.length())));
                return b.toString();
            }
            case "IPv4": return r.nextInt(256) + "." + r.nextInt(256) + "." + r.nextInt(256) + "." + r.nextInt(256);
            case "IPv6": {
                StringBuilder b = new StringBuilder();
                for (int i = 0; i < 8; i++) { if (i > 0) b.append(':'); b.append(Integer.toHexString(r.nextInt(0x10000))); }
                return b.toString();
            }
            case "binary": {
                int c = 'a' + r.nextInt(26);
                return String.format("%8s", Integer.toBinaryString(c)).replace(' ', '0');
            }
            case "hexadecimal": return String.format("0x%02X", 33 + r.nextInt(94));
            case "weakspot": {
                String best = null;
                double bestScore = -1;
                for (int i = 0; i < 24; i++) {
                    String w = lang.words[r.nextInt(lang.words.length)];
                    double score = 0;
                    for (char ch : w.toCharArray()) score += LetterStats.weakness(ch);
                    score = score / Math.max(1, w.length()) * (0.7 + r.nextDouble() * 0.6);
                    if (score > bestScore) { bestScore = score; best = w; }
                }
                return best;
            }
            case "pseudolang": return pseudo(r, lang);
            case "polyglot": {
                List<String> langs = Settings.get().list("customPolyglot");
                if (langs.size() < 2) return lang.words[r.nextInt(lang.words.length)];
                Language l = Content.language(langs.get(r.nextInt(langs.size())));
                return l.words[r.nextInt(l.words.length)];
            }
            case "zipf": {
                int n = lang.words.length;
                int rank = (int) Math.min(n - 1, Math.floor(Math.exp(r.nextDouble() * Math.log(n))) - 1);
                return lang.words[Math.max(0, rank)];
            }
            default: return lang.words[r.nextInt(lang.words.length)];
        }
    }

    private static String randomChars(Random r, int lo, int hi, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append((char) (lo + r.nextInt(hi - lo + 1)));
        return b.toString();
    }

    private static synchronized String pseudo(Random r, Language lang) {
        Map<String, List<Character>> m = MARKOV.get(lang.name);
        if (m == null) {
            m = new HashMap<>();
            for (String w : lang.words) {
                String p = "^^" + w + "$";
                for (int i = 2; i < p.length(); i++) m.computeIfAbsent(p.substring(i - 2, i), k -> new ArrayList<>()).add(p.charAt(i));
            }
            MARKOV.put(lang.name, m);
        }
        StringBuilder b = new StringBuilder("^^");
        for (int guard = 0; guard < 24; guard++) {
            List<Character> next = m.get(b.substring(b.length() - 2));
            if (next == null) break;
            char c = next.get(r.nextInt(next.size()));
            if (c == '$') break;
            b.append(c);
        }
        String w = b.substring(2);
        if (w.length() < 2) return lang.words[r.nextInt(lang.words.length)];
        return w;
    }

    // ------------------------------------------------------------------ text transforms
    /** Applies the active text transforms to one word. */
    public String alter(String w, Random r, boolean last) {
        if (w.isEmpty()) return w;
        if (on("backwards")) w = new StringBuilder(w).reverse().toString();
        if (on("rot13")) w = rot13(w);
        if (on("capitals")) w = Character.toUpperCase(w.charAt(0)) + w.substring(1);
        if (on("ALL_CAPS")) w = w.toUpperCase();
        if (on("instant_messaging")) w = w.toLowerCase().replaceAll("[.,!?;:\"]", "");
        if (on("rAnDoMcAsE")) {
            StringBuilder b = new StringBuilder();
            for (char c : w.toCharArray()) b.append(r.nextBoolean() ? Character.toUpperCase(c) : Character.toLowerCase(c));
            w = b.toString();
        }
        if (on("sPoNgEcAsE")) {
            StringBuilder b = new StringBuilder();
            boolean up = r.nextBoolean();
            for (char c : w.toCharArray()) { b.append(up ? Character.toUpperCase(c) : Character.toLowerCase(c)); if (Character.isLetter(c)) up = !up; }
            w = b.toString();
        }
        if (on("ddoouubblleedd")) {
            StringBuilder b = new StringBuilder();
            for (char c : w.toCharArray()) b.append(c).append(c);
            w = b.toString();
        }
        if (on("morse")) {
            StringBuilder b = new StringBuilder();
            for (char c : w.toLowerCase().toCharArray()) {
                String m = MORSE.get(String.valueOf(c));
                if (m != null) b.append(m).append('/');
            }
            if (b.length() > 0) w = b.toString();
        }
        if (on("underscore_spaces") && !last) w = w + "_";
        return w;
    }

    private static String rot13(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c >= 'a' && c <= 'z') b.append((char) ('a' + (c - 'a' + 13) % 26));
            else if (c >= 'A' && c <= 'Z') b.append((char) ('A' + (c - 'A' + 13) % 26));
            else b.append(c);
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ network text (poetry, wikipedia)
    /** Fetches a passage in the background; completes with null when offline. */
    public static CompletableFuture<String> fetch(String kind) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
        String url = kind.equals("wikipedia") ? "https://en.wikipedia.org/api/rest_v1/page/random/summary" : "https://poetrydb.org/random/1";
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).header("User-Agent", "TypeJunKey/0.2").GET().build();
        return client.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(resp -> {
            try {
                if (resp.statusCode() != 200) return null;
                Object o = Json.parse(resp.body());
                if (kind.equals("wikipedia")) {
                    Object ex = Json.obj(o).get("extract");
                    return ex == null ? null : String.valueOf(ex);
                }
                List<Object> poems = Json.arr(o);
                StringBuilder b = new StringBuilder();
                for (Object l : Json.arr(Json.obj(poems.get(0)).get("lines"))) b.append(l).append(' ');
                return b.toString();
            } catch (RuntimeException e) {
                return null;
            }
        }).exceptionally(e -> null);
    }
}
