package com.typejunkey.ui;

import com.typejunkey.data.Content;
import com.typejunkey.data.Settings;
import com.typejunkey.model.Quote;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.function.Consumer;

/** Search the quotes of the current language and pick one to type. */
final class QuoteSearchDialog extends JDialog {
    QuoteSearchDialog(Component parent, Consumer<Quote> onPick) {
        super(javax.swing.SwingUtilities.getWindowAncestor(parent), "Search quotes", ModalityType.APPLICATION_MODAL);
        DefaultListModel<Quote> model = new DefaultListModel<>();
        JList<Quote> list = new JList<>(model);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                Quote q = (Quote) v;
                String t = q.text.length() > 110 ? q.text.substring(0, 108) + "\u2026" : q.text;
                return super.getListCellRendererComponent(l, "<html>" + t.replace("<", "&lt;") + " <font color='gray'>\u2014 " + q.source.replace("<", "&lt;") + "</font></html>", i, s, f);
            }
        });
        JTextField field = new JTextField();
        field.putClientProperty("JTextField.placeholderText", "Search text or source\u2026");
        Runnable fill = () -> {
            String q = field.getText().trim().toLowerCase();
            model.clear();
            int n = 0;
            for (Quote x : Content.quotes(Settings.get().language).quotes) {
                if (q.isEmpty() || x.text.toLowerCase().contains(q) || x.source.toLowerCase().contains(q)) { model.addElement(x); if (++n >= 300) break; }
            }
        };
        field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { fill.run(); }
            public void removeUpdate(DocumentEvent e) { fill.run(); }
            public void changedUpdate(DocumentEvent e) { fill.run(); }
        });
        Runnable pick = () -> { Quote q = list.getSelectedValue(); if (q != null) { dispose(); onPick.accept(q); } };
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { if (e.getClickCount() == 2) pick.run(); }
        });
        field.addActionListener(e -> { if (list.getSelectedIndex() < 0 && !model.isEmpty()) list.setSelectedIndex(0); pick.run(); });
        add(field, BorderLayout.NORTH);
        add(new JScrollPane(list), BorderLayout.CENTER);
        fill.run();
        setSize(760, 480);
        setLocationRelativeTo(parent);
    }
}
