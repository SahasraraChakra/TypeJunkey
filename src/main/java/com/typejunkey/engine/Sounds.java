package com.typejunkey.engine;

import com.typejunkey.data.Settings;
import com.typejunkey.util.Resources;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Click, error and time-warning sounds. A missing sound or an unavailable audio device is silently ignored. */
public final class Sounds {
    private Sounds() {}

    private static final Map<String, List<Clip[]>> CACHE = new HashMap<>();
    private static final Map<String, Integer> NEXT = new HashMap<>();
    private static final Random RND = new Random();
    private static boolean broken;

    private static byte[] bytes(String rel) {
        byte[] b = Resources.readFile(Resources.side("sounds").resolve(rel));
        return b != null ? b : Resources.readBundled("sounds/" + rel);
    }

    private static synchronized List<Clip[]> load(String folder) {
        List<Clip[]> list = CACHE.get(folder);
        if (list != null) return list;
        list = new ArrayList<>();
        for (int v = 1; v <= 4; v++) {
            byte[] b = bytes(folder + "/" + v + ".wav");
            if (b == null) continue;
            Clip[] pool = new Clip[3];
            try {
                for (int i = 0; i < pool.length; i++) {
                    AudioInputStream in = AudioSystem.getAudioInputStream(new ByteArrayInputStream(b));
                    Clip c = AudioSystem.getClip();
                    c.open(in);
                    pool[i] = c;
                }
                list.add(pool);
            } catch (Exception | Error e) {
                broken = true;
                break;
            }
        }
        CACHE.put(folder, list);
        return list;
    }

    private static void play(List<Clip[]> variants, String keyName) {
        if (variants.isEmpty()) return;
        Clip[] pool = variants.get(RND.nextInt(variants.size()));
        int i = NEXT.merge(keyName, 1, Integer::sum) % pool.length;
        Clip c = pool[i];
        if (c == null) return;
        try {
            if (c.isRunning()) c.stop();
            c.setFramePosition(0);
            if (c.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl g = (FloatControl) c.getControl(FloatControl.Type.MASTER_GAIN);
                double vol = Math.max(0.0001, Settings.get().num("soundVolume") / 100.0);
                g.setValue((float) Math.max(g.getMinimum(), Math.min(g.getMaximum(), 20 * Math.log10(vol))));
            }
            c.start();
        } catch (RuntimeException ignored) { }
    }

    public static void click() {
        String n = Settings.get().str("playSoundOnClick");
        if (n.equals("off") || broken) return;
        play(load("click" + n), "click" + n);
    }

    public static void error() {
        String n = Settings.get().str("playSoundOnError");
        if (n.equals("off") || broken) return;
        play(load("error" + n), "error" + n);
    }

    public static void timeWarning() {
        if (Settings.get().str("playTimeWarning").equals("off") || broken) return;
        byte[] b = Resources.readFile(Resources.side("sounds").resolve("timeWarning.wav"));
        if (b == null) b = Resources.readBundled("sounds/timeWarning.wav");
        if (b == null) return;
        try {
            AudioInputStream in = AudioSystem.getAudioInputStream(new ByteArrayInputStream(b));
            Clip c = AudioSystem.getClip();
            c.open(in);
            if (c.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl g = (FloatControl) c.getControl(FloatControl.Type.MASTER_GAIN);
                double vol = Math.max(0.0001, Settings.get().num("soundVolume") / 100.0);
                g.setValue((float) Math.max(g.getMinimum(), Math.min(g.getMaximum(), 20 * Math.log10(vol))));
            }
            c.start();
        } catch (Exception | Error e) {
            broken = true;
        }
    }
}
