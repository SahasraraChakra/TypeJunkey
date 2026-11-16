package com.typejunkey;

import com.typejunkey.ui.AppTheme;
import com.typejunkey.ui.MainFrame;

import javax.swing.SwingUtilities;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            AppTheme.prepare();
            AppTheme.apply(AppTheme.desiredDark());
            new MainFrame().setVisible(true);
        });
    }
}
