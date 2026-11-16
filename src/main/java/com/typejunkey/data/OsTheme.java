package com.typejunkey.data;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/** Detects whether the operating system is using a dark theme (best effort, cached for a few seconds). */
public final class OsTheme {
    private OsTheme() {}

    private static boolean dark;
    private static long checkedAt;

    public static synchronized boolean isDark() {
        long now = System.currentTimeMillis();
        if (now - checkedAt < 5000) return dark;
        checkedAt = now;
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("win")) {
                String out = run("reg", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", "/v", "AppsUseLightTheme");
                dark = out.contains("0x0");
            } else if (os.contains("mac")) {
                dark = run("defaults", "read", "-g", "AppleInterfaceStyle").toLowerCase().contains("dark");
            } else {
                String o = run("gsettings", "get", "org.gnome.desktop.interface", "color-scheme").toLowerCase();
                dark = o.contains("dark");
            }
        } catch (Exception e) {
            dark = false;
        }
        return dark;
    }

    private static String run(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        StringBuilder b = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) b.append(line).append('\n');
        }
        p.waitFor(2, TimeUnit.SECONDS);
        return b.toString();
    }
}
