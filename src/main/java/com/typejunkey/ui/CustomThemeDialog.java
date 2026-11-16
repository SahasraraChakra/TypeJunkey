package com.typejunkey.ui;

import com.typejunkey.data.Settings;
import com.typejunkey.data.ThemeManager;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/** Edit the ten colours of the custom theme, with a live preview. */
final class CustomThemeDialog extends JDialog {
    private final Color[] colors = ThemeManager.customColors();
    private final JButton[] swatches = new JButton[10];
    private final JComponent preview = new JComponent() {
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Render.hints(g);
            g.setColor(colors[0]); g.fillRect(0, 0, getWidth(), getHeight());
            g.setFont(Fonts.type(22));
            int x = 20, y = 46;
            String[] parts = {"the ", "quick ", "brown "};
            Color[] cols = {colors[5], colors[5], colors[3]};
            for (int i = 0; i < parts.length; i++) { g.setColor(cols[i]); g.drawString(parts[i], x, y); x += g.getFontMetrics().stringWidth(parts[i]); }
            g.setColor(colors[6]); g.drawString("fx", x, y); x += g.getFontMetrics().stringWidth("fx");
            g.setColor(colors[7]); g.drawString("tra ", x, y); x += g.getFontMetrics().stringWidth("tra ");
            g.setColor(colors[3]); g.drawString("jumps", x, y);
            g.setColor(colors[2]); g.fillRect(20 + g.getFontMetrics().stringWidth("the quick "), y - 22, 2, 28);
            g.setColor(colors[1]); g.setFont(Fonts.type(30)); g.drawString("86", 20, 100);
            g.setColor(colors[4]); g.fillRoundRect(100, 78, 120, 26, 10, 10);
            g.setColor(colors[3]); g.setFont(Fonts.type(12)); g.drawString("sub alt", 120, 96);
            g.dispose();
        }
    };

    CustomThemeDialog(Component parent) {
        super(javax.swing.SwingUtilities.getWindowAncestor(parent), "Custom theme", ModalityType.APPLICATION_MODAL);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(10, 14, 6, 14));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 4, 3, 4);
        c.anchor = GridBagConstraints.WEST;
        for (int i = 0; i < 10; i++) {
            final int idx = i;
            c.gridy = i; c.gridx = 0;
            form.add(new JLabel(ThemeManager.COLOR_NAMES[i]), c);
            JButton b = new JButton("      ");
            b.setBackground(colors[i]);
            b.setPreferredSize(new Dimension(90, 24));
            b.addActionListener(e -> {
                Color n = JColorChooser.showDialog(this, ThemeManager.COLOR_NAMES[idx], colors[idx]);
                if (n != null) { colors[idx] = n; b.setBackground(n); preview.repaint(); }
            });
            swatches[i] = b;
            c.gridx = 1;
            form.add(b, c);
        }
        preview.setPreferredSize(new Dimension(300, 120));
        JPanel right = new JPanel(new BorderLayout());
        right.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 14));
        right.add(preview, BorderLayout.NORTH);
        add(form, BorderLayout.WEST);
        add(right, BorderLayout.CENTER);
        JCheckBox use = new JCheckBox("Use the custom theme now", Settings.get().bool("customTheme"));
        JButton ok = new JButton("Save");
        ok.addActionListener(e -> { ThemeManager.saveCustom(colors); Settings.get().set("customTheme", use.isSelected()); dispose(); });
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        south.add(use); south.add(ok); south.add(cancel);
        add(south, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(parent);
    }
}
