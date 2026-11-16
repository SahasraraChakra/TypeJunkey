package com.typejunkey.ui;

import javax.swing.UIManager;
import java.awt.Color;

final class UIHelper {
    private UIHelper() {}

    static Color panelBackground() {
        Color c = UIManager.getColor("Panel.background");
        return c != null ? c : Color.WHITE;
    }
}
