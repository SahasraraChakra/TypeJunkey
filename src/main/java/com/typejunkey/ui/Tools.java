package com.typejunkey.ui;

import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import java.awt.Insets;

/** Factory for compact FlatLaf toolbar buttons that carry an icon or a short label plus a tooltip. */
final class Tools {
    private Tools() {}

    static JToggleButton toggle(Icons.Kind kind, String label, String tip) {
        JToggleButton b = new JToggleButton();
        style(b, kind, label, tip);
        return b;
    }

    static JButton button(Icons.Kind kind, String label, String tip) {
        JButton b = new JButton();
        style(b, kind, label, tip);
        return b;
    }

    private static void style(AbstractButton b, Icons.Kind kind, String label, String tip) {
        if (kind != null) b.setIcon(Icons.of(kind));
        if (label != null) b.setText(label);
        b.setToolTipText(tip);
        b.getAccessibleContext().setAccessibleName(tip);
        b.setFocusable(false);
        b.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        b.setMargin(new Insets(4, label != null && kind == null ? 8 : 6, 4, label != null && kind == null ? 8 : 6));
    }

    static JToolBar.Separator sep() { return new JToolBar.Separator(); }
}
