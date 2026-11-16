package com.typejunkey.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Access to bundled resources (inside the jar / classes folder) and to the "side-by-side" folders that sit
 * next to the jar. Everything here is defensive: a missing or damaged resource yields an empty result, never
 * an exception, so users can delete bundled content freely.
 */
public final class Resources {
    private Resources() {}

    private static Path appDir;
    private static List<String> entries;

    /** Folder that holds the jar (or the working directory when running from an IDE). */
    public static synchronized Path appDir() {
        if (appDir == null) {
            String override = System.getProperty("typejunkey.home");
            if (override != null && !override.isBlank()) {
                appDir = Path.of(override);
            } else {
                try {
                    Path p = Path.of(Resources.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                    appDir = Files.isDirectory(p) ? Path.of(System.getProperty("user.dir")) : p.getParent();
                } catch (URISyntaxException | RuntimeException e) {
                    appDir = Path.of(System.getProperty("user.dir"));
                }
                if (appDir == null) appDir = Path.of(".");
            }
        }
        return appDir;
    }

    public static Path side(String sub) { return appDir().resolve(sub); }

    /** Every file path bundled with the application, using '/' separators. */
    public static synchronized List<String> bundledEntries() {
        if (entries == null) {
            List<String> out = new ArrayList<>();
            try {
                Path p = Path.of(Resources.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                if (Files.isDirectory(p)) {
                    try (Stream<Path> s = Files.walk(p)) {
                        s.filter(Files::isRegularFile).forEach(f -> out.add(p.relativize(f).toString().replace('\\', '/')));
                    }
                } else {
                    try (JarFile jf = new JarFile(p.toFile())) {
                        for (var en = jf.entries(); en.hasMoreElements();) {
                            JarEntry e = en.nextElement();
                            if (!e.isDirectory()) out.add(e.getName());
                        }
                    }
                }
            } catch (IOException | URISyntaxException | RuntimeException ignored) { }
            Collections.sort(out);
            entries = out;
        }
        return entries;
    }

    /** Bundled files under a folder (recursive), as paths relative to that folder. */
    public static List<String> bundledUnder(String dir) {
        String prefix = dir.endsWith("/") ? dir : dir + "/";
        List<String> out = new ArrayList<>();
        for (String e : bundledEntries()) if (e.startsWith(prefix)) out.add(e.substring(prefix.length()));
        return out;
    }

    /** Files in a side folder (recursive), relative paths with '/' separators. */
    public static List<String> folderUnder(Path root) {
        List<String> out = new ArrayList<>();
        if (root == null || !Files.isDirectory(root)) return out;
        try (Stream<Path> s = Files.walk(root)) {
            s.filter(Files::isRegularFile).forEach(f -> out.add(root.relativize(f).toString().replace('\\', '/')));
        } catch (IOException | RuntimeException ignored) { }
        Collections.sort(out);
        return out;
    }

    public static byte[] readBundled(String path) {
        try (InputStream in = Resources.class.getResourceAsStream("/" + path)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    public static byte[] readFile(Path p) {
        try {
            return Files.readAllBytes(p);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public static String text(byte[] b) { return b == null ? null : new String(b, java.nio.charset.StandardCharsets.UTF_8); }
}
