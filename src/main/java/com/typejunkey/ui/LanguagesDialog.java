package com.typejunkey.ui;

import com.typejunkey.data.Content;
import com.typejunkey.data.Settings;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

/** Choose several languages for a list setting such as the polyglot funbox. */
final class LanguagesDialog extends JDialog {
    LanguagesDialog(Component parent, String key) {
        super(javax.swing.SwingUtilities.getWindowAncestor(parent), "Languages", ModalityType.APPLICATION_MODAL);
        Settings cfg = Settings.get();
        List<JCheckBox> boxes = new ArrayList<>();
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        List<String> on = cfg.list(key);
        for (String l : Content.languages()) {
            JCheckBox cb = new JCheckBox(l.replace('_', ' '));
            cb.putClientProperty("lang", l);
            cb.setSelected(on.contains(l));
            boxes.add(cb);
            list.add(cb);
        }
        add(new JLabel("  Words are picked from all ticked languages (at least two)."), BorderLayout.NORTH);
        add(new JScrollPane(list), BorderLayout.CENTER);
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton ok = new JButton("OK");
        ok.addActionListener(e -> {
            List<String> sel = new ArrayList<>();
            for (JCheckBox b : boxes) if (b.isSelected()) sel.add((String) b.getClientProperty("lang"));
            cfg.setList(key, sel);
            dispose();
        });
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        south.add(ok);
        south.add(cancel);
        add(south, BorderLayout.SOUTH);
        setSize(360, 460);
        setLocationRelativeTo(parent);
    }
}
