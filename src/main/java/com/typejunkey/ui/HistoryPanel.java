package com.typejunkey.ui;

import com.typejunkey.data.History;
import com.typejunkey.model.TestResult;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/** Table of every finished test, newest first. */
public final class HistoryPanel extends JPanel {
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] {"Date", "Test", "Language", "wpm", "raw", "acc %", "consistency %", "chars (c/i/e/m)", "time (s)"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
        @Override public Class<?> getColumnClass(int c) { return (c >= 3 && c <= 6) || c == 8 ? Double.class : String.class; }
    };
    private final JTable table = new JTable(model);

    public HistoryPanel(Runnable onCleared) {
        super(new BorderLayout());
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(22);
        table.setFillsViewportHeight(true);
        table.setShowGrid(false);
        table.setIntercellSpacing(new java.awt.Dimension(0, 0));
        DefaultTableCellRenderer num = new DefaultTableCellRenderer();
        num.setHorizontalAlignment(DefaultTableCellRenderer.RIGHT);
        for (int c : new int[] {3, 4, 5, 6, 8}) table.getColumnModel().getColumn(c).setCellRenderer(num);
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createEmptyBorder());
        add(sp, BorderLayout.CENTER);
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        JButton clear = new JButton("Clear history\u2026");
        clear.addActionListener(e -> {
            int a = JOptionPane.showConfirmDialog(this, "Delete all saved results? This cannot be undone.",
                    "Clear history", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (a == JOptionPane.OK_OPTION) { History.clear(); onCleared.run(); }
        });
        south.add(clear);
        add(south, BorderLayout.SOUTH);
        refresh();
    }

    private static double r1(double v) { return Math.round(v * 10.0) / 10.0; }

    public void refresh() {
        model.setRowCount(0);
        List<TestResult> all = History.all();
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        for (int i = all.size() - 1; i >= 0; i--) {
            TestResult r = all.get(i);
            String test = r.label() + (r.flags().isEmpty() ? "" : " (" + r.flags() + ")");
            model.addRow(new Object[] {f.format(new Date(r.when)), test, r.language, r1(r.wpm), r1(r.raw), r1(r.acc),
                    r1(r.consistency), r.correct + "/" + r.incorrect + "/" + r.extra + "/" + r.missed, r1(r.seconds)});
        }
    }
}
