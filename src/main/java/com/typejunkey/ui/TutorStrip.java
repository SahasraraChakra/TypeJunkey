package com.typejunkey.ui;

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
import java.awt.RenderingHints;
import java.awt.BasicStroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * The strip pinned to the bottom of the tutor: left hand, compact keyboard, right hand. The strip scales with
 * the window width. The finger and the key for the next character are highlighted.
 */
final class TutorStrip extends JComponent {
    private static final double W0 = 640, H0 = 192, U = 26, KH = 24, X0 = 125, Y0 = 58;
    private static final java.util.Map<Character, Color> ZONE = new java.util.HashMap<>();
    static {
        ZONE.put('p', new Color(0x9b6aa8)); ZONE.put('r', new Color(0x5f86b8)); ZONE.put('m', new Color(0x4f9a86));
        ZONE.put('i', new Color(0xb58d52)); ZONE.put('t', new Color(0x7b7f84));
    }
    // finger shapes (left hand, back view, fingers up): code, base x, base y, tilt degrees, length, base width, tip width
    private static final double[][] FING = {
            {'p', 34, 102, -13, 44, 15, 11.5}, {'r', 54, 92, -5, 56, 17, 13}, {'m', 73, 88, 0, 62, 18, 13.5},
            {'i', 92, 92, 6, 55, 17.5, 13}, {'t', 97, 142, 50, 52, 20, 14}};

    private Theme theme = Theme.fallback();
    private KeyLayout layout;
    private FingerMap.Target target;
    private boolean showHands = true;

    void setTheme(Theme t) { theme = t; repaint(); }
    void setLayout(KeyLayout l) { layout = l; repaint(); }
    void setTarget(FingerMap.Target t) { target = t; repaint(); }
    void setShowHands(boolean b) { showHands = b; repaint(); }

    @Override
    public Dimension getPreferredSize() {
        int w = Math.max(getWidth(), 640);
        return new Dimension(640, (int) Math.max(150, Math.min(240, w * H0 / W0)));
    }

    private static Color mix(Color c, Color b, double a) {
        return new Color((int) Math.round(c.getRed() * a + b.getRed() * (1 - a)), (int) Math.round(c.getGreen() * a + b.getGreen() * (1 - a)),
                (int) Math.round(c.getBlue() * a + b.getBlue() * (1 - a)));
    }

    private List<KeyboardGeometry.Key> geometry() {
        List<KeyboardGeometry.Key> keys = new ArrayList<>();
        if (layout == null) return keys;
        KeyboardGeometry.Board board = KeyboardGeometry.build(layout, "staggered", true, true);
        for (KeyboardGeometry.Key k : board.keys) {
            if (k.row >= 0 && k.row <= 3 && !k.label.isEmpty() && k.label.length() == 1 && Character.isLetter(k.label.charAt(0))) k.label = k.label.toUpperCase();
            k.x += X0; k.y += Y0;
            keys.add(k);
        }
        return keys;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Render.hints(g);
        g.setColor(theme.bg);
        g.fillRect(0, 0, getWidth(), getHeight());
        double s = Math.min(getWidth() / W0, getHeight() / H0);
        g.translate((getWidth() - W0 * s) / 2, getHeight() - H0 * s);
        g.scale(s, s);

        String hi1 = target == null ? null : target.finger;
        String hi2 = target == null || !target.shift ? null : target.shiftFinger;
        if (showHands) {
            double S = 0.88, TY = 188 - 178 * S;
            AffineTransform base = g.getTransform();
            g.translate(12 - 26 * S, TY); g.scale(S, S);
            hand(g, 'l', hi1, hi2);
            g.setTransform(base);
            g.translate(628 + 26 * S, TY); g.scale(-S, S);
            hand(g, 'r', hi1, hi2);
            g.setTransform(base);
        }
        paintKeys(g, hi1, hi2);
        g.dispose();
    }

    private void paintKeys(Graphics2D g, String hi1, String hi2) {
        Font f = Fonts.ui(Font.PLAIN, 12f);
        Font small = Fonts.ui(Font.PLAIN, 11f);
        for (KeyboardGeometry.Key k : geometry()) {
            boolean on = false;
            if (target != null) {
                if (k.row == target.row && k.idx == target.idx && k.row >= 0) on = true;
                if (target.shift && k.shiftKey && hi2 != null && hi2.equals(k.finger)) on = true;
            }
            Color zone = ZONE.get(k.finger.charAt(1));
            g.setColor(on ? theme.main : mix(zone, theme.subAlt, 0.3));
            g.fill(new RoundRectangle2D.Double(k.x, k.y, k.w, KH, 4, 4));
            if (!k.label.isEmpty()) {
                Font use = k.label.length() > 1 ? small : f;
                g.setFont(use);
                FontMetrics fm = g.getFontMetrics();
                g.setColor(on ? theme.bg : mix(theme.text, theme.subAlt, 0.7));
                g.drawString(k.label, (float) (k.x + (k.w - fm.stringWidth(k.label)) / 2), (float) (k.y + (KH - fm.getHeight()) / 2 + fm.getAscent()));
            }
            if (k.row == 2 && (k.idx == 3 || k.idx == 6) && layout != null && layout.staggered()) {
                g.setColor(on ? theme.bg : theme.text);
                g.fill(new RoundRectangle2D.Double(k.x + k.w / 2 - 4, k.y + KH - 6, 8, 1.6, 1, 1));
            }
        }
    }

    // ------------------------------------------------------------------ hands
    private static Path2D finger(double bx, double by, double th, double len, double w0, double w1, double[] tip) {
        double a = Math.toRadians(th), dx = Math.sin(a), dy = -Math.cos(a), nx = Math.cos(a), ny = Math.sin(a);
        double tx = bx + dx * len, ty = by + dy * len;
        Path2D p = new Path2D.Double();
        p.moveTo(bx - nx * w0 / 2, by - ny * w0 / 2);
        p.lineTo(tx - nx * w1 / 2, ty - ny * w1 / 2);
        p.quadTo(tx + dx * w1 * 0.95, ty + dy * w1 * 0.95, tx + nx * w1 / 2, ty + ny * w1 / 2);
        p.lineTo(bx + nx * w0 / 2, by + ny * w0 / 2);
        p.closePath();
        tip[0] = tx; tip[1] = ty; tip[2] = dx; tip[3] = dy; tip[4] = nx; tip[5] = ny;
        return p;
    }

    private void hand(Graphics2D g, char side, String hi1, String hi2) {
        Color sub = theme.sub;
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D palm = new Path2D.Double();
        palm.moveTo(46, 178); palm.curveTo(34, 160, 24, 132, 27, 102); palm.lineTo(27, 98); palm.quadTo(62, 70, 100, 94);
        palm.curveTo(106, 112, 100, 132, 96, 146); palm.curveTo(92, 160, 90, 170, 88, 178); palm.closePath();
        g.setColor(theme.bg); g.fill(palm);
        g.setColor(sub); g.draw(palm);
        g.setColor(mix(sub, theme.bg, 0.45));
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (double[] t : new double[][] {{54, 104, 58, 130, 57, 160}, {73, 100, 70, 130, 66, 162}, {92, 104, 84, 128, 78, 158}, {88, 112, 99, 124, 96, 144}}) {
            Path2D q = new Path2D.Double();
            q.moveTo(t[0], t[1]); q.quadTo(t[2], t[3], t[4], t[5]);
            g.draw(q);
        }
        int[] order = {4, 0, 1, 2, 3};
        List<Runnable> details = new ArrayList<>();
        for (int idx : order) {
            double[] F = FING[idx];
            char code = (char) (int) F[0];
            String id = "" + side + code;
            boolean on = id.equals(hi1) || id.equals(hi2);
            double[] tip = new double[6];
            Path2D fp = finger(F[1], F[2], F[3], F[4], F[5], F[6], tip);
            g.setColor(on ? mix(theme.main, theme.bg, 0.4) : mix(ZONE.get(code), theme.bg, 0.22));
            g.fill(fp);
            g.setColor(on ? theme.main : sub);
            g.setStroke(new BasicStroke(on ? 2.2f : 1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(fp);
            final double[] ft = F;
            final boolean fon = on;
            details.add(() -> {
                double cx = tip[0] - tip[2] * 8, cy = tip[1] - tip[3] * 8;
                AffineTransform old = g.getTransform();
                g.rotate(Math.toRadians(ft[3]), cx, cy);
                g.setColor(fon ? theme.main : mix(sub, theme.bg, 0.8));
                g.setStroke(new BasicStroke(1f));
                g.draw(new java.awt.geom.Ellipse2D.Double(cx - ft[6] * 0.3, cy - 5, ft[6] * 0.6, 10));
                g.setTransform(old);
                g.setColor(mix(sub, theme.bg, 0.6));
                for (double t : new double[] {0.42, 0.68}) {
                    double px = ft[1] + tip[2] * ft[4] * t, py = ft[2] + tip[3] * ft[4] * t, hw = ft[5] * 0.2;
                    Path2D c = new Path2D.Double();
                    c.moveTo(px - tip[4] * hw, py - tip[5] * hw);
                    c.quadTo(px, py + 1.6, px + tip[4] * hw, py + tip[5] * hw);
                    g.draw(c);
                }
            });
        }
        for (Runnable r : details) r.run();
    }
}
