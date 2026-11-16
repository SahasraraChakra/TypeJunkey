package com.typejunkey.model;

import java.util.List;

/** A keyboard layout from Monkeytype's layout files: rows of keys, each key {lower[, upper]}. */
public final class KeyLayout {
    public final String name, type;
    public final List<List<String[]>> rows; // row 0 = number row ... row 3 = bottom letter row, row 4 = space row
    public final boolean showTopRow;        // layouts with extra number-row symbols ask for the number row

    public KeyLayout(String name, String type, List<List<String[]>> rows) { this(name, type, rows, false); }

    public KeyLayout(String name, String type, List<List<String[]>> rows, boolean showTopRow) {
        this.name = name; this.type = type; this.rows = rows; this.showTopRow = showTopRow;
    }

    public boolean staggered() { return type.equals("ansi") || type.equals("iso"); }
}
