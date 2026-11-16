package com.typejunkey.ui;

import com.typejunkey.data.Content;
import com.typejunkey.data.Settings;
import com.typejunkey.model.KeyLayout;
import com.typejunkey.model.Theme;
import com.typejunkey.tutor.FingerMap;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;

/** On-screen keyboard under the typing text (static, react to keys, or show the next key). */
final class KeymapView extends JComponent {
    private final Settings cfg = Settings.get();
    private Theme theme = Theme.fallback();
    private String layoutName = "qwerty";
    private char next, pressed;
    private long pressedAt;
    private boolean shiftNeeded;

    KeymapView() {
        setOpaque(false);
        javax.swing.Timer t = new javax.swing.Timer(60, e -> { if (pressed != 0 && System.currentTimeMillis() - pressedAt > 160) { pressed = 0; repaint(); } });
        t.start();
    }

    void setTheme(Theme t) { theme = t; repaint(); }
    void setLayoutName(String n) { layoutName = n; revalidate(); repaint(); }
    void setNext(char c) { if (c != next) { next = c; repaint(); } }
    void pressed(char c) { pressed = c; pressedAt = System.currentTimeMillis(); repaint(); }
    boolean active() { return !cfg.str("keymapMode").equals("off"); }

    private KeyboardGeometry.Board board() {
        KeyLayout lay = Content.layout(layoutName);
        String keys = cfg.str("keymapKeys");
        boolean full = keys.equals("full");
        boolean numRow = keys.equals("minimal_numrow") || full || lay.showTopRow;
        return KeyboardGeometry.build(lay, cfg.str("keymapStyle"), full, numRow);
    }

    @Override
    public Dimension getPreferredSize() {
        if (!active()) return new Dimension(0, 0);
        KeyboardGeometry.Board b = board();
        double s = cfg.num("keymapSize") / 10.0;
        return new Dimension((int) (b.width * s) + 8, (int) (b.height * s) + 8);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        if (!active()) return;
        Graphics2D g = (Graphics2D) g0.create();
        Render.hints(g);
        KeyboardGeometry.Board b = board();
        KeyLayout lay = Content.layout(layoutName);
        double s = cfg.num("keymapSize") / 10.0;
        g.translate((getWidth() - b.width * s) / 2, 4);
        g.scale(s, s);
        String mode = cfg.str("keymapMode"), legend = cfg.str("keymapLegendStyle");
        FingerMap.Target nt = next == 0 ? null : FingerMap.find(lay, next);
        FingerMap.Target pt = pressed == 0 ? null : FingerMap.find(lay, pressed);
        Font f = Fonts.type(12f), small = Fonts.type(10f);
        for (KeyboardGeometry.Key k : b.keys) {
            boolean on = false, shiftHint = false;
            if (mode.equals("next") && nt != null) {
                on = k.row == nt.row && k.idx == nt.idx && k.row >= 0;
                if (nt.shift && k.shiftKey && nt.shiftFinger != null && nt.shiftFinger.equals(k.finger)) on = true;
            } else if (mode.equals("next") && next == ' ' && k.space) on = true;
            if (mode.equals("react") && pt != null) on = k.row == pt.row && k.idx == pt.idx && k.row >= 0 || (pressed == ' ' && k.space);
            if (mode.equals("next") && !on && cfg.str("oppositeShiftMode").equals("keymap") && nt != null && nt.shift && k.shiftKey && nt.shiftFinger != null && nt.shiftFinger.equals(k.finger)) shiftHint = true;
            g.setColor(on ? theme.main : shiftHint ? theme.sub : theme.subAlt);
            g.fill(new RoundRectangle2D.Double(k.x, k.y, k.w, KeyboardGeometry.KH, 4, 4));
            String label = k.label;
            if (k.row >= 0 && k.row <= 3) {
                switch (legend) {
                    case "uppercase": label = k.upper.length() == 1 && Character.isLetter(k.upper.charAt(0)) ? k.upper : k.lower.toUpperCase(); break;
                    case "blank": label = ""; break;
                    case "dynamic": label = (mode.equals("next") && nt != null && nt.shift) ? k.upper : k.lower; break;
                    default: label = k.lower;
                }
            }
            if (!label.isEmpty()) {
                g.setFont(label.length() > 1 ? small : f);
                FontMetrics fm = g.getFontMetrics();
                g.setColor(on ? theme.bg : theme.sub);
                g.drawString(label, (float) (k.x + (k.w - fm.stringWidth(label)) / 2), (float) (k.y + (KeyboardGeometry.KH - fm.getHeight()) / 2 + fm.getAscent()));
            }
        }
        g.dispose();
    }
}
