package com.typejunkey.ui;

import com.typejunkey.data.Content;
import com.typejunkey.data.Settings;
import com.typejunkey.data.ThemeManager;
import com.typejunkey.model.Theme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/** Searchable grid of all themes with favorites and a custom theme editor. Clicking a theme applies it. */
public final class ThemesPanel extends JPanel {
    private final Settings cfg = Settings.get();
    private final Grid grid = new Grid();
    private final JTextField search = new JTextField(16);
    private final JCheckBox favOnly = new JCheckBox("Favorites only");

    public ThemesPanel() {
        super(new BorderLayout());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        top.add(new JLabel("Search themes:"));
        top.add(search);
        top.add(favOnly);
        JButton custom = new JButton("Custom theme\u2026");
        custom.setToolTipText("Edit the ten colours of your own theme");
        custom.addActionListener(e -> new CustomThemeDialog(this).setVisible(true));
        top.add(custom);
        top.add(new JLabel("\u2605 marks a favorite (used by random theme: favorites)"));
        add(top, BorderLayout.NORTH);

        JScrollPane sp = new JScrollPane(grid);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getVerticalScrollBar().setUnitIncrement(24);
        add(sp, BorderLayout.CENTER);

        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refresh(); }
            public void removeUpdate(DocumentEvent e) { refresh(); }
            public void changedUpdate(DocumentEvent e) { refresh(); }
        });
        favOnly.addActionListener(e -> refresh());
        refresh();
    }

    /** Rebuilds the grid (after content was hidden or reloaded, or favorites changed). */
    public void refresh() { grid.filter(search.getText(), favOnly.isSelected()); }

    /** Scrolls the grid to the currently active theme. */
    public void syncSelection() {
        grid.repaint();
        SwingUtilities.invokeLater(grid::scrollToCurrent);
    }

    private final class Grid extends JPanel implements Scrollable {
        private static final int CW = 176, CH = 70;
        private final List<Theme> items = new ArrayList<>();

        Grid() {
            setBackground(UIHelper.panelBackground());
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    int i = indexAt(e.getX(), e.getY());
                    if (i < 0 || i >= items.size()) return;
                    Theme t = items.get(i);
                    int cx = e.getX() % CW, cy = e.getY() % CH;
                    if (cx > CW - 36 && cy < 34) { toggleFavorite(t.name); return; }
                    cfg.setQuiet("customTheme", false);
                    cfg.theme = t.name;
                    cfg.save();
                    ThemeManager.clearOverride();
                    cfg.fire();
                    repaint();
                }
            });
        }

        private void toggleFavorite(String name) {
            List<String> fav = cfg.list("favThemes");
            if (fav.contains(name)) fav.remove(name); else fav.add(name);
            cfg.setList("favThemes", fav);
            if (favOnly.isSelected()) refresh(); else repaint();
        }

        void filter(String q, boolean favs) {
            items.clear();
            String needle = q.trim().toLowerCase().replace(' ', '_');
            List<String> fav = cfg.list("favThemes");
            for (Theme t : Content.themes()) {
                if (!needle.isEmpty() && !t.name.contains(needle)) continue;
                if (favs && !fav.contains(t.name)) continue;
                items.add(t);
            }
            revalidate();
            repaint();
        }

        private int viewWidth() { return getParent() instanceof JViewport ? Math.max(CW, getParent().getWidth()) : 800; }
        private int cols() { return Math.max(1, viewWidth() / CW); }

        private int indexAt(int x, int y) {
            int c = cols();
            int col = x / CW;
            if (col >= c) return -1;
            return (y / CH) * c + col;
        }

        void scrollToCurrent() {
            int c = cols();
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).name.equals(cfg.theme)) { scrollRectToVisible(new Rectangle((i % c) * CW, (i / c) * CH, CW, CH)); return; }
            }
        }

        @Override
        public Dimension getPreferredSize() {
            int c = cols();
            int rows = (items.size() + c - 1) / c;
            return new Dimension(viewWidth(), Math.max(CH, rows * CH));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            Render.hints(g);
            int c = cols();
            Rectangle clip = g.getClipBounds() != null ? g.getClipBounds() : new Rectangle(0, 0, getWidth(), getHeight());
            int first = Math.max(0, clip.y / CH * c);
            int last = Math.min(items.size() - 1, (clip.y + clip.height) / CH * c + c);
            List<String> fav = cfg.list("favThemes");
            Font mono = Fonts.type(13), star = Fonts.ui(Font.PLAIN, 16f);
            for (int i = first; i <= last; i++) {
                Theme t = items.get(i);
                int x = (i % c) * CW, y = (i / c) * CH;
                boolean sel = t.name.equals(cfg.theme) && !cfg.bool("customTheme");
                RoundRectangle2D box = new RoundRectangle2D.Float(x + 5, y + 5, CW - 10, CH - 10, 8, 8);
                g.setColor(t.bg);
                g.fill(box);
                g.setColor(sel ? new Color(0x3c7fb1) : new Color(0x80808080, true));
                g.setStroke(new BasicStroke(sel ? 3f : 1f));
                g.draw(box);
                g.setFont(mono);
                g.setColor(t.main);
                g.drawString(t.display(), x + 15, y + 28);
                g.setFont(star);
                g.setColor(fav.contains(t.name) ? t.main : t.sub);
                g.drawString(fav.contains(t.name) ? "\u2605" : "\u2606", x + CW - 30, y + 27);
                Color[] sw = {t.main, t.sub, t.text, t.error, t.subAlt};
                for (int k = 0; k < sw.length; k++) {
                    g.setColor(sw[k]);
                    g.fillRoundRect(x + 15 + k * 27, y + 40, 21, 14, 4, 4);
                }
            }
            g.dispose();
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return new Dimension(CW * 5, CH * 5); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return CH / 2; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(CH, r.height - CH); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
