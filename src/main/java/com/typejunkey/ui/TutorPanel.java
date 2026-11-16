package com.typejunkey.ui;

import com.typejunkey.data.Content;
import com.typejunkey.data.Settings;
import com.typejunkey.model.Theme;
import com.typejunkey.tutor.FingerMap;
import com.typejunkey.tutor.Lesson;
import com.typejunkey.tutor.TutorLibrary;
import com.typejunkey.tutor.TutorProgress;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JList;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The Tutor tab: lesson picker, lesson text that flexes with the window, and hands plus keyboard at the bottom. */
public final class TutorPanel extends JPanel {
    private static final Map<String, String> FINGER_NAMES = Map.of(
            "lp", "left little finger", "lr", "left ring finger", "lm", "left middle finger", "li", "left index finger",
            "lt", "left thumb", "ri", "right index finger", "rm", "right middle finger", "rr", "right ring finger",
            "rp", "right little finger", "rt", "right thumb");

    private final Settings cfg = Settings.get();
    private final JComboBox<String> levelBox = new JComboBox<>();
    private final JComboBox<Lesson> lessonBox = new JComboBox<>();
    private final JComboBox<String> layoutBox = new JComboBox<>();
    private final LessonView view = new LessonView();
    private final TutorStrip strip = new TutorStrip();
    private Theme theme = Theme.fallback();
    private boolean updating;

    public TutorPanel() {
        super(new BorderLayout());
        add(buildControls(), BorderLayout.NORTH);
        add(view, BorderLayout.CENTER);
        add(strip, BorderLayout.SOUTH);
        strip.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                if (strip.getPreferredSize().height != strip.getHeight()) revalidate();
            }
        });
        reloadLevels();
        refreshFromSettings();
    }

    // ------------------------------------------------------------------ controls
    private JPanel buildControls() {
        JPanel bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.X_AXIS));
        bar.setBorder(new EmptyBorder(6, 10, 6, 10));
        levelBox.setToolTipText("Tutorial level");
        lessonBox.setToolTipText("Tutorial");
        layoutBox.setToolTipText("Keyboard layout (also used by the typing test)");
        levelBox.setFocusable(false); lessonBox.setFocusable(false); layoutBox.setFocusable(false);
        levelBox.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                String t = String.valueOf(v);
                return super.getListCellRendererComponent(l, t.isEmpty() ? t : Character.toUpperCase(t.charAt(0)) + t.substring(1), i, s, f);
            }
        });
        lessonBox.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                String t = String.valueOf(v);
                if (v instanceof Lesson && TutorProgress.completed(((Lesson) v).id())) t = "\u2713  " + t;
                return super.getListCellRendererComponent(l, t, i, s, f);
            }
        });
        lessonBox.setPrototypeDisplayValue(null);
        levelBox.setMaximumRowCount(10);
        lessonBox.setMaximumRowCount(16);
        layoutBox.setMaximumRowCount(18);
        levelBox.addActionListener(e -> { if (!updating) { cfg.tutorLevel = (String) levelBox.getSelectedItem(); cfg.tutorLesson = ""; cfg.save(); fillLessons(); } });
        lessonBox.addActionListener(e -> { if (!updating && lessonBox.getSelectedItem() != null) selectLesson((Lesson) lessonBox.getSelectedItem()); });
        layoutBox.addActionListener(e -> { if (!updating && layoutBox.getSelectedItem() != null) cfg.set("keymapLayout", layoutBox.getSelectedItem()); });

        JButton prev = Tools.button(Icons.Kind.PREV, null, "Previous tutorial");
        JButton next = Tools.button(Icons.Kind.NEXT, null, "Next tutorial");
        JButton folder = Tools.button(Icons.Kind.FOLDER, null, "Tutor folder: choose or open the folder that holds tutorial files");
        JButton restart = Tools.button(Icons.Kind.RESTART, null, "Restart this tutorial");
        prev.addActionListener(e -> step(-1));
        next.addActionListener(e -> step(1));
        restart.addActionListener(e -> view.restart());
        folder.addActionListener(e -> folderMenu().show(folder, 0, folder.getHeight()));

        bar.add(new JLabel("Level")); bar.add(Box.createHorizontalStrut(6));
        bar.add(levelBox); bar.add(Box.createHorizontalStrut(14));
        bar.add(new JLabel("Tutorial")); bar.add(Box.createHorizontalStrut(6));
        lessonBox.setPreferredSize(new Dimension(280, lessonBox.getPreferredSize().height));
        lessonBox.setMaximumSize(new Dimension(340, 40));
        levelBox.setMaximumSize(new Dimension(150, 40));
        bar.add(lessonBox); bar.add(Box.createHorizontalStrut(4));
        bar.add(prev); bar.add(next);
        bar.add(Box.createHorizontalGlue());
        bar.add(new JLabel("Layout")); bar.add(Box.createHorizontalStrut(6));
        layoutBox.setMaximumSize(new Dimension(190, 40));
        bar.add(layoutBox); bar.add(Box.createHorizontalStrut(4));
        bar.add(folder); bar.add(restart);
        return bar;
    }

    private JPopupMenu folderMenu() {
        JPopupMenu m = new JPopupMenu();
        JMenuItem cur = new JMenuItem("Folder: " + TutorLibrary.folder());
        cur.setEnabled(false);
        m.add(cur);
        JMenuItem open = new JMenuItem("Open tutor folder");
        open.addActionListener(e -> {
            try {
                Files.createDirectories(TutorLibrary.folder());
                java.awt.Desktop.getDesktop().open(TutorLibrary.folder().toFile());
            } catch (IOException | UnsupportedOperationException | SecurityException ex) {
                javax.swing.JOptionPane.showMessageDialog(this, TutorLibrary.folder().toString(), "Tutor folder", javax.swing.JOptionPane.INFORMATION_MESSAGE);
            }
        });
        JMenuItem choose = new JMenuItem("Choose tutor folder\u2026");
        choose.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(TutorLibrary.folder().toFile());
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.setDialogTitle("Folder with level subfolders (basic, beginner, intermediary, advanced, professional)");
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                cfg.tutorFolder = fc.getSelectedFile().getAbsolutePath();
                cfg.save();
                reloadLevels();
            }
        });
        JMenuItem def = new JMenuItem("Use the default folder (next to the app)");
        def.addActionListener(e -> { cfg.tutorFolder = ""; cfg.save(); reloadLevels(); });
        JMenuItem reload = new JMenuItem("Reload tutorials");
        reload.addActionListener(e -> reloadLevels());
        m.add(open); m.add(choose); m.add(def); m.addSeparator(); m.add(reload);
        return m;
    }

    // ------------------------------------------------------------------ data
    public void reloadLevels() {
        TutorLibrary.reload();
        updating = true;
        levelBox.removeAllItems();
        for (String l : TutorLibrary.levels()) levelBox.addItem(l);
        if (levelBox.getItemCount() > 0) {
            levelBox.setSelectedItem(cfg.tutorLevel);
            if (levelBox.getSelectedItem() == null) levelBox.setSelectedIndex(0);
        }
        updating = false;
        fillLessons();
    }

    private void fillLessons() {
        updating = true;
        lessonBox.removeAllItems();
        String level = (String) levelBox.getSelectedItem();
        Lesson pick = null;
        if (level != null) {
            for (Lesson l : TutorLibrary.lessons(level)) {
                lessonBox.addItem(l);
                if (l.id().equals(cfg.tutorLesson)) pick = l;
            }
            if (pick == null && lessonBox.getItemCount() > 0) pick = lessonBox.getItemAt(0);
        }
        updating = false;
        if (pick != null) { lessonBox.setSelectedItem(pick); selectLesson(pick); } else view.setLesson(null);
    }

    private void selectLesson(Lesson l) {
        cfg.tutorLevel = l.level;
        cfg.tutorLesson = l.id();
        cfg.save();
        view.setLesson(l);
        view.requestFocusInWindow();
    }

    private void step(int d) {
        int i = lessonBox.getSelectedIndex() + d;
        if (i >= 0 && i < lessonBox.getItemCount()) lessonBox.setSelectedIndex(i);
        else if (d > 0) {
            int li = levelBox.getSelectedIndex() + 1;
            if (li < levelBox.getItemCount()) levelBox.setSelectedIndex(li);
        } else if (d < 0 && levelBox.getSelectedIndex() > 0) {
            levelBox.setSelectedIndex(levelBox.getSelectedIndex() - 1);
            if (lessonBox.getItemCount() > 0) lessonBox.setSelectedIndex(lessonBox.getItemCount() - 1);
        }
    }

    // ------------------------------------------------------------------ settings and theme
    public void setTheme(Theme t) {
        theme = t;
        view.repaint();
        strip.setTheme(t);
        setBackground(t.bg);
    }

    /** Pulls the layout and the hand setting from Settings (call after any settings change). */
    public void refreshFromSettings() {
        updating = true;
        List<String> layouts = Content.layouts();
        boolean same = layoutBox.getItemCount() == layouts.size();
        if (same) for (int i = 0; i < layouts.size(); i++) if (!layouts.get(i).equals(layoutBox.getItemAt(i))) { same = false; break; }
        if (!same) {
            layoutBox.removeAllItems();
            for (String l : layouts) layoutBox.addItem(l);
        }
        layoutBox.setSelectedItem(effective());
        updating = false;
        strip.setLayout(Content.layout(effective()));
        strip.setShowHands(cfg.bool("tutorShowHands"));
        view.refreshTarget();
        view.repaint();
    }

    /** Layout drawn by the tutor: the chosen keymap layout, or the emulated layout, or qwerty. */
    static String effective() {
        Settings c = Settings.get();
        String k = c.str("keymapLayout");
        if (!k.equals("sync")) return k;
        String l = c.str("layout");
        return l.equals("default") ? "qwerty" : l;
    }

    public void focusLesson() { view.requestFocusInWindow(); }

    // ------------------------------------------------------------------ lesson view
    private final class LessonView extends JComponent {
        private Lesson lesson;
        private int pos, correct, wrong;
        private long startNs;
        private boolean finished;
        private long flashUntil;
        private double lastWpm, lastAcc;
        private boolean tabPending;
        private final Timer timer = new Timer(120, e -> { if (startNs != 0 && !finished) repaint(); else if (System.currentTimeMillis() < flashUntil + 150) repaint(); });

        LessonView() {
            setFocusable(true);
            setFocusTraversalKeysEnabled(false);
            setOpaque(true);
            timer.start();
            addMouseListener(new MouseAdapter() { @Override public void mousePressed(MouseEvent e) { requestFocusInWindow(); } });
            addFocusListener(new java.awt.event.FocusAdapter() {
                @Override public void focusGained(java.awt.event.FocusEvent e) { repaint(); }
                @Override public void focusLost(java.awt.event.FocusEvent e) { repaint(); }
            });
            addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent e) {
                    int k = e.getKeyCode();
                    if (k == KeyEvent.VK_TAB) { tabPending = true; e.consume(); return; }
                    if (k == KeyEvent.VK_ENTER) {
                        if (tabPending) restart(); else if (finished) step(1);
                        tabPending = false; e.consume(); return;
                    }
                    if (k == KeyEvent.VK_ESCAPE) { restart(); e.consume(); return; }
                    if (k != KeyEvent.VK_SHIFT && k != KeyEvent.VK_CONTROL && k != KeyEvent.VK_ALT) tabPending = false;
                }
                @Override public void keyTyped(KeyEvent e) {
                    char c = e.getKeyChar();
                    if (lesson == null || finished) return;
                    if (e.isControlDown() && !e.isAltDown()) return;
                    if (c < 32 || c == 127) return;
                    type(com.typejunkey.engine.Emulator.map(cfg.str("layout"), c));
                }
            });
        }

        void setLesson(Lesson l) { lesson = l; restart(); }

        void restart() {
            pos = 0; correct = 0; wrong = 0; startNs = 0; finished = false; flashUntil = 0;
            refreshTarget();
            repaint();
        }

        void refreshTarget() {
            if (lesson == null || finished || pos >= lesson.exercise.length()) { strip.setTarget(null); return; }
            strip.setTarget(FingerMap.find(Content.layout(effective()), lesson.exercise.charAt(pos)));
        }

        private void type(char c) {
            if (startNs == 0) startNs = System.nanoTime();
            if (c == lesson.exercise.charAt(pos)) {
                pos++; correct++;
                if (pos >= lesson.exercise.length()) finish();
            } else {
                wrong++;
                flashUntil = System.currentTimeMillis() + 220;
            }
            refreshTarget();
            repaint();
        }

        private double minutes() { return startNs == 0 ? 0 : Math.max((System.nanoTime() - startNs) / 6e10, 1e-6); }
        private double wpm() { return startNs == 0 ? 0 : (correct / 5.0) / Math.max(minutes(), 0.05 / 60); }
        private double acc() { return correct + wrong == 0 ? 100 : 100.0 * correct / (correct + wrong); }

        private void finish() {
            finished = true;
            lastWpm = wpm();
            lastAcc = acc();
            TutorProgress.record(lesson.id(), lastWpm, lastAcc);
            strip.setTarget(null);
        }

        private List<int[]> wrap(String text, FontMetrics fm, int maxW) {
            List<int[]> lines = new ArrayList<>();
            int start = 0, width = 0, i = 0, n = text.length();
            while (i < n) {
                int j = i;
                while (j < n && text.charAt(j) != ' ') j++;
                while (j < n && text.charAt(j) == ' ') j++;
                int w = 0;
                for (int k = i; k < j; k++) w += fm.charWidth(text.charAt(k));
                if (width > 0 && width + w - fm.charWidth(' ') > maxW) { lines.add(new int[] {start, i}); start = i; width = 0; }
                width += w;
                i = j;
            }
            if (start < n) lines.add(new int[] {start, n});
            return lines;
        }

        private List<String> wrapWords(String text, FontMetrics fm, int maxW) {
            List<String> out = new ArrayList<>();
            for (String para : text.split("\n")) {
                StringBuilder cur = new StringBuilder();
                for (String w : para.split(" ")) {
                    if (cur.length() > 0 && fm.stringWidth(cur + " " + w) > maxW) { out.add(cur.toString()); cur.setLength(0); }
                    if (cur.length() > 0) cur.append(' ');
                    cur.append(w);
                }
                if (cur.length() > 0) out.add(cur.toString());
            }
            return out;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            Render.hints(g);
            int W = getWidth(), H = getHeight();
            g.setColor(theme.bg);
            g.fillRect(0, 0, W, H);
            if (lesson == null) {
                g.setFont(Fonts.ui(Font.PLAIN, 14f));
                g.setColor(theme.sub);
                String s = "No tutorials found. Put tutorial files in the tutor folder, or choose another folder.";
                g.drawString(s, (W - g.getFontMetrics().stringWidth(s)) / 2f, H / 2f);
                g.dispose();
                return;
            }
            int pad = 28;
            // header
            Font head = Fonts.ui(Font.BOLD, 15f), stat = Fonts.type(12);
            g.setFont(head);
            g.setColor(theme.main);
            FontMetrics hm = g.getFontMetrics();
            g.drawString(String.format("Lesson %02d \u00b7 %s", lesson.number, lesson.title), pad, pad - 4 + hm.getAscent() / 2);
            g.setFont(stat);
            FontMetrics sm = g.getFontMetrics();
            String s1 = "step ", s2 = " acc ", s3 = " wpm ";
            String a = pos + "/" + lesson.exercise.length(), b = Math.round(finished ? lastAcc : acc()) + "%", c = Long.toString(Math.round(finished ? lastWpm : wpm()));
            int total = sm.stringWidth(s1 + a + "   " + s2.trim() + " " + b + "   " + s3.trim() + " " + c);
            int x = W - pad - total;
            int by = pad - 4 + hm.getAscent() / 2;
            String[][] parts = {{"step ", "s"}, {a + "   ", "m"}, {"acc ", "s"}, {b + "   ", "m"}, {"wpm ", "s"}, {c, "m"}};
            for (String[] p : parts) {
                g.setColor(p[1].equals("s") ? theme.sub : theme.main);
                g.drawString(p[0], x, by);
                x += sm.stringWidth(p[0]);
            }

            int top = pad + 22, bottom = H - 12;
            int avail = bottom - top;
            int textW = Math.min(W - 2 * pad, 880);
            int cx = W / 2;

            // instruction
            Font insF = Fonts.ui(Font.PLAIN, Settings.get().num("uiFontSize") + 2f);
            g.setFont(insF);
            FontMetrics im = g.getFontMetrics();
            List<String> ins = wrapWords(lesson.instruction, im, Math.min(textW, 600));
            int insH = ins.size() * (im.getHeight() + 2);
            // exercise
            int size = Math.max(16, Math.min(Settings.get().num("fontSize"), Math.max(16, W / 28)));
            Font exF = Fonts.type(size);
            g.setFont(exF);
            FontMetrics em = g.getFontMetrics();
            List<int[]> lines = wrap(lesson.exercise, em, textW);
            int lineH = (int) Math.round(em.getHeight() * 1.55);
            int chipH = 26, gap = 18;
            int fixed = insH + gap + chipH + gap;
            int fit = Math.max(1, Math.min(lines.size(), (avail - fixed) / lineH));
            int exH = fit * lineH;
            int blockH = fixed + exH;
            int y = top + Math.max(0, (avail - blockH) / 2);

            g.setFont(insF);
            g.setColor(mix(theme.text, theme.bg, 0.75));
            for (String line : ins) {
                g.drawString(line, cx - im.stringWidth(line) / 2f, y + im.getAscent());
                y += im.getHeight() + 2;
            }
            y += gap;

            if (finished) {
                g.setFont(Fonts.ui(Font.BOLD, 22f));
                g.setColor(theme.main);
                String t = "Tutorial complete";
                g.drawString(t, cx - g.getFontMetrics().stringWidth(t) / 2f, y + 28);
                g.setFont(Fonts.type(18));
                g.setColor(theme.text);
                TutorProgress.Record rec = TutorProgress.get(lesson.id());
                String r1 = Math.round(lastWpm) + " wpm   " + Math.round(lastAcc) + "% accuracy";
                String r2 = rec == null ? "" : "best " + Math.round(rec.wpm) + " wpm, " + Math.round(rec.acc) + "%   completed " + rec.times + "x";
                FontMetrics fm = g.getFontMetrics();
                g.drawString(r1, cx - fm.stringWidth(r1) / 2f, y + 64);
                g.setColor(theme.sub);
                g.drawString(r2, cx - fm.stringWidth(r2) / 2f, y + 90);
                g.setFont(Fonts.ui(Font.PLAIN, 13f));
                String h = "Enter: next tutorial   \u00b7   Tab + Enter: repeat";
                g.drawString(h, cx - g.getFontMetrics().stringWidth(h) / 2f, y + 124);
                g.dispose();
                return;
            }

            // finger chip
            FingerMap.Target tg = FingerMap.find(Content.layout(effective()), lesson.exercise.charAt(pos));
            String key = lesson.exercise.charAt(pos) == ' ' ? "Space" : String.valueOf(lesson.exercise.charAt(pos));
            String chip = key + "  \u00b7  " + (tg == null ? "any finger" : FINGER_NAMES.get(tg.finger)
                    + (tg.shift ? "  +  Shift (" + FINGER_NAMES.get(tg.shiftFinger) + ")" : ""));
            g.setFont(Fonts.type(13));
            FontMetrics cm = g.getFontMetrics();
            int cw = cm.stringWidth(chip) + 28;
            g.setColor(theme.subAlt);
            g.fill(new RoundRectangle2D.Double(cx - cw / 2.0, y, cw, chipH, chipH, chipH));
            g.setColor(theme.main);
            g.drawString(chip, cx - cm.stringWidth(chip) / 2f, y + (chipH - cm.getHeight()) / 2f + cm.getAscent());
            y += chipH + gap;

            // exercise lines
            int curLine = 0;
            for (int i = 0; i < lines.size(); i++) if (pos >= lines.get(i)[0] && pos < lines.get(i)[1]) curLine = i;
            int first = Math.max(0, Math.min(curLine - fit / 2, lines.size() - fit));
            g.setFont(exF);
            g.setClip(0, y - 4, W, exH + 8);
            boolean flash = System.currentTimeMillis() < flashUntil;
            for (int li = first; li < first + fit && li < lines.size(); li++) {
                int[] ln = lines.get(li);
                int end = ln[1];
                while (end > ln[0] && lesson.exercise.charAt(end - 1) == ' ') end--;
                int lw = 0;
                for (int k = ln[0]; k < end; k++) lw += em.charWidth(lesson.exercise.charAt(k));
                int px = cx - lw / 2;
                int base = y + (li - first) * lineH + (lineH - em.getHeight()) / 2 + em.getAscent();
                for (int k = ln[0]; k < end; k++) {
                    char ch = lesson.exercise.charAt(k);
                    int w = em.charWidth(ch);
                    if (k == pos) {
                        g.setColor(flash ? theme.error : theme.main);
                        g.fill(new RoundRectangle2D.Double(px, base - em.getAscent() - 1, w, em.getHeight() + 2, 4, 4));
                        g.setColor(theme.bg);
                    } else {
                        g.setColor(k < pos ? theme.text : theme.sub);
                    }
                    if (ch != ' ') g.drawString(String.valueOf(ch), px, base);
                    px += w;
                }
            }
            g.setClip(null);
            if (!hasFocus()) {
                g.setFont(Fonts.ui(Font.PLAIN, 13f));
                g.setColor(theme.sub);
                String t = "Click here and start typing";
                g.drawString(t, cx - g.getFontMetrics().stringWidth(t) / 2f, H - 8);
            }
            g.dispose();
        }
    }

    private static Color mix(Color c, Color b, double a) {
        return new Color((int) Math.round(c.getRed() * a + b.getRed() * (1 - a)), (int) Math.round(c.getGreen() * a + b.getGreen() * (1 - a)),
                (int) Math.round(c.getBlue() * a + b.getBlue() * (1 - a)));
    }
}
