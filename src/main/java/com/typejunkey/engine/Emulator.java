package com.typejunkey.engine;

import com.typejunkey.data.Content;
import com.typejunkey.model.KeyLayout;

import java.util.List;

/**
 * Layout emulation: maps the character produced by a QWERTY key to the character the same key would
 * produce on another layout, so you can practise Dvorak, Colemak and others on an ordinary keyboard.
 */
public final class Emulator {
    private Emulator() {}

    /** Character the key under c would type on layout {@code name}; c itself when unknown or name is "default". */
    public static char map(String name, char c) {
        if (name == null || name.equals("default") || name.isEmpty() || c == ' ') return c;
        KeyLayout from = Content.layout("qwerty"), to = Content.layout(name);
        for (int r = 0; r < 4 && r < from.rows.size(); r++) {
            List<String[]> row = from.rows.get(r);
            for (int i = 0; i < row.size(); i++) {
                String[] k = row.get(i);
                for (int v = 0; v < k.length && v < 2; v++) {
                    if (k[v].length() == 1 && k[v].charAt(0) == c) {
                        if (r < to.rows.size() && i < to.rows.get(r).size()) {
                            String[] t = to.rows.get(r).get(i);
                            if (v < t.length && t[v].length() == 1) return t[v].charAt(0);
                        }
                        return c;
                    }
                }
            }
        }
        return c;
    }

    /** Mirrors the keyboard left to right (the layout_mirror funbox). */
    public static char mirror(char c) {
        if (c == ' ') return c;
        KeyLayout q = Content.layout("qwerty");
        for (int r = 0; r < 4 && r < q.rows.size(); r++) {
            List<String[]> row = q.rows.get(r);
            for (int i = 0; i < row.size(); i++) {
                String[] k = row.get(i);
                for (int v = 0; v < k.length && v < 2; v++) {
                    if (k[v].length() == 1 && k[v].charAt(0) == c) {
                        int start = r == 0 ? 1 : 0;           // the ten main keys of a row: 1..0, q..p, a..;, z../
                        if (i < start || i > start + 9) return c;
                        int j = start + 9 - (i - start);
                        if (j >= row.size()) return c;
                        String[] t = row.get(j);
                        if (v < t.length && t[v].length() == 1) return t[v].charAt(0);
                        return c;
                    }
                }
            }
        }
        return c;
    }
}
