package com.typejunkey.ui;

import com.typejunkey.data.Content;
import com.typejunkey.data.Settings;
import com.typejunkey.util.Resources;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.nio.file.Files;

/** Lets the user hide bundled or folder content (languages, themes, layouts, quotes) without touching the jar. */
final class ContentDialog extends JDialog {
    ContentDialog(Component parent) {
        super(javax.swing.SwingUtilities.getWindowAncestor(parent), "Manage content", ModalityType.APPLICATION_MODAL);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Languages", page(Content.LANGUAGE, "languages"));
        tabs.addTab("Themes", page(Content.THEME, "themes"));
        tabs.addTab("Layouts", page(Content.LAYOUT, "layouts"));
        tabs.addTab("Quotes", page(Content.QUOTES, "quotes"));
        add(new JLabel("<html>&nbsp;Untick an entry to hide it from the app. Bundled files are never modified; "
                + "to remove them for good, delete them from the folder next to the app or from the jar.</html>"), BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton open = new JButton("Open content folder");
        open.addActionListener(e -> SettingsPanel.openContentFolder());
        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        south.add(open);
        south.add(close);
        add(south, BorderLayout.SOUTH);
        setSize(620, 520);
        setLocationRelativeTo(parent);
    }

    private JPanel page(String kind, String folder) {
        DefaultTableModel model = new DefaultTableModel(new Object[] {"Name", "Source", "Show"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return c == 2; }
            @Override public Class<?> getColumnClass(int c) { return c == 2 ? Boolean.class : String.class; }
        };
        for (Content.Item i : Content.items(kind, true)) {
            String src = i.fromFolder ? (i.overridesBundled ? "folder (overrides bundled)" : "folder") : "bundled";
            model.addRow(new Object[] {i.name, src, !i.hidden});
        }
        JTable table = new JTable(model);
        table.setRowHeight(24);
        table.getColumnModel().getColumn(2).setMaxWidth(70);
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        model.addTableModelListener(e -> {
            if (e.getColumn() != 2 || e.getFirstRow() < 0) return;
            String name = (String) model.getValueAt(e.getFirstRow(), 0);
            boolean show = (Boolean) model.getValueAt(e.getFirstRow(), 2);
            Settings.get().setHidden(kind, name, !show);
            Content.reload();
            Settings.get().fire();
        });
        JTextField filter = new JTextField();
        filter.putClientProperty("JTextField.placeholderText", "Filter");
        filter.getDocument().addDocumentListener(new DocumentListener() {
            void apply() { String t = filter.getText().trim(); sorter.setRowFilter(t.isEmpty() ? null : RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(t), 0)); }
            public void insertUpdate(DocumentEvent e) { apply(); }
            public void removeUpdate(DocumentEvent e) { apply(); }
            public void changedUpdate(DocumentEvent e) { apply(); }
        });
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        p.add(filter, BorderLayout.NORTH);
        p.add(new JScrollPane(table), BorderLayout.CENTER);
        JLabel foot = new JLabel(items(kind) + " items. Folder: " + Resources.side(folder));
        p.add(foot, BorderLayout.SOUTH);
        return p;
    }

    private static int items(String kind) { return Content.items(kind, true).size(); }
}
