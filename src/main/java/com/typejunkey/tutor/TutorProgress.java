package com.typejunkey.tutor;

import com.typejunkey.data.Settings;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Saved results per lesson: best wpm, best accuracy, and how often it was completed. */
public final class TutorProgress {
    private TutorProgress() {}

    public static final class Record {
        public final double wpm, acc;
        public final int times;
        Record(double wpm, double acc, int times) { this.wpm = wpm; this.acc = acc; this.times = times; }
    }

    private static final Path FILE = Settings.DIR.resolve("tutor-progress.properties");
    private static Properties props;

    private static synchronized Properties props() {
        if (props == null) {
            props = new Properties();
            try (InputStream in = Files.newInputStream(FILE)) { props.load(in); } catch (IOException ignored) { }
        }
        return props;
    }

    public static synchronized Record get(String id) {
        String v = props().getProperty(id);
        if (v == null) return null;
        try {
            String[] p = v.split(";");
            return new Record(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Integer.parseInt(p[2]));
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static synchronized void record(String id, double wpm, double acc) {
        Record old = get(id);
        double bw = old == null ? wpm : Math.max(old.wpm, wpm), ba = old == null ? acc : Math.max(old.acc, acc);
        props().setProperty(id, Math.round(bw * 10) / 10.0 + ";" + Math.round(ba * 10) / 10.0 + ";" + ((old == null ? 0 : old.times) + 1));
        try {
            Files.createDirectories(Settings.DIR);
            try (OutputStream out = Files.newOutputStream(FILE)) { props().store(out, "TypeJunKey tutor progress"); }
        } catch (IOException ignored) { }
    }

    public static boolean completed(String id) { return get(id) != null; }
}
