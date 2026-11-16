package com.typejunkey.ui;

import com.typejunkey.model.KeyLayout;
import com.typejunkey.tutor.FingerMap;

import java.util.ArrayList;
import java.util.List;

/** Key rectangles for a layout, in design units (one key is 26 wide). Shared by the tutor and the keymap. */
final class KeyboardGeometry {
    private KeyboardGeometry() {}

    static final double U = 26, KH = 24;

    static final class Key {
        double x, y, w;
        String label = "", lower = "", upper = "";
        String finger = "lp";
        int row = -1, idx = -1;
        boolean shiftKey, left, modifier, space;
    }

    /** Result of {@link #build}: the keys plus the total size. */
    static final class Board {
        final List<Key> keys = new ArrayList<>();
        double width, height;
    }

    static Board build(KeyLayout lay, String style, boolean modifiers, boolean numRow) {
        Board b = new Board();
        if (lay == null) return b;
        boolean matrix = style.contains("matrix") || style.startsWith("steno");
        boolean split = style.startsWith("split") || style.equals("alice");
        boolean st = lay.staggered() && !matrix;
        double gap = split ? 1.5 * U : 0;
        int[] splitAt = {7, 5, 5, 5};
        double[] noModLead = {0, 0.5, 0.75, 1.25};
        double[] lead = st && modifiers ? new double[] {0, 1.5, 1.75, lay.type.equals("iso") ? 1.25 : 2.25} : (st ? noModLead : new double[] {0, 0, 0, 0});
        String[] leadLabel = {"", "Tab", "Caps", "Shift"}, trail = {"Bksp", "", "Enter", "Shift"};
        int row0 = numRow ? 0 : 1;
        double maxX = 0;
        for (int r = row0; r < 4 && r < lay.rows.size(); r++) {
            List<String[]> row = lay.rows.get(r);
            double y = (r - row0) * U, x = 0;
            if (lead[r] > 0) {
                if (modifiers && st) {
                    Key k = new Key();
                    k.x = x; k.y = y; k.w = lead[r] * U - 2; k.label = leadLabel[r]; k.finger = "lp"; k.modifier = true;
                    k.shiftKey = r == 3; k.left = true;
                    b.keys.add(k);
                }
                x += lead[r] * U;
            }
            for (int i = 0; i < row.size(); i++) {
                if (split && i == splitAt[r] ) x += gap;
                Key k = new Key();
                k.x = x; k.y = y; k.w = U - 2; k.row = r; k.idx = i;
                String[] kk = row.get(i);
                k.lower = kk.length > 0 ? kk[0] : "";
                k.upper = kk.length > 1 ? kk[1] : k.lower.toUpperCase();
                k.label = k.lower;
                k.finger = FingerMap.finger(lay, r, i);
                b.keys.add(k);
                x += U;
            }
            if (st && modifiers) {
                double rem = 15 * U + (split ? gap : 0) - x;
                if (rem >= 0.75 * U) {
                    Key k = new Key();
                    k.x = x; k.y = y; k.w = rem - 2; k.label = trail[r]; k.finger = "rp"; k.modifier = true; k.shiftKey = r == 3; k.left = false;
                    b.keys.add(k);
                    x += rem;
                } else if (rem > 0 && !row.isEmpty()) {
                    b.keys.get(b.keys.size() - 1).w += rem;
                    x += rem;
                }
            }
            maxX = Math.max(maxX, x);
        }
        double y = (4 - row0) * U;
        if (st && modifiers) {
            String[] lab = {"Ctrl", "Win", "Alt", "", "Alt", "Win", "Menu", "Ctrl"};
            double[] wd = {1.25, 1.25, 1.25, 6.25, 1.25, 1.25, 1.25, 1.25};
            double x = 0;
            for (int i = 0; i < lab.length; i++) {
                Key k = new Key();
                k.x = x; k.y = y; k.w = wd[i] * U - 2; k.label = lab[i]; k.modifier = true;
                k.finger = i == 3 ? "rt" : (i < 4 ? "lp" : "rp");
                if (i == 2) k.finger = "lt"; else if (i == 4) k.finger = "rt";
                if (i == 3) { k.row = 4; k.idx = 0; k.space = true; k.modifier = false; }
                b.keys.add(k);
                x += wd[i] * U;
            }
            maxX = Math.max(maxX, x);
        } else {
            double sw = 6.25 * U;
            Key k = new Key();
            k.x = st ? 3.75 * U : Math.max(0, (Math.max(maxX, 10 * U) - sw) / 2);
            k.y = y; k.w = sw - 2; k.finger = "rt"; k.row = 4; k.idx = 0; k.space = true;
            b.keys.add(k);
            maxX = Math.max(maxX, k.x + sw);
        }
        b.width = Math.max(maxX, 15 * U);
        b.height = (5 - row0) * U;
        return b;
    }
}
