package com.typejunkey.engine;

import com.typejunkey.data.Settings;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/** Per-letter accuracy and speed, used by the weakspot funbox. Saved in ~/.typejunkey/letters.properties. */
public final class LetterStats {
    private LetterStats() {}

    private static final Path FILE = Settings.DIR.resolve("letters.properties");
    private static final Map<Character, double[]> DATA = new HashMap<>(); // {correct, wrong, totalMs}
    private static boolean loaded, dirty;

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) { p.load(in); } catch (IOException e) { return; }
        for (String k : p.stringPropertyNames()) {
            try {
                String[] v = p.getProperty(k).split(";");
                DATA.put((char) Integer.parseInt(k), new double[] {Double.parseDouble(v[0]), Double.parseDouble(v[1]), Double.parseDouble(v[2])});
            } catch (RuntimeException ignored) { }
        }
    }

    public static synchronized void add(char expected, boolean correct, long ms) {
        load();
        double[] d = DATA.computeIfAbsent(Character.toLowerCase(expected), k -> new double[3]);
        d[correct ? 0 : 1]++;
        if (correct) d[2] += Math.min(ms, 3000);
        dirty = true;
    }

    /** Higher means more in need of practice (mistakes and slowness). */
    public static synchronized double weakness(char c) {
        load();
        double[] d = DATA.get(Character.toLowerCase(c));
        if (d == null || d[0] + d[1] < 5) return 1.0;
        double errRate = d[1] / (d[0] + d[1]);
        double avg = d[0] > 0 ? d[2] / d[0] : 600;
        return 1.0 + errRate * 12 + Math.max(0, avg - 250) / 250.0;
    }

    public static synchronized void save() {
        if (!dirty) return;
        dirty = false;
        Properties p = new Properties();
        for (Map.Entry<Character, double[]> e : DATA.entrySet()) {
            double[] d = e.getValue();
            p.setProperty("" + (int) e.getKey(), Math.round(d[0]) + ";" + Math.round(d[1]) + ";" + Math.round(d[2]));
        }
        try {
            Files.createDirectories(Settings.DIR);
            try (OutputStream out = Files.newOutputStream(FILE)) { p.store(out, "TypeJunKey letter statistics"); }
        } catch (IOException ignored) { }
    }
}
