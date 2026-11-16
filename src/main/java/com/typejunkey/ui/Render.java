package com.typejunkey.ui;

import com.typejunkey.data.Settings;

import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Rendering hints for all custom painted text: smooth anti-aliasing by default. */
public final class Render {
    private Render() {}

    public static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        String mode = Settings.get().str("textRendering");
        Object aa = mode.equals("off") ? RenderingHints.VALUE_TEXT_ANTIALIAS_OFF
                : mode.equals("sharp") ? RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB : RenderingHints.VALUE_TEXT_ANTIALIAS_ON;
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, aa);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                mode.equals("off") ? RenderingHints.VALUE_FRACTIONALMETRICS_OFF : RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }
}
