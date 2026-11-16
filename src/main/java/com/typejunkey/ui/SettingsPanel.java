package com.typejunkey.ui;

import com.typejunkey.data.Content;
import com.typejunkey.data.Settings;
import com.typejunkey.util.Resources;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Settings tab: categories on the left, the options of the chosen category (or a search result) on the right. */
public final class SettingsPanel extends JPanel {
    private final Settings cfg = Settings.get();
    private final JList<String> cats;
    private final JPanel form = new JPanel(new GridBagLayout());
    private final JTextField search = new JTextField();
    private final Map<String, Runnable> syncers = new HashMap<>();
    private final Runnable manageContent;
    private boolean updating;

    public SettingsPanel(Runnable manageContent) {
        super(new BorderLayout());
        this.manageContent = manageContent;
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Settings.Def d : Settings.defs()) if (!d.cat.isEmpty()) names.add(d.cat);
        names.add(Settings.CONTENT);
        DefaultListModel<String> m = new DefaultListModel<>();
        for (String n : names) m.addElement(n);
        cats = new JList<>(m);
        cats.setSelectedIndex(0);
        cats.setFixedCellHeight(30);
        cats.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        cats.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) { search.setText(""); build(); } });

        JPanel left = new JPanel(new BorderLayout());
        search.putClientProperty("JTextField.placeholderText", "Search settings");
        search.putClientProperty("JTextField.showClearButton", true);
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { build(); }
            public void removeUpdate(DocumentEvent e) { build(); }
            public void changedUpdate(DocumentEvent e) { build(); }
        });
        JPanel sp = new JPanel(new BorderLayout());
        sp.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        sp.add(search, BorderLayout.CENTER);
        left.add(sp, BorderLayout.NORTH);
        left.add(new JScrollPane(cats), BorderLayout.CENTER);
        left.setPreferredSize(new Dimension(210, 100));
        left.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, UIManager.getColor("Separator.foreground")));

        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        add(left, BorderLayout.WEST);
        add(scroll, BorderLayout.CENTER);
        build();
        cfg.onChange(this::sync);
    }

    private void build() {
        form.removeAll();
        syncers.clear();
        String q = search.getText().trim().toLowerCase();
        String cat = cats.getSelectedValue();
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0; c.anchor = GridBagConstraints.NORTHWEST; c.insets = new Insets(8, 20, 8, 20);
        boolean any = false;
        if (q.isEmpty() && Settings.CONTENT.equals(cat)) {
            addContentPage(c);
            any = true;
        } else {
            for (Settings.Def d : Settings.defs()) {
                if (d.cat.isEmpty() || (d.type == Settings.Type.STYLE && !q.isEmpty())) continue;
                boolean show = q.isEmpty() ? d.cat.equals(cat) : (d.label.toLowerCase().contains(q) || d.tip.toLowerCase().contains(q) || d.cat.toLowerCase().contains(q));
                if (!show) continue;
                addRow(c, d);
                any = true;
            }
        }
        if (!any) {
            c.gridx = 0; c.gridwidth = 2;
            form.add(new JLabel("No settings match."), c);
            c.gridy++;
        }
        c.gridx = 0; c.gridwidth = 2; c.weightx = 1; c.weighty = 1;
        form.add(new JLabel(), c);
        form.revalidate();
        form.repaint();
    }

    private void addRow(GridBagConstraints c, Settings.Def d) {
        JPanel label = new JPanel(new BorderLayout());
        label.setOpaque(false);
        JLabel name = new JLabel(d.label);
        label.add(name, BorderLayout.NORTH);
        if (!d.tip.isEmpty()) {
            JLabel tip = new JLabel("<html><div style='width:330px'>" + d.tip + "</div></html>");
            Color dim = UIManager.getColor("Label.disabledForeground");
            if (dim != null) tip.setForeground(dim);
            tip.setFont(tip.getFont().deriveFont(tip.getFont().getSize2D() - 1f));
            label.add(tip, BorderLayout.CENTER);
        }
        JComponent editor = editor(d);
        c.gridx = 0; c.gridwidth = 1; c.weightx = 0;
        form.add(label, c);
        c.gridx = 1; c.weightx = 1; c.anchor = GridBagConstraints.NORTHWEST;
        form.add(editor, c);
        c.gridy++;
    }

    private JComponent editor(Settings.Def d) {
        switch (d.type) {
            case BOOL: {
                JCheckBox b = new JCheckBox();
                b.addActionListener(e -> { if (!updating) cfg.set(d.key, b.isSelected()); });
                syncers.put(d.key, () -> b.setSelected(cfg.bool(d.key)));
                syncers.get(d.key).run();
                return b;
            }
            case ENUM: return combo(d, java.util.Arrays.asList(d.options));
            case LAYOUT: {
                List<String> items = new ArrayList<>();
                items.add(d.key.equals("layout") ? "default" : "sync");
                items.addAll(Content.layouts());
                return combo(d, items);
            }
            case THEME: {
                List<String> items = new ArrayList<>();
                for (com.typejunkey.model.Theme t : Content.themes()) items.add(t.name);
                return combo(d, items);
            }
            case FILE: return fileEditor(d);
            case TEXT: return textEditor(d);
            case FUNBOX: {
                JButton b = new JButton();
                Runnable r = () -> b.setText(cfg.list("funbox").isEmpty() ? "Choose funbox\u2026" : cfg.list("funbox").size() + " active \u2014 change\u2026");
                b.addActionListener(e -> new FunboxDialog(this).setVisible(true));
                syncers.put(d.key, r);
                r.run();
                return b;
            }
            case LANGS: {
                JButton b = new JButton();
                Runnable r = () -> b.setText(cfg.list(d.key).size() + " languages \u2014 change\u2026");
                b.addActionListener(e -> new LanguagesDialog(this, d.key).setVisible(true));
                syncers.put(d.key, r);
                r.run();
                return b;
            }
            case FAMILY: {
                JComboBox<String> box = combo(d, Fonts.families());
                box.setPreferredSize(new Dimension(230, box.getPreferredSize().height));
                box.addActionListener(e -> {
                    if (updating) return;
                    for (Settings.Def s : Settings.defs()) {
                        if (s.type == Settings.Type.STYLE && d.key.equals(s.linked)) {
                            List<String> st = Fonts.styles(cfg.str(d.key));
                            if (!st.contains(cfg.str(s.key))) cfg.set(s.key, st.contains("Regular") ? "Regular" : st.get(0));
                        }
                    }
                });
                return box;
            }
            case STYLE: {
                JComboBox<String> box = new JComboBox<>();
                Runnable fill = () -> {
                    updating = true;
                    box.removeAllItems();
                    for (String s : Fonts.styles(cfg.str(d.linked))) box.addItem(s);
                    box.setSelectedItem(cfg.str(d.key));
                    updating = false;
                };
                box.addActionListener(e -> { if (!updating && box.getSelectedItem() != null) cfg.set(d.key, box.getSelectedItem()); });
                syncers.put(d.key, fill);
                fill.run();
                return box;
            }
            default: {
                JSpinner sp = new JSpinner(new SpinnerNumberModel(cfg.num(d.key), d.min, d.max, 1));
                sp.addChangeListener(e -> { if (!updating) cfg.set(d.key, sp.getValue()); });
                syncers.put(d.key, () -> sp.setValue(cfg.num(d.key)));
                return sp;
            }
        }
    }

    private JComponent textEditor(Settings.Def d) {
        JTextField f = new JTextField(cfg.str(d.key), 22);
        Runnable commit = () -> { if (!updating && !f.getText().equals(cfg.str(d.key))) cfg.set(d.key, f.getText().trim()); };
        f.addActionListener(e -> commit.run());
        f.addFocusListener(new java.awt.event.FocusAdapter() { @Override public void focusLost(java.awt.event.FocusEvent e) { commit.run(); } });
        syncers.put(d.key, () -> f.setText(cfg.str(d.key)));
        return f;
    }

    private JComponent fileEditor(Settings.Def d) {
        JTextField f = new JTextField(cfg.str(d.key), 22);
        JButton browse = new JButton("Browse\u2026");
        JButton clear = new JButton("Clear");
        Runnable commit = () -> { if (!updating && !f.getText().equals(cfg.str(d.key))) cfg.set(d.key, f.getText().trim()); };
        f.addActionListener(e -> commit.run());
        f.addFocusListener(new java.awt.event.FocusAdapter() { @Override public void focusLost(java.awt.event.FocusEvent e) { commit.run(); } });
        browse.addActionListener(e -> {
            javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Images", "png", "jpg", "jpeg", "gif", "bmp", "webp"));
            if (fc.showOpenDialog(this) == javax.swing.JFileChooser.APPROVE_OPTION) { f.setText(fc.getSelectedFile().getAbsolutePath()); commit.run(); }
        });
        clear.addActionListener(e -> { f.setText(""); commit.run(); });
        JPanel p = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
        p.setOpaque(false);
        p.add(f); p.add(browse); p.add(clear);
        syncers.put(d.key, () -> f.setText(cfg.str(d.key)));
        return p;
    }

    private JComboBox<String> combo(Settings.Def d, List<String> items) {
        JComboBox<String> box = new JComboBox<>(items.toArray(new String[0]));
        box.setMaximumRowCount(16);
        box.setSelectedItem(cfg.str(d.key));
        box.addActionListener(e -> { if (!updating && box.getSelectedItem() != null) cfg.set(d.key, box.getSelectedItem()); });
        syncers.put(d.key, () -> {
            box.setSelectedItem(cfg.str(d.key));
            if (d.key.equals("appTheme")) box.setEnabled(!cfg.bool("autoAppTheme")); // only used when auto theme is off
        });
        syncers.get(d.key).run();
        return box;
    }

    private void addContentPage(GridBagConstraints c) {
        c.gridx = 0; c.gridwidth = 2;
        JLabel info = new JLabel("<html><div style='width:520px'>Word lists, quotes, themes, keyboard layouts, fonts and tutorials are bundled with the app. "
                + "Folders next to the app add to them and override bundled files of the same name:<br><br>"
                + "<b>languages</b>, <b>quotes</b>, <b>themes</b>, <b>layouts</b>, <b>fonts</b>, <b>tutor</b><br><br>"
                + "Location: " + Resources.appDir() + "</div></html>");
        form.add(info, c);
        c.gridy++;
        JButton manage = new JButton("Manage languages, themes, layouts and quotes\u2026");
        manage.addActionListener(e -> manageContent.run());
        form.add(manage, c);
        c.gridy++;
        JButton open = new JButton("Open the content folder next to the app");
        open.addActionListener(e -> openContentFolder());
        form.add(open, c);
        c.gridy++;
    }

    static void openContentFolder() {
        try {
            for (String s : new String[] {"languages", "quotes", "themes", "layouts", "fonts", "tutor"}) Files.createDirectories(Resources.side(s));
            java.awt.Desktop.getDesktop().open(Resources.appDir().toFile());
        } catch (Exception ex) {
            javax.swing.JOptionPane.showMessageDialog(null, Resources.appDir().toString(), "Content folder", javax.swing.JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /** Refreshes the controls after a settings change made elsewhere. */
    public void sync() {
        updating = true;
        for (Runnable r : new ArrayList<>(syncers.values())) r.run();
        updating = false;
    }
}
