package com.typejunkey.ui;

import com.typejunkey.data.History;
import com.typejunkey.model.TestResult;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/** Best result for each test configuration. */
public final class BestsPanel extends JPanel {
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] {"Test", "Language", "Options", "wpm", "raw", "acc %", "consistency %", "Date"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
        @Override public Class<?> getColumnClass(int c) { return c >= 3 && c <= 6 ? Double.class : String.class; }
    };

    public BestsPanel() {
        super(new BorderLayout());
        JTable table = new JTable(model);
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(22);
        table.setFillsViewportHeight(true);
        table.setShowGrid(false);
        DefaultTableCellRenderer num = new DefaultTableCellRenderer();
        num.setHorizontalAlignment(DefaultTableCellRenderer.RIGHT);
        for (int c = 3; c <= 6; c++) table.getColumnModel().getColumn(c).setCellRenderer(num);
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createEmptyBorder());
        add(sp, BorderLayout.CENTER);
        refresh();
    }

    private static double r1(double v) { return Math.round(v * 10.0) / 10.0; }

    public void refresh() {
        model.setRowCount(0);
        Map<String, TestResult> best = new LinkedHashMap<>();
        for (TestResult r : History.all()) {
            TestResult b = best.get(r.key());
            if (b == null || r.wpm > b.wpm) best.put(r.key(), r);
        }
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd");
        for (TestResult r : best.values()) {
            model.addRow(new Object[] {r.label(), r.language, r.flags().isEmpty() ? "-" : r.flags(), r1(r.wpm), r1(r.raw),
                    r1(r.acc), r1(r.consistency), f.format(new Date(r.when))});
        }
    }
}
