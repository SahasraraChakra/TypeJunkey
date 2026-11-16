package com.typejunkey.ui;

import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.util.List;

/** Command palette: type to filter every action and setting, Enter to run. */
final class PaletteDialog extends JDialog {
    static final class Cmd {
        final String label;
        final Runnable run;
        Cmd(String label, Runnable run) { this.label = label; this.run = run; }
        @Override public String toString() { return label; }
    }

    PaletteDialog(Component parent, List<Cmd> cmds) {
        super(javax.swing.SwingUtilities.getWindowAncestor(parent), "Command palette", ModalityType.APPLICATION_MODAL);
        DefaultListModel<Cmd> model = new DefaultListModel<>();
        JList<Cmd> list = new JList<>(model);
        JTextField field = new JTextField();
        field.putClientProperty("JTextField.placeholderText", "Type a command, setting or value\u2026");
        Runnable fill = () -> {
            String q = field.getText().trim().toLowerCase();
            model.clear();
            int n = 0;
            for (Cmd c : cmds) {
                boolean ok = true;
                for (String part : q.split("\\s+")) if (!part.isEmpty() && !c.label.toLowerCase().contains(part)) { ok = false; break; }
                if (ok) { model.addElement(c); if (++n >= 200) break; }
            }
            if (!model.isEmpty()) list.setSelectedIndex(0);
        };
        field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { fill.run(); }
            public void removeUpdate(DocumentEvent e) { fill.run(); }
            public void changedUpdate(DocumentEvent e) { fill.run(); }
        });
        Runnable run = () -> {
            Cmd c = list.getSelectedValue();
            if (c != null) { dispose(); javax.swing.SwingUtilities.invokeLater(c.run); }
        };
        field.addActionListener(e -> run.run());
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { if (e.getClickCount() >= 1) run.run(); }
        });
        field.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "next");
        field.getActionMap().put("next", new javax.swing.AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) { int i = list.getSelectedIndex(); if (i + 1 < model.size()) list.setSelectedIndex(i + 1); list.ensureIndexIsVisible(list.getSelectedIndex()); }
        });
        field.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "prev");
        field.getActionMap().put("prev", new javax.swing.AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) { int i = list.getSelectedIndex(); if (i > 0) list.setSelectedIndex(i - 1); list.ensureIndexIsVisible(list.getSelectedIndex()); }
        });
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        add(field, BorderLayout.NORTH);
        add(new JScrollPane(list), BorderLayout.CENTER);
        fill.run();
        setSize(560, 440);
        setLocationRelativeTo(parent);
    }
}
