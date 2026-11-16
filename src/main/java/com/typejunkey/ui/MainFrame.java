package com.typejunkey.ui;

import javax.swing.JFrame;
import java.awt.Dimension;

/** Top-level window; all content lives in {@link MainPanel}. */
public final class MainFrame extends JFrame {
    public MainFrame() {
        super("TypeJunKey");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setIconImage(MainPanel.appIcon());
        MainPanel panel = new MainPanel();
        setContentPane(panel);
        setJMenuBar(panel.menuBar());
        setMinimumSize(new Dimension(820, 620));
        setSize(1120, 760);
        setLocationRelativeTo(null);
    }
}
