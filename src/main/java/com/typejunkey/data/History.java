package com.typejunkey.data;

import com.typejunkey.model.TestResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/** Local result history, stored as tab-separated lines in ~/.typejunkey/history.tsv. */
public final class History {
    private History() {}

    private static final Path FILE = Settings.DIR.resolve("history.tsv");
    private static List<TestResult> cache;

    public static synchronized List<TestResult> all() {
        if (cache == null) {
            cache = new ArrayList<>();
            try {
                for (String line : Files.readAllLines(FILE, StandardCharsets.UTF_8)) {
                    TestResult r = TestResult.fromLine(line);
                    if (r != null) cache.add(r);
                }
            } catch (IOException ignored) { }
        }
        return cache;
    }

    public static synchronized void add(TestResult r) {
        all().add(r);
        try {
            Files.createDirectories(Settings.DIR);
            Files.writeString(FILE, r.toLine() + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) { }
    }

    public static synchronized void clear() {
        all().clear();
        try { Files.deleteIfExists(FILE); } catch (IOException ignored) { }
    }

    /** Best wpm recorded for a test configuration key, or 0. */
    public static synchronized double best(String key) {
        double b = 0;
        for (TestResult r : all()) if (r.key().equals(key) && r.wpm > b) b = r.wpm;
        return b;
    }
}
