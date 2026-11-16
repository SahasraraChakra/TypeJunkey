package com.typejunkey.tutor;

import com.typejunkey.data.Settings;
import com.typejunkey.util.Resources;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Tutorials grouped by level. Bundled lessons live in the jar (tutor/&lt;level&gt;/NN_title.txt); a tutor folder
 * (next to the jar, or any folder chosen in the tutor tab) adds lessons and overrides bundled ones with the
 * same file name. Removing bundled or folder lessons can never break the library.
 */
public final class TutorLibrary {
    private TutorLibrary() {}

    public static final List<String> CANONICAL = List.of("basic", "beginner", "intermediary", "advanced", "professional");
    private static Map<String, List<Lesson>> cache;

    public static Path folder() {
        String f = Settings.get().tutorFolder;
        return f == null || f.isBlank() ? Resources.side("tutor") : Path.of(f);
    }

    public static synchronized void reload() { cache = null; }

    private static synchronized Map<String, List<Lesson>> load() {
        if (cache != null) return cache;
        Map<String, Map<String, Lesson>> byLevel = new TreeMap<>();
        for (String e : Resources.bundledUnder("tutor")) add(byLevel, e, Resources.text(Resources.readBundled("tutor/" + e)), false);
        Path dir = folder();
        for (String e : Resources.folderUnder(dir)) add(byLevel, e, Resources.text(Resources.readFile(dir.resolve(e))), true);
        Map<String, List<Lesson>> out = new LinkedHashMap<>();
        List<String> order = new ArrayList<>(CANONICAL);
        TreeSet<String> extra = new TreeSet<>(byLevel.keySet());
        extra.removeAll(CANONICAL);
        order.addAll(extra);
        for (String level : order) {
            Map<String, Lesson> m = byLevel.get(level);
            if (m == null || m.isEmpty()) continue;
            List<Lesson> list = new ArrayList<>(m.values());
            list.sort(Comparator.comparingInt((Lesson l) -> l.number).thenComparing(l -> l.file));
            out.put(level, list);
        }
        cache = out;
        return out;
    }

    private static void add(Map<String, Map<String, Lesson>> byLevel, String entry, String text, boolean folder) {
        int slash = entry.indexOf('/');
        if (slash <= 0 || text == null || !entry.toLowerCase().endsWith(".txt")) return;
        String level = entry.substring(0, slash).toLowerCase();
        String file = entry.substring(slash + 1);
        if (file.contains("/")) return;
        try {
            Lesson l = Lesson.parse(level, file, text, folder);
            if (l != null) byLevel.computeIfAbsent(level, k -> new TreeMap<>()).put(file, l);
        } catch (RuntimeException ignored) { }
    }

    public static List<String> levels() { return new ArrayList<>(load().keySet()); }

    public static List<Lesson> lessons(String level) {
        List<Lesson> l = load().get(level);
        return l == null ? List.of() : l;
    }

    public static Lesson find(String id) {
        for (List<Lesson> l : load().values()) for (Lesson x : l) if (x.id().equals(id)) return x;
        return null;
    }
}
