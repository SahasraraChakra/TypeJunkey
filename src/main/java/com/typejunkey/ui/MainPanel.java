package com.typejunkey.ui;

import com.typejunkey.data.Content;
import com.typejunkey.data.History;
import com.typejunkey.data.Settings;
import com.typejunkey.data.ThemeManager;
import com.typejunkey.engine.Funbox;
import com.typejunkey.model.Quote;
import com.typejunkey.model.TestResult;
import com.typejunkey.model.Theme;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Main content: horizontal tabs (the toolbar lives inside the Typing test tab), pages and status bar. */
public final class MainPanel extends JPanel {
    static final int TUTOR = 0, TEST = 1, RESULTS = 2, HISTORY = 3, BESTS = 4, THEMES = 5, SETTINGS = 6;

    private final Settings cfg = Settings.get();
    private final TypingPane pane = new TypingPane();
    private final KeymapView keymap = new KeymapView();
    private final ResultPanel resultPanel = new ResultPanel(this::newTest, this::repeatTest);
    private final HistoryPanel historyPanel = new HistoryPanel(this::historyCleared);
    private final BestsPanel bestsPanel = new BestsPanel();
    private final ThemesPanel themesPanel = new ThemesPanel();
    private final SettingsPanel settingsPanel = new SettingsPanel(this::manageContent);
    private final TutorPanel tutorPanel = new TutorPanel();
    private final Page typingPage = new Page();
    private final JPanel typingTab = new JPanel(new BorderLayout());
    private final JTabbedPane tabs = new JTabbedPane();
    private final JToolBar toolbar = new JToolBar();
    private final JPanel countPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
    private final JComboBox<String> langBox = new JComboBox<>();
    private final JLabel statusLeft = new JLabel(), statusRight = new JLabel();
    private final Map<String, JToggleButton> modeButtons = new LinkedHashMap<>();
    private JToggleButton punctButton, numsButton;
    private JCheckBoxMenuItem autoThemeItem, smoothItem;
    private final Map<String, JRadioButtonMenuItem> caretItems = new LinkedHashMap<>();
    private JMenuBar menuBar;
    private boolean updatingCombo;
    private String lastUiFont = "";

    public JMenuBar menuBar() { return menuBar; }

    /** Typing page: paints the optional background image behind the text. */
    private final class Page extends JPanel {
        Page() { super(new GridBagLayout()); }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Theme t = ThemeManager.active();
            if (!Backdrop.paint(g, this, t.bg)) { g.setColor(t.bg); g.fillRect(0, 0, getWidth(), getHeight()); }
            g.dispose();
        }
    }

    public MainPanel() {
        super(new BorderLayout());
        pane.setOnFinish(this::finished);
        pane.setOnRestart(this::newTest);
        pane.setOnPalette(this::openPalette);
        pane.setKeyHook(c -> { keymap.pressed(c); keymap.setNext(pane.engine().nextChar()); });

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0; gc.gridy = 0; gc.weightx = 1; gc.weighty = 1; gc.fill = GridBagConstraints.BOTH;
        typingPage.add(new JPanel() {{ setOpaque(false); }}, gc);
        gc.gridy = 1; gc.weighty = 0; gc.fill = GridBagConstraints.HORIZONTAL; gc.anchor = GridBagConstraints.CENTER;
        typingPage.add(pane, gc);
        gc.gridy = 2; gc.fill = GridBagConstraints.NONE;
        typingPage.add(keymap, gc);
        gc.gridy = 3; gc.weighty = 1; gc.fill = GridBagConstraints.BOTH;
        typingPage.add(new JPanel() {{ setOpaque(false); }}, gc);

        buildToolbar();
        typingTab.add(toolbar, BorderLayout.NORTH);
        typingTab.add(typingPage, BorderLayout.CENTER);

        tabs.addTab("Tutor", Icons.of(Icons.Kind.SCHOOL), tutorPanel, "Learn to type, step by step");
        tabs.addTab("Typing test", Icons.of(Icons.Kind.KEYBOARD), typingTab, "Typing test");
        tabs.addTab("Results", Icons.of(Icons.Kind.CHART), resultPanel, "Results of the last test");
        tabs.addTab("History", Icons.of(Icons.Kind.LIST), historyPanel, "All finished tests");
        tabs.addTab("Personal bests", Icons.of(Icons.Kind.TROPHY), bestsPanel, "Best result per test type");
        tabs.addTab("Themes", Icons.of(Icons.Kind.PALETTE), themesPanel, "Typing themes");
        tabs.addTab("Settings", Icons.of(Icons.Kind.GEAR), settingsPanel, "Settings");
        tabs.setSelectedIndex(TEST);
        tabs.addChangeListener(e -> tabChanged());

        add(tabs, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
        menuBar = buildMenu();

        cfg.onChange(this::settingsChanged);
        new Timer(120, e -> { keymap.setLayoutName(pane.displayLayoutName()); keymap.setNext(pane.engine().nextChar()); }).start();
        new Timer(15000, e -> { if (cfg.bool("autoSwitchTheme")) settingsChanged(); }).start();
        applyTheme();
        syncMenu();
        keymap.setLayoutName(pane.displayLayoutName());
        tabChanged();
        newTest();
    }

    // ------------------------------------------------------------------ actions
    private void newTest() {
        if (!pane.canRestart()) return;
        if (!cfg.str("randomTheme").equals("off")) { ThemeManager.randomize(); settingsChanged(); }
        pane.newTest();
        tabs.setSelectedIndex(TEST);
        updateStatus();
        SwingUtilities.invokeLater(pane::requestFocusInWindow);
    }

    private void repeatTest() {
        if (!pane.canRestart()) return;
        pane.repeatTest();
        tabs.setSelectedIndex(TEST);
        SwingUtilities.invokeLater(pane::requestFocusInWindow);
    }

    private void finished(TestResult r) {
        boolean pb = false;
        if (!r.failed) {
            pb = r.wpm > History.best(r.key());
            if (cfg.bool("resultSaving")) History.add(r);
        }
        resultPanel.show(r, pb && cfg.bool("resultSaving"));
        historyPanel.refresh();
        bestsPanel.refresh();
        updateStatus();
        tabs.setSelectedIndex(RESULTS);
    }

    private void historyCleared() {
        historyPanel.refresh();
        bestsPanel.refresh();
        updateStatus();
    }

    private void tabChanged() {
        switch (tabs.getSelectedIndex()) {
            case TUTOR: SwingUtilities.invokeLater(tutorPanel::focusLesson); break;
            case TEST: SwingUtilities.invokeLater(pane::requestFocusInWindow); break;
            case HISTORY: historyPanel.refresh(); break;
            case BESTS: bestsPanel.refresh(); break;
            case THEMES: themesPanel.refresh(); themesPanel.syncSelection(); break;
            default: break;
        }
    }

    private void manageContent() {
        new ContentDialog(this).setVisible(true);
        refreshLanguages();
        themesPanel.refresh();
        tutorPanel.refreshFromSettings();
    }

    // ------------------------------------------------------------------ settings and theme
    private void settingsChanged() {
        String uiFont = cfg.str("uiFontFamily") + "|" + cfg.str("uiFontStyle") + "|" + cfg.num("uiFontSize");
        AppTheme.apply(AppTheme.desiredDark());
        if (!uiFont.equals(lastUiFont)) {
            if (!lastUiFont.isEmpty()) AppTheme.refreshFonts();
            lastUiFont = uiFont;
        }
        applyTheme();
        syncMenu();
        syncToolbar();
        tutorPanel.refreshFromSettings();
        keymap.setLayoutName(pane.displayLayoutName());
        keymap.revalidate();
        pane.revalidate();
        pane.repaint();
        updateStatus();
    }

    private void applyTheme() {
        Theme t = ThemeManager.active();
        pane.setTheme(t);
        resultPanel.setTheme(t);
        tutorPanel.setTheme(t);
        keymap.setTheme(t);
        typingPage.repaint();
        repaint();
    }

    private void updateStatus() {
        String param = cfg.param();
        String left = cfg.mode + (param.isEmpty() ? "" : " " + param) + "  \u00b7  " + cfg.language.replace('_', ' ');
        if (cfg.punctuation) left += "  \u00b7  punctuation";
        if (cfg.numbers) left += "  \u00b7  numbers";
        List<String> fb = cfg.list("funbox");
        if (!fb.isEmpty()) left += "  \u00b7  funbox: " + String.join(", ", fb);
        if (!cfg.str("paceCaret").equals("off")) left += "  \u00b7  pace: " + cfg.str("paceCaret");
        statusLeft.setText(left);
        double best = History.best(TestResult.key(cfg.mode, param, cfg.language, cfg.punctuation, cfg.numbers));
        statusRight.setText("Best: " + (best > 0 ? Math.round(best) + " wpm" : "none yet")
                + "   |   Type theme: " + ThemeManager.active().display()
                + "   |   App theme: " + (AppTheme.isDark() ? "Darcula" : "IntelliJ light") + (cfg.bool("autoAppTheme") ? " (auto)" : ""));
    }

    private void editCustomText() {
        JTextArea area = new JTextArea(cfg.customText, 10, 52);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        int a = JOptionPane.showConfirmDialog(this, new JScrollPane(area), "Custom text \u2014 words to type",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (a == JOptionPane.OK_OPTION) {
            cfg.customText = area.getText();
            cfg.save();
            newTest();
        }
    }

    private void searchQuotes() {
        new QuoteSearchDialog(this, (Quote q) -> {
            cfg.mode = "quote";
            cfg.save();
            rebuildCounts();
            syncToolbar();
            pane.engine().forceQuote(q);
            newTest();
        }).setVisible(true);
    }

    private void setMode(String id) {
        cfg.mode = id;
        cfg.save();
        rebuildCounts();
        syncToolbar();
        if (id.equals("custom") && cfg.customText.isBlank()) editCustomText(); else newTest();
    }

    // ------------------------------------------------------------------ toolbar
    private void buildToolbar() {
        toolbar.setFloatable(false);
        toolbar.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));

        var restart = Tools.button(Icons.Kind.RESTART, null, "New test (Tab + Enter, F5)");
        restart.addActionListener(e -> newTest());
        toolbar.add(restart);
        toolbar.add(Tools.sep());

        punctButton = Tools.toggle(Icons.Kind.AT, null, "Punctuation: add capitals and punctuation marks");
        punctButton.addActionListener(e -> { cfg.punctuation = punctButton.isSelected(); cfg.save(); newTest(); });
        numsButton = Tools.toggle(Icons.Kind.HASH, null, "Numbers: mix numbers in with the words");
        numsButton.addActionListener(e -> { cfg.numbers = numsButton.isSelected(); cfg.save(); newTest(); });
        toolbar.add(punctButton);
        toolbar.add(numsButton);
        toolbar.add(Tools.sep());

        ButtonGroup modes = new ButtonGroup();
        String[][] ms = {
                {"time", "Time mode: type as much as you can before the timer ends"},
                {"words", "Words mode: type a fixed number of words"},
                {"quote", "Quote mode: type a real quotation"},
                {"zen", "Zen mode: type freely, finish with Shift + Enter"},
                {"custom", "Custom mode: type your own text"}};
        Icons.Kind[] mk = {Icons.Kind.CLOCK, Icons.Kind.WORDS, Icons.Kind.QUOTE, Icons.Kind.ZEN, Icons.Kind.CUSTOM};
        for (int i = 0; i < ms.length; i++) {
            final String id = ms[i][0];
            JToggleButton b = Tools.toggle(mk[i], null, ms[i][1]);
            b.addActionListener(e -> setMode(id));
            modes.add(b);
            modeButtons.put(id, b);
            toolbar.add(b);
        }
        toolbar.add(Tools.sep());
        countPanel.setOpaque(false);
        toolbar.add(countPanel);
        rebuildCounts();
        toolbar.add(javax.swing.Box.createHorizontalGlue());

        var funbox = Tools.button(Icons.Kind.FLASK, null, "Funbox: fun modifiers for the next test");
        funbox.addActionListener(e -> { new FunboxDialog(this).setVisible(true); newTest(); });
        var pace = Tools.button(Icons.Kind.GHOST, null, "Pace caret: a second caret running at a steady speed");
        pace.addActionListener(e -> paceMenu().show(pace, 0, pace.getHeight()));
        toolbar.add(funbox);
        toolbar.add(pace);
        toolbar.add(Tools.sep());

        langBox.setMaximumRowCount(18);
        langBox.setToolTipText("Language / word list");
        langBox.setFocusable(false);
        langBox.setPreferredSize(new Dimension(190, 28));
        langBox.setMaximumSize(new Dimension(190, 30));
        langBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                return super.getListCellRendererComponent(l, String.valueOf(v).replace('_', ' '), i, s, f);
            }
        });
        langBox.addActionListener(e -> {
            if (updatingCombo) return;
            String sel = (String) langBox.getSelectedItem();
            if (sel != null && !sel.equals(cfg.language)) { cfg.language = sel; cfg.save(); newTest(); }
        });
        refreshLanguages();
        toolbar.add(langBox);
        syncToolbar();
    }

    private JPopupMenu paceMenu() {
        JPopupMenu m = new JPopupMenu();
        ButtonGroup g = new ButtonGroup();
        String[][] opts = {{"off", "Off"}, {"average", "Average of your last 10 tests"}, {"pb", "Personal best"}, {"last", "Last result"},
                {"daily", "Today's average"}, {"custom", "Custom speed (" + cfg.num("paceCaretCustomSpeed") + " wpm, see Settings)"}};
        for (String[] o : opts) {
            JRadioButtonMenuItem it = new JRadioButtonMenuItem(o[1], cfg.str("paceCaret").equals(o[0]));
            it.addActionListener(e -> { cfg.set("paceCaret", o[0]); newTest(); });
            g.add(it);
            m.add(it);
        }
        return m;
    }

    private void syncToolbar() {
        if (punctButton == null) return;
        punctButton.setSelected(cfg.punctuation);
        numsButton.setSelected(cfg.numbers);
        JToggleButton b = modeButtons.get(cfg.mode);
        if (b != null) b.setSelected(true);
    }

    private void refreshLanguages() {
        updatingCombo = true;
        langBox.removeAllItems();
        List<String> langs = Content.languages();
        for (String l : langs) langBox.addItem(l);
        if (!langs.contains(cfg.language)) {
            cfg.language = langs.contains("english") ? "english" : langs.get(0);
            cfg.save();
        }
        langBox.setSelectedItem(cfg.language);
        updatingCombo = false;
    }

    private void rebuildCounts() {
        countPanel.removeAll();
        ButtonGroup g = new ButtonGroup();
        switch (cfg.mode) {
            case "time":
                for (int v : new int[] {15, 30, 60, 120}) count(g, "" + v, "Time limit: " + v + " seconds", cfg.time == v, () -> cfg.time = v);
                break;
            case "words":
                for (int v : new int[] {10, 25, 50, 100}) count(g, "" + v, "Word count: " + v + " words", cfg.words == v, () -> cfg.words = v);
                break;
            case "quote": {
                String[] lbl = {"all", "S", "M", "L", "XL"};
                String[] tip = {"Any quote length", "Short quotes", "Medium quotes", "Long quotes", "Very long quotes"};
                for (int i = 0; i < lbl.length; i++) {
                    final int q = i - 1;
                    count(g, lbl[i], tip[i], cfg.quoteLen == q, () -> cfg.quoteLen = q);
                }
                var search = Tools.button(Icons.Kind.SEARCH, null, "Search quotes by text or source");
                search.addActionListener(e -> searchQuotes());
                countPanel.add(search);
                break;
            }
            case "custom": {
                var edit = Tools.button(Icons.Kind.CUSTOM, null, "Edit custom text\u2026");
                edit.addActionListener(e -> editCustomText());
                countPanel.add(edit);
                break;
            }
            default: break;
        }
        countPanel.revalidate();
        countPanel.repaint();
    }

    private void count(ButtonGroup g, String label, String tip, boolean selected, Runnable set) {
        JToggleButton b = Tools.toggle(null, label, tip);
        b.setSelected(selected);
        b.addActionListener(e -> { set.run(); cfg.save(); newTest(); });
        g.add(b);
        countPanel.add(b);
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, javax.swing.UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        bar.add(statusLeft, BorderLayout.WEST);
        bar.add(statusRight, BorderLayout.EAST);
        return bar;
    }

    // ------------------------------------------------------------------ command palette
    private void openPalette() {
        new PaletteDialog(this, commands()).setVisible(true);
    }

    List<PaletteDialog.Cmd> commands() {
        List<PaletteDialog.Cmd> l = new ArrayList<>();
        l.add(new PaletteDialog.Cmd("New test", this::newTest));
        l.add(new PaletteDialog.Cmd("Repeat test", this::repeatTest));
        String[] tabNames = {"Tutor", "Typing test", "Results", "History", "Personal bests", "Themes", "Settings"};
        for (int i = 0; i < tabNames.length; i++) { final int idx = i; l.add(new PaletteDialog.Cmd("Go to tab: " + tabNames[i], () -> tabs.setSelectedIndex(idx))); }
        l.add(new PaletteDialog.Cmd("Manage content (languages, themes, layouts, quotes)", this::manageContent));
        l.add(new PaletteDialog.Cmd("Edit custom text", this::editCustomText));
        l.add(new PaletteDialog.Cmd("Search quotes", this::searchQuotes));
        l.add(new PaletteDialog.Cmd("Funbox: choose modifiers", () -> { new FunboxDialog(this).setVisible(true); newTest(); }));
        l.add(new PaletteDialog.Cmd("Funbox: clear all", () -> { cfg.setList("funbox", new ArrayList<>()); newTest(); }));
        for (Funbox.Info f : Funbox.ALL) {
            l.add(new PaletteDialog.Cmd("Funbox: toggle " + f.name + " \u2014 " + f.description, () -> {
                List<String> on = cfg.list("funbox");
                if (on.contains(f.name)) on.remove(f.name); else on.add(f.name);
                cfg.setList("funbox", on);
                newTest();
            }));
        }
        for (String m : new String[] {"time", "words", "quote", "zen", "custom"}) l.add(new PaletteDialog.Cmd("Mode: " + m, () -> setMode(m)));
        for (int v : new int[] {15, 30, 60, 120}) l.add(new PaletteDialog.Cmd("Time: " + v + " seconds", () -> { cfg.mode = "time"; cfg.time = v; cfg.save(); rebuildCounts(); syncToolbar(); newTest(); }));
        for (int v : new int[] {10, 25, 50, 100}) l.add(new PaletteDialog.Cmd("Words: " + v, () -> { cfg.mode = "words"; cfg.words = v; cfg.save(); rebuildCounts(); syncToolbar(); newTest(); }));
        l.add(new PaletteDialog.Cmd("Toggle punctuation", () -> { cfg.punctuation = !cfg.punctuation; cfg.save(); syncToolbar(); newTest(); }));
        l.add(new PaletteDialog.Cmd("Toggle numbers", () -> { cfg.numbers = !cfg.numbers; cfg.save(); syncToolbar(); newTest(); }));
        for (String lang : Content.languages()) l.add(new PaletteDialog.Cmd("Language: " + lang.replace('_', ' '), () -> { cfg.language = lang; cfg.save(); refreshLanguages(); newTest(); }));
        for (Theme t : Content.themes()) l.add(new PaletteDialog.Cmd("Theme: " + t.display(), () -> {
            cfg.setQuiet("customTheme", false); cfg.theme = t.name; cfg.save(); ThemeManager.clearOverride(); cfg.fire(); }));
        for (Settings.Def d : Settings.defs()) {
            if (d.cat.isEmpty()) continue;
            switch (d.type) {
                case BOOL:
                    l.add(new PaletteDialog.Cmd("Toggle: " + d.label + " (" + d.cat + ")", () -> cfg.set(d.key, !cfg.bool(d.key))));
                    break;
                case ENUM:
                    for (String o : d.options) l.add(new PaletteDialog.Cmd(d.label + ": " + o + " (" + d.cat + ")", () -> cfg.set(d.key, o)));
                    break;
                case LAYOUT: {
                    String first = d.key.equals("layout") ? "default" : "sync";
                    l.add(new PaletteDialog.Cmd(d.label + ": " + first, () -> cfg.set(d.key, first)));
                    for (String n : Content.layouts()) l.add(new PaletteDialog.Cmd(d.label + ": " + n, () -> cfg.set(d.key, n)));
                    break;
                }
                default: break;
            }
        }
        return l;
    }

    // ------------------------------------------------------------------ menu
    private JMenuBar buildMenu() {
        JMenuBar mb = new JMenuBar();

        JMenu file = new JMenu("File");
        JMenuItem nt = new JMenuItem("New test");
        nt.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
        nt.addActionListener(e -> newTest());
        JMenuItem rt = new JMenuItem("Repeat test");
        rt.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0));
        rt.addActionListener(e -> repeatTest());
        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(e -> { java.awt.Window w = SwingUtilities.getWindowAncestor(this); if (w != null) w.dispose(); });
        file.add(nt); file.add(rt); file.addSeparator(); file.add(exit);

        JMenu view = new JMenu("View");
        autoThemeItem = new JCheckBoxMenuItem("Auto Theme App With Type Theme");
        autoThemeItem.addActionListener(e -> cfg.set("autoAppTheme", autoThemeItem.isSelected()));
        smoothItem = new JCheckBoxMenuItem("Smooth caret");
        smoothItem.addActionListener(e -> cfg.set("smoothCaret", smoothItem.isSelected() ? "medium" : "off"));
        JMenu caret = new JMenu("Caret style");
        ButtonGroup cg = new ButtonGroup();
        for (String s : new String[] {"off", "default", "block", "outline", "underline", "carrot", "banana", "monkey"}) {
            JRadioButtonMenuItem it = new JRadioButtonMenuItem(s);
            it.addActionListener(e -> cfg.set("caretStyle", s));
            cg.add(it);
            caret.add(it);
            caretItems.put(s, it);
        }
        JMenuItem funboxItem = new JMenuItem("Funbox\u2026");
        funboxItem.addActionListener(e -> { new FunboxDialog(this).setVisible(true); newTest(); });
        JMenuItem themesItem = new JMenuItem("Themes\u2026");
        themesItem.addActionListener(e -> tabs.setSelectedIndex(THEMES));
        view.add(autoThemeItem); view.addSeparator(); view.add(smoothItem); view.add(caret);
        view.addSeparator(); view.add(funboxItem); view.add(themesItem);

        JMenu tools = new JMenu("Tools");
        JMenuItem palette = new JMenuItem("Command palette\u2026");
        palette.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, java.awt.event.InputEvent.CTRL_DOWN_MASK | java.awt.event.InputEvent.SHIFT_DOWN_MASK));
        palette.addActionListener(e -> openPalette());
        JMenuItem custom = new JMenuItem("Edit custom text\u2026");
        custom.addActionListener(e -> editCustomText());
        JMenuItem manage = new JMenuItem("Manage content\u2026");
        manage.addActionListener(e -> manageContent());
        JMenuItem content = new JMenuItem("Open content folder");
        content.addActionListener(e -> SettingsPanel.openContentFolder());
        JMenuItem folder = new JMenuItem("Open data folder");
        folder.addActionListener(e -> {
            try {
                java.nio.file.Files.createDirectories(Settings.DIR);
                java.awt.Desktop.getDesktop().open(Settings.DIR.toFile());
            } catch (IOException | UnsupportedOperationException ex) {
                JOptionPane.showMessageDialog(this, Settings.DIR.toString(), "Data folder", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        JMenuItem clear = new JMenuItem("Clear history\u2026");
        clear.addActionListener(e -> tabs.setSelectedIndex(HISTORY));
        tools.add(palette); tools.addSeparator(); tools.add(custom); tools.add(manage); tools.addSeparator();
        tools.add(content); tools.add(folder); tools.addSeparator(); tools.add(clear);

        JMenu help = new JMenu("Help");
        JMenuItem keys = new JMenuItem("Keyboard shortcuts");
        keys.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "Tab + Enter  \u2014  restart test or tutorial (or the single quick-restart key you chose)\nCtrl + Shift + P (or Esc)  \u2014  command palette\n"
                        + "F5  \u2014  new test\nF6  \u2014  repeat the same words\n"
                        + "Ctrl + Backspace  \u2014  delete the whole word\nShift + Enter  \u2014  finish a zen test",
                "Keyboard shortcuts", JOptionPane.INFORMATION_MESSAGE));
        JMenuItem about = new JMenuItem("About TypeJunKey");
        about.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "TypeJunKey 0.3\nA Java desktop port of Monkeytype 26.43 with a typing tutor.\n\nWord lists, quotes, themes and layouts come from Monkeytype\n"
                        + "(github.com/monkeytypegame/monkeytype), GPL-3.0.\nFonts: Inter and Cascadia Code (SIL Open Font License).\nLook and feel: FlatLaf (Apache 2.0).",
                "About", JOptionPane.INFORMATION_MESSAGE));
        help.add(keys); help.addSeparator(); help.add(about);

        mb.add(file); mb.add(view); mb.add(tools); mb.add(help);
        return mb;
    }

    private void syncMenu() {
        if (autoThemeItem == null) return;
        autoThemeItem.setSelected(cfg.bool("autoAppTheme"));
        smoothItem.setSelected(!cfg.str("smoothCaret").equals("off"));
        JRadioButtonMenuItem it = caretItems.get(cfg.str("caretStyle"));
        if (it != null) it.setSelected(true);
    }

    static BufferedImage appIcon() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, new Color(0x4a78b0), 0, 32, new Color(0x1f3f69)));
        g.fillRoundRect(0, 0, 32, 32, 8, 8);
        g.scale(1.5, 1.5);
        new Icons(Icons.Kind.KEYBOARD, Color.WHITE).paintIcon(null, g, 2, 2);
        g.dispose();
        return img;
    }
}
