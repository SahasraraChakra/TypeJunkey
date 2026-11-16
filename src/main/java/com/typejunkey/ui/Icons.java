package com.typejunkey.ui;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/** Small vector icons (16x16) drawn with Java2D so the app needs no image files. */
public final class Icons implements Icon {
    public enum Kind { RESTART, AT, HASH, CLOCK, WORDS, QUOTE, ZEN, CUSTOM, KEYBOARD, CHART, LIST, TROPHY, PALETTE, GEAR, SCHOOL, FOLDER, PREV, NEXT, FLASK, GHOST, SEARCH }

    private final Kind kind;
    private final Color color;

    public Icons(Kind kind, Color color) { this.kind = kind; this.color = color; }

    public static Icons of(Kind kind) { return new Icons(kind, null); }

    @Override public int getIconWidth() { return 16; }
    @Override public int getIconHeight() { return 16; }

    @Override
    public void paintIcon(Component c, Graphics g0, int x, int y) {
        Graphics2D g = (Graphics2D) g0.create();
        g.translate(x, y);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setColor(color != null ? color : (c != null ? c.getForeground() : Color.GRAY));
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (kind) {
            case RESTART: {
                g.draw(new Arc2D.Double(3, 3, 10, 10, 40, 280, Arc2D.OPEN));
                Path2D p = new Path2D.Double();
                p.moveTo(13.5, 1.5); p.lineTo(13.2, 6.2); p.lineTo(8.8, 4.6); p.closePath();
                g.fill(p);
                break;
            }
            case AT: glyph(g, "@", 14, Font.BOLD); break;
            case HASH: glyph(g, "#", 14, Font.BOLD); break;
            case CLOCK:
                g.draw(new Ellipse2D.Double(2, 2, 12, 12));
                g.draw(new Line2D.Double(8, 8, 8, 4.5));
                g.draw(new Line2D.Double(8, 8, 10.6, 9.6));
                break;
            case WORDS:
                g.draw(new Line2D.Double(2, 4, 14, 4));
                g.draw(new Line2D.Double(2, 8, 10, 8));
                g.draw(new Line2D.Double(2, 12, 13, 12));
                break;
            case QUOTE:
                g.fill(new Ellipse2D.Double(2, 6, 4.5, 4.5));
                g.fill(new Ellipse2D.Double(9, 6, 4.5, 4.5));
                g.draw(new Line2D.Double(5.4, 9, 3.4, 13));
                g.draw(new Line2D.Double(12.4, 9, 10.4, 13));
                break;
            case ZEN: {
                Path2D p = new Path2D.Double();
                p.moveTo(3, 13); p.quadTo(3, 3, 13, 3); p.quadTo(13, 13, 3, 13); p.closePath();
                g.draw(p);
                g.draw(new Line2D.Double(3, 13, 9, 7));
                break;
            }
            case CUSTOM: {
                g.setStroke(new BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Double(6, 10, 12.2, 3.8));
                Path2D p = new Path2D.Double();
                p.moveTo(2.5, 13.5); p.lineTo(3.6, 9.4); p.lineTo(6.6, 12.4); p.closePath();
                g.fill(p);
                break;
            }
            case KEYBOARD:
                g.draw(new RoundRectangle2D.Double(1, 3, 14, 10, 2, 2));
                for (int i = 0; i < 3; i++) {
                    g.fill(new RoundRectangle2D.Double(3.3 + i * 3.6, 5.2, 2, 1.8, 0.5, 0.5));
                    g.fill(new RoundRectangle2D.Double(3.3 + i * 3.6, 8, 2, 1.8, 0.5, 0.5));
                }
                g.draw(new Line2D.Double(5, 11.4, 11, 11.4));
                break;
            case CHART: {
                Path2D ax = new Path2D.Double();
                ax.moveTo(2, 2); ax.lineTo(2, 14); ax.lineTo(14, 14);
                g.draw(ax);
                Path2D p = new Path2D.Double();
                p.moveTo(4.5, 11); p.lineTo(7, 7); p.lineTo(9.5, 9); p.lineTo(13, 4);
                g.draw(p);
                break;
            }
            case LIST:
                for (int i = 0; i < 3; i++) {
                    g.fill(new Ellipse2D.Double(2, 3 + i * 4.2, 2, 2));
                    g.draw(new Line2D.Double(6, 4 + i * 4.2, 14, 4 + i * 4.2));
                }
                break;
            case TROPHY: {
                Path2D p = new Path2D.Double();
                p.moveTo(4.5, 2); p.lineTo(11.5, 2); p.lineTo(11.5, 6); p.quadTo(11.5, 9.5, 8, 9.5);
                p.quadTo(4.5, 9.5, 4.5, 6); p.closePath();
                g.draw(p);
                g.draw(new Arc2D.Double(1.5, 3, 4, 4, 90, 180, Arc2D.OPEN));
                g.draw(new Arc2D.Double(10.5, 3, 4, 4, 90, -180, Arc2D.OPEN));
                g.draw(new Line2D.Double(8, 9.5, 8, 12.5));
                g.draw(new Line2D.Double(5, 13.5, 11, 13.5));
                break;
            }
            case PALETTE:
                g.draw(new Ellipse2D.Double(1.5, 1.5, 13, 13));
                g.fill(new Ellipse2D.Double(4, 6, 2.2, 2.2));
                g.fill(new Ellipse2D.Double(7, 3.8, 2.2, 2.2));
                g.fill(new Ellipse2D.Double(9.8, 7, 2.2, 2.2));
                break;
            case SCHOOL: {
                Path2D p = new Path2D.Double();
                p.moveTo(8, 3); p.lineTo(15, 6.5); p.lineTo(8, 10); p.lineTo(1, 6.5); p.closePath();
                g.draw(p);
                Path2D q = new Path2D.Double();
                q.moveTo(4, 8.5); q.lineTo(4, 11.5); q.quadTo(8, 14, 12, 11.5); q.lineTo(12, 8.5);
                g.draw(q);
                break;
            }
            case FOLDER: {
                Path2D p = new Path2D.Double();
                p.moveTo(2, 4); p.lineTo(6.5, 4); p.lineTo(8, 5.8); p.lineTo(14, 5.8); p.lineTo(14, 12.5); p.lineTo(2, 12.5); p.closePath();
                g.draw(p);
                break;
            }
            case PREV: {
                Path2D p = new Path2D.Double();
                p.moveTo(10, 3.5); p.lineTo(5.5, 8); p.lineTo(10, 12.5);
                g.draw(p);
                break;
            }
            case NEXT: {
                Path2D p = new Path2D.Double();
                p.moveTo(6, 3.5); p.lineTo(10.5, 8); p.lineTo(6, 12.5);
                g.draw(p);
                break;
            }
            case FLASK: {
                Path2D p = new Path2D.Double();
                p.moveTo(6, 2); p.lineTo(6, 6.5); p.lineTo(2.5, 13); p.lineTo(13.5, 13); p.lineTo(10, 6.5); p.lineTo(10, 2);
                g.draw(p);
                g.draw(new Line2D.Double(5, 2, 11, 2));
                g.draw(new Line2D.Double(4.5, 10, 11.5, 10));
                break;
            }
            case GHOST: {
                Path2D p = new Path2D.Double();
                p.moveTo(3, 13.5); p.lineTo(3, 7); p.curveTo(3, 1.5, 13, 1.5, 13, 7); p.lineTo(13, 13.5);
                p.lineTo(11, 12); p.lineTo(9.5, 13.5); p.lineTo(8, 12); p.lineTo(6.5, 13.5); p.lineTo(5, 12); p.closePath();
                g.draw(p);
                g.fill(new Ellipse2D.Double(5.3, 6, 1.8, 2.2));
                g.fill(new Ellipse2D.Double(8.9, 6, 1.8, 2.2));
                break;
            }
            case SEARCH:
                g.draw(new Ellipse2D.Double(2.5, 2.5, 8, 8));
                g.draw(new Line2D.Double(9, 9, 13.5, 13.5));
                break;
            case GEAR:
                g.draw(new Ellipse2D.Double(5, 5, 6, 6));
                g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    g.draw(new Line2D.Double(8 + 5 * Math.cos(a), 8 + 5 * Math.sin(a),
                            8 + 6.8 * Math.cos(a), 8 + 6.8 * Math.sin(a)));
                }
                break;
        }
        g.dispose();
    }

    private void glyph(Graphics2D g, String s, int size, int style) {
        Font f = Fonts.ui(style, size);
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, (16 - fm.stringWidth(s)) / 2f, (16 - fm.getHeight()) / 2f + fm.getAscent() - 0.5f);
    }
}
