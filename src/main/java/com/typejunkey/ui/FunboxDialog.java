package com.typejunkey.ui;

import com.typejunkey.data.Settings;
import com.typejunkey.engine.Funbox;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

/** Pick the active funbox modifiers. Changes apply to the next test. */
final class FunboxDialog extends JDialog {
    private final Settings cfg = Settings.get();
    private final List<JCheckBox> boxes = new ArrayList<>();
    private final List<JPanel> rows = new ArrayList<>();

    FunboxDialog(Component parent) {
        super(javax.swing.SwingUtilities.getWindowAncestor(parent), "Funbox", ModalityType.APPLICATION_MODAL);
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        List<String> active = cfg.list("funbox");
        for (Funbox.Info i : Funbox.ALL) {
            JCheckBox cb = new JCheckBox(i.name);
            cb.setSelected(active.contains(i.name));
            cb.addActionListener(e -> save());
            JLabel d = new JLabel(i.description);
            Color dim = UIManager.getColor("Label.disabledForeground");
            if (dim != null) d.setForeground(dim);
            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
            row.add(cb, BorderLayout.WEST);
            row.add(d, BorderLayout.CENTER);
            row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, row.getPreferredSize().height + 4));
            boxes.add(cb);
            rows.add(row);
            list.add(row);
        }
        JTextField filter = new JTextField();
        filter.putClientProperty("JTextField.placeholderText", "Filter");
        filter.getDocument().addDocumentListener(new DocumentListener() {
            void apply() {
                String q = filter.getText().trim().toLowerCase();
                for (int i = 0; i < rows.size(); i++) {
                    Funbox.Info f = Funbox.ALL.get(i);
                    rows.get(i).setVisible(q.isEmpty() || f.name.toLowerCase().contains(q) || f.description.toLowerCase().contains(q));
                }
                list.revalidate();
                list.repaint();
            }
            public void insertUpdate(DocumentEvent e) { apply(); }
            public void removeUpdate(DocumentEvent e) { apply(); }
            public void changedUpdate(DocumentEvent e) { apply(); }
        });
        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.setBorder(BorderFactory.createEmptyBorder(8, 10, 0, 10));
        top.add(new JLabel("Funbox modifiers change the words, the rules or the look of the next test."), BorderLayout.NORTH);
        top.add(filter, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(list);
        sp.getVerticalScrollBar().setUnitIncrement(18);
        add(sp, BorderLayout.CENTER);
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton clear = new JButton("Clear all");
        clear.addActionListener(e -> { for (JCheckBox b : boxes) b.setSelected(false); save(); });
        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        south.add(clear);
        south.add(close);
        add(south, BorderLayout.SOUTH);
        setSize(720, 640);
        setLocationRelativeTo(parent);
    }

    private void save() {
        List<String> on = new ArrayList<>();
        for (JCheckBox b : boxes) if (b.isSelected()) on.add(b.getText());
        cfg.setList("funbox", on);
    }
}
