package com.typejunkey.ui;

import com.typejunkey.model.Theme;

import javax.swing.JButton;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/** Flat button painted in the colours of the active typing theme. */
final class ThemeButton extends JButton {
    private Theme theme = Theme.fallback();

    ThemeButton(String text, String tip) {
        super(text);
        setToolTipText(tip);
        setFocusable(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setPreferredSize(new Dimension(124, 34));
    }

    void setTheme(Theme t) { theme = t; repaint(); }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        boolean hot = getModel().isRollover() || getModel().isPressed();
        g.setColor(hot ? theme.text : theme.subAlt);
        g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
        g.setColor(hot ? theme.bg : theme.text);
        g.setFont(Fonts.ui(java.awt.Font.BOLD, 13f));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2f,
                (getHeight() - fm.getHeight()) / 2f + fm.getAscent());
        g.dispose();
    }
}
