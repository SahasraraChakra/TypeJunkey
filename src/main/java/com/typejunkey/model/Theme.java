package com.typejunkey.model;

import java.awt.Color;

/** A Monkeytype colour theme. */
public final class Theme {
    public final String name;
    public final Color bg, main, caret, sub, subAlt, text, error, errorExtra, colorfulError, colorfulErrorExtra;

    public Theme(String name, Color bg, Color main, Color caret, Color sub, Color subAlt, Color text,
                 Color error, Color errorExtra, Color colorfulError, Color colorfulErrorExtra) {
        this.name = name; this.bg = bg; this.main = main; this.caret = caret; this.sub = sub; this.subAlt = subAlt;
        this.text = text; this.error = error; this.errorExtra = errorExtra;
        this.colorfulError = colorfulError; this.colorfulErrorExtra = colorfulErrorExtra;
    }

    public String display() { return name.replace('_', ' '); }

    /** Perceived luminance of the background, 0 (black) to 1 (white). */
    public double luminance() {
        return (0.2126 * bg.getRed() + 0.7152 * bg.getGreen() + 0.0722 * bg.getBlue()) / 255.0;
    }

    public boolean isDark() { return luminance() < 0.5; }

    public static Theme fallback() {
        return new Theme("serika_dark", new Color(0x323437), new Color(0xe2b714), new Color(0xe2b714),
                new Color(0x646669), new Color(0x2c2e31), new Color(0xd1d0c5), new Color(0xca4754), new Color(0x7e2a33),
                new Color(0xcaca47), new Color(0x7e2a33));
    }
}
