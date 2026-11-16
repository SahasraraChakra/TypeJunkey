package com.typejunkey.ui;

import com.typejunkey.data.Settings;
import com.typejunkey.model.TestResult;
import com.typejunkey.model.Theme;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

/** Results screen: big speed and accuracy, chart or words history, and the detail row. */
public final class ResultPanel extends JPanel {
    private final Settings cfg = Settings.get();
    private Theme theme = Theme.fallback();
    private TestResult result;
    private boolean pb, showWords;
    private final ThemeButton next = new ThemeButton("Next test", "Start a new test");
    private final ThemeButton repeat = new ThemeButton("Repeat test", "Retry the same words");
    private final ThemeButton words = new ThemeButton("Words history", "Show every word with its speed and mistakes");
    private final JPanel bar = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 8));
    private final Canvas canvas = new Canvas();

    public ResultPanel(Runnable onNext, Runnable onRepeat) {
        super(new BorderLayout());
        next.addActionListener(e -> onNext.run());
        repeat.addActionListener(e -> onRepeat.run());
        words.addActionListener(e -> { showWords = !showWords; canvas.repaint(); });
        bar.add(next);
        bar.add(repeat);
        bar.add(words);
        add(canvas, BorderLayout.CENTER);
        add(bar, BorderLayout.SOUTH);
        setTheme(theme);
    }

    public void setTheme(Theme t) {
        theme = t;
        bar.setBackground(t.bg);
        bar.setOpaque(true);
        next.setTheme(t);
        repeat.setTheme(t);
        words.setTheme(t);
        repaint();
    }

    public void show(TestResult r, boolean personalBest) {
        result = r;
        pb = personalBest;
        showWords = cfg.bool("alwaysShowWordsHistory");
        repaint();
    }

    private double unit() {
        switch (cfg.str("typingSpeedUnit")) {
            case "cpm": return 5;
            case "wps": return 1 / 60.0;
            case "cps": return 5 / 60.0;
            case "wph": return 60;
            default: return 1;
        }
    }

    private String num(double v) { return cfg.bool("alwaysShowDecimalPlaces") ? String.format("%.2f", v) : Long.toString(Math.round(v)); }

    private static Color mix(Color c, Color b, double a) {
        return new Color((int) Math.round(c.getRed() * a + b.getRed() * (1 - a)), (int) Math.round(c.getGreen() * a + b.getGreen() * (1 - a)),
                (int) Math.round(c.getBlue() * a + b.getBlue() * (1 - a)));
    }

    private final class Canvas extends JComponent {
        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Render.hints(g);
            int W = getWidth(), H = getHeight();
            g.setColor(theme.bg);
            g.fillRect(0, 0, W, H);
            if (result == null) {
                g.setFont(Fonts.ui(Font.PLAIN, 15f));
                g.setColor(theme.sub);
                String s = "Finish a test to see your results.";
                g.drawString(s, (W - g.getFontMetrics().stringWidth(s)) / 2f, H / 2f);
                g.dispose();
                return;
            }
            TestResult r = result;
            String u = cfg.str("typingSpeedUnit");
            int m = Math.max(24, Math.min(60, W / 20));
            Font label = Fonts.type(14), big = Fonts.type(60), mid = Fonts.type(22), small = Fonts.type(13);

            int y = m;
            g.setFont(label); g.setColor(theme.sub);
            g.drawString(u, m, y + 14);
            g.setFont(big); g.setColor(r.failed ? theme.error : theme.main);
            g.drawString(num(r.wpm * unit()), m - 2, y + 66);
            g.setFont(label); g.setColor(theme.sub);
            g.drawString("acc", m, y + 100);
            g.setFont(big); g.setColor(r.failed ? theme.error : theme.main);
            g.drawString(cfg.bool("alwaysShowDecimalPlaces") ? String.format("%.2f%%", r.acc) : Math.round(r.acc) + "%", m - 2, y + 152);
            g.setFont(small);
            if (r.failed) {
                g.setColor(theme.error);
                g.drawString("test failed", m, y + 182);
                g.setColor(theme.sub);
                g.drawString(r.failReason, m, y + 202);
            } else if (pb) {
                g.setColor(theme.main);
                g.drawString("new personal best!", m, y + 182);
            }

            int bottomH = 92;
            Rectangle area = new Rectangle(m + 200, m, W - 2 * m - 200, H - 2 * m - bottomH);
            if (area.width > 120 && area.height > 90) {
                if (showWords) drawWords(g, area, r); else drawChart(g, area, r);
            }

            int by = H - m - 36;
            String type = r.label() + (r.flags().isEmpty() ? "" : " " + r.flags());
            String[][] items = {
                    {"test type", r.label(), r.language + (r.flags().isEmpty() ? "" : " " + r.flags()) + (r.funbox.isEmpty() ? "" : " \u00b7 " + r.funbox)},
                    {"raw", num(r.raw * unit()), null},
                    {"characters", r.correct + "/" + r.incorrect + "/" + r.extra + "/" + r.missed, "correct/incorrect/extra/missed"},
                    {"consistency", Math.round(r.consistency) + "%", null},
                    {"time", Math.round(r.seconds) + "s", null}};
            int x = m;
            int colW = Math.max(110, (W - 2 * m) / 5);
            for (String[] it : items) {
                g.setFont(label); g.setColor(theme.sub);
                g.drawString(it[0], x, by);
                g.setFont(mid); g.setColor(theme.main);
                g.drawString(it[1], x, by + 26);
                if (it[2] != null) {
                    g.setFont(small); g.setColor(theme.sub);
                    String s = it[2];
                    FontMetrics fm = g.getFontMetrics();
                    while (s.length() > 4 && fm.stringWidth(s) > colW - 12) s = s.substring(0, s.length() - 2) + "\u2026";
                    g.drawString(s, x, by + 44);
                }
                x += colW;
            }
            g.dispose();
        }

        private void drawWords(Graphics2D g, Rectangle a, TestResult r) {
            Font f = Fonts.type(16);
            g.setFont(f);
            FontMetrics fm = g.getFontMetrics();
            g.setColor(theme.sub);
            g.drawString(cfg.bool("burstHeatmap") ? "words history \u00b7 heatmap: slow \u2192 fast" : "words history", a.x, a.y + 12);
            double min = Double.MAX_VALUE, max = 0;
            for (double b : r.bursts) { min = Math.min(min, b); max = Math.max(max, b); }
            int x = a.x, y = a.y + 40, lineH = fm.getHeight() + 16;
            for (int i = 0; i < r.words.size(); i++) {
                String[] w = r.words.get(i);
                int ww = Math.max(fm.stringWidth(w[0]), fm.stringWidth(w[1])) + fm.charWidth(' ');
                if (x + ww > a.x + a.width) { x = a.x; y += lineH; }
                if (y > a.y + a.height) break;
                boolean ok = w[0].equals(w[1]);
                Color c = ok ? theme.text : theme.error;
                if (cfg.bool("burstHeatmap") && i < r.bursts.length && max > min) {
                    double t = (r.bursts[i] - min) / (max - min);
                    c = t < 0.5 ? mix(theme.text, theme.error, t * 2) : mix(theme.main, theme.text, (t - 0.5) * 2);
                    c = t < 0.5 ? mix(theme.sub, theme.error, t * 2) : mix(theme.main, theme.sub, (t - 0.5) * 2);
                }
                g.setColor(c);
                g.drawString(w[0], x, y);
                if (!ok && !w[1].isEmpty()) {
                    g.setFont(Fonts.type(11));
                    g.setColor(theme.error);
                    g.drawString(w[1], x, y + 12);
                    g.setFont(f);
                }
                x += ww;
            }
        }

        private void drawChart(Graphics2D g, Rectangle a, TestResult r) {
            double u = unit();
            double max = 10, min = Double.MAX_VALUE;
            for (double v : r.wpmSeries) { max = Math.max(max, v * u); min = Math.min(min, v * u); }
            for (double v : r.rawSeries) { max = Math.max(max, v * u); min = Math.min(min, v * u); }
            if (min == Double.MAX_VALUE) min = 0;
            double[] steps = {0.5, 1, 2, 5, 10, 20, 25, 50, 100, 200, 500, 1000, 2000};
            double step = steps[steps.length - 1];
            for (double s : steps) if (max / s <= 5) { step = s; break; }
            double top = Math.ceil(max / step) * step;
            double bottom = cfg.bool("startGraphsAtZero") ? 0 : Math.floor(min / step) * step;
            if (top <= bottom) top = bottom + step;
            int left = a.x + 44, right = a.x + a.width - 8, t = a.y + 22, b = a.y + a.height - 22;
            g.setFont(Fonts.type(12));
            for (double v = bottom; v <= top + 0.0001; v += step) {
                double yy = b - (b - t) * ((v - bottom) / (top - bottom));
                g.setColor(theme.subAlt);
                g.setStroke(new BasicStroke(1f));
                g.draw(new Line2D.Double(left, yy, right, yy));
                g.setColor(theme.sub);
                String s = step < 1 ? String.format("%.1f", v) : Long.toString(Math.round(v));
                g.drawString(s, left - 6 - g.getFontMetrics().stringWidth(s), (float) yy + 4);
            }
            int n = r.wpmSeries.length;
            if (n == 0) return;
            double[] xs = new double[n];
            for (int i = 0; i < n; i++) xs[i] = n == 1 ? (left + right) / 2.0 : left + (right - left) * i / (double) (n - 1);
            int stride = Math.max(1, (int) Math.ceil(n / 12.0));
            g.setColor(theme.sub);
            for (int i = 0; i < n; i += stride) {
                String s = Integer.toString(i + 1);
                g.drawString(s, (float) xs[i] - g.getFontMetrics().stringWidth(s) / 2f, b + 16);
            }
            plot(g, xs, r.rawSeries, theme.sub, 2f, top, bottom, u, t, b, false);
            plot(g, xs, r.wpmSeries, theme.main, 2.6f, top, bottom, u, t, b, true);
            if (r.paceWpm > 0) {
                double yy = b - (b - t) * Math.max(0, Math.min(1, (r.paceWpm * u - bottom) / (top - bottom)));
                g.setColor(theme.sub);
                g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, new float[] {5f, 4f}, 0f));
                g.draw(new Line2D.Double(left, yy, right, yy));
            }
            int maxErr = 0;
            for (int e : r.errSeries) maxErr = Math.max(maxErr, e);
            if (maxErr > 0) {
                g.setColor(theme.error);
                g.setStroke(new BasicStroke(1.6f));
                for (int i = 0; i < n && i < r.errSeries.length; i++) {
                    if (r.errSeries[i] <= 0) continue;
                    double yy = b - 10 - (b - t) * 0.4 * r.errSeries[i] / maxErr;
                    g.draw(new Line2D.Double(xs[i] - 3, yy - 3, xs[i] + 3, yy + 3));
                    g.draw(new Line2D.Double(xs[i] - 3, yy + 3, xs[i] + 3, yy - 3));
                }
            }
            int lx = left;
            g.setFont(Fonts.type(12));
            String[] names = {cfg.str("typingSpeedUnit"), "raw", "errors"};
            Color[] cols = {theme.main, theme.sub, theme.error};
            for (int i = 0; i < 3; i++) {
                g.setColor(cols[i]);
                g.fill(new Ellipse2D.Double(lx, a.y + 3, 8, 8));
                g.setColor(theme.sub);
                g.drawString(names[i], lx + 12, a.y + 12);
                lx += 20 + g.getFontMetrics().stringWidth(names[i]) + 12;
            }
        }

        private void plot(Graphics2D g, double[] xs, double[] vs, Color c, float w, double top, double bottom, double u, int t, int b, boolean dots) {
            Path2D p = new Path2D.Double();
            for (int i = 0; i < xs.length && i < vs.length; i++) {
                double yy = b - (b - t) * Math.max(0, Math.min(1, (vs[i] * u - bottom) / (top - bottom)));
                if (i == 0) p.moveTo(xs[i], yy); else p.lineTo(xs[i], yy);
            }
            g.setColor(c);
            g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(p);
            if (dots) {
                for (int i = 0; i < xs.length && i < vs.length; i++) {
                    double yy = b - (b - t) * Math.max(0, Math.min(1, (vs[i] * u - bottom) / (top - bottom)));
                    g.fill(new Ellipse2D.Double(xs[i] - 3, yy - 3, 6, 6));
                }
            }
        }
    }
}
