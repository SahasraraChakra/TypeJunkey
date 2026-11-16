package com.typejunkey.tutor;

import com.typejunkey.model.KeyLayout;

import java.util.List;

/**
 * Which finger types which key. Fingers are named by hand and digit: lp lr lm li lt (left pinky, ring, middle,
 * index, thumb) and ri rm rr rp rt for the right hand. Assignment is by column, the usual touch-typing scheme.
 */
public final class FingerMap {
    private FingerMap() {}

    /** A key to press, and the Shift key if the character needs one. */
    public static final class Target {
        public final int row, idx;
        public final boolean shift;
        public final String finger, shiftFinger;

        Target(int row, int idx, boolean shift, String finger, String shiftFinger) {
            this.row = row; this.idx = idx; this.shift = shift; this.finger = finger; this.shiftFinger = shiftFinger;
        }
    }

    private static final String[] NUMBER_ROW = {"lp", "lp", "lr", "lm", "li", "li", "ri", "ri", "rm", "rr", "rp"};
    private static final String[] LETTER_ROW = {"lp", "lr", "lm", "li", "li", "ri", "ri", "rm", "rr", "rp"};

    public static String finger(KeyLayout lay, int row, int idx) {
        if (row == 4) return "rt";
        int c = idx;
        if (lay.type.equals("iso") && row == 3) c = idx - 1;
        else if (!lay.staggered() && row == 0 && lay.rows.size() > 1 && lay.rows.get(0).size() > lay.rows.get(1).size()) c = idx - 1;
        String[] table = row == 0 && lay.staggered() ? NUMBER_ROW : LETTER_ROW;
        if (c < 0) return "lp";
        return c >= table.length ? "rp" : table[c];
    }

    /** Finds the key that types c, or null when the layout has none (for example a line break). */
    public static Target find(KeyLayout lay, char c) {
        String s = String.valueOf(c);
        if (c == ' ') return new Target(4, 0, false, "rt", null);
        for (int r = 0; r < 4 && r < lay.rows.size(); r++) {
            List<String[]> row = lay.rows.get(r);
            for (int i = 0; i < row.size(); i++) {
                String[] k = row.get(i);
                if (k.length > 0 && k[0].equals(s)) return new Target(r, i, false, finger(lay, r, i), null);
                if (k.length > 1 && k[1].equals(s)) {
                    String f = finger(lay, r, i);
                    return new Target(r, i, true, f, f.startsWith("l") ? "rp" : "lp");
                }
            }
        }
        return null;
    }
}
