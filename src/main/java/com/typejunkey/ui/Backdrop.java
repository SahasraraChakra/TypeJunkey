package com.typejunkey.ui;

import com.typejunkey.data.Settings;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.File;
import java.net.URL;

/** The optional custom background image behind the typing test, with size and filter settings. */
final class Backdrop {
    private Backdrop() {}

    private static BufferedImage source;
    private static String sourcePath = "";
    private static boolean loading;
    private static BufferedImage cache;
    private static String cacheKey = "";

    private static void ensureSource(String path) {
        if (path.equals(sourcePath)) return;
        sourcePath = path;
        source = null; cache = null; cacheKey = "";
        if (path.isEmpty()) return;
        if (path.startsWith("http://") || path.startsWith("https://")) {
            if (loading) return;
            loading = true;
            Thread t = new Thread(() -> {
                try {
                    BufferedImage img = ImageIO.read(new URL(path));
                    if (path.equals(sourcePath)) source = img;
                } catch (Exception ignored) { }
                loading = false;
                SwingUtilities.invokeLater(() -> Settings.get().fire());
            }, "backdrop");
            t.setDaemon(true);
            t.start();
        } else {
            try { source = ImageIO.read(new File(path)); } catch (Exception ignored) { source = null; }
        }
    }

    /** Paints the background slice behind c. Returns false when no background is configured (caller fills its own colour). */
    static boolean paint(Graphics2D g, JComponent c, Color bg) {
        Settings cfg = Settings.get();
        String path = cfg.str("customBackground").trim();
        ensureSource(path);
        if (source == null) return false;
        java.awt.Container root = SwingUtilities.getRootPane(c);
        int W = root == null ? c.getWidth() : root.getWidth(), H = root == null ? c.getHeight() : root.getHeight();
        if (W <= 0 || H <= 0) return false;
        String key = path + "|" + W + "x" + H + "|" + cfg.str("customBackgroundSize") + "|" + cfg.num("bgBlur") + "|" + cfg.num("bgBrightness") + "|" + cfg.num("bgSaturate");
        if (!key.equals(cacheKey) || cache == null) { cache = process(W, H, cfg); cacheKey = key; }
        Point off = root == null ? new Point() : SwingUtilities.convertPoint(c, 0, 0, root);
        g.setColor(bg);
        g.fillRect(0, 0, c.getWidth(), c.getHeight());
        g.setComposite(AlphaComposite.SrcOver.derive(cfg.num("bgOpacity") / 100f));
        g.drawImage(cache, -off.x, -off.y, null);
        g.setComposite(AlphaComposite.SrcOver);
        return true;
    }

    private static BufferedImage process(int W, int H, Settings cfg) {
        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int iw = source.getWidth(), ih = source.getHeight();
        String size = cfg.str("customBackgroundSize");
        double sx, sy;
        if (size.equals("max")) { sx = (double) W / iw; sy = (double) H / ih; }
        else {
            double s = size.equals("contain") ? Math.min((double) W / iw, (double) H / ih) : Math.max((double) W / iw, (double) H / ih);
            sx = sy = s;
        }
        int dw = (int) Math.round(iw * sx), dh = (int) Math.round(ih * sy);
        g.drawImage(source, (W - dw) / 2, (H - dh) / 2, dw, dh, null);
        g.dispose();
        int blur = cfg.num("bgBlur"), br = cfg.num("bgBrightness"), sat = cfg.num("bgSaturate");
        if (br != 100 || sat != 100) {
            int[] px = out.getRGB(0, 0, W, H, null, 0, W);
            float b = br / 100f, s = sat / 100f;
            for (int i = 0; i < px.length; i++) {
                int p = px[i];
                int a = p >>> 24, r = (p >> 16) & 255, gr = (p >> 8) & 255, bl = p & 255;
                float lum = 0.299f * r + 0.587f * gr + 0.114f * bl;
                r = clamp((lum + (r - lum) * s) * b); gr = clamp((lum + (gr - lum) * s) * b); bl = clamp((lum + (bl - lum) * s) * b);
                px[i] = (a << 24) | (r << 16) | (gr << 8) | bl;
            }
            out.setRGB(0, 0, W, H, px, 0, W);
        }
        if (blur > 0) {
            int k = blur * 2 + 1;
            float[] data = new float[k];
            java.util.Arrays.fill(data, 1f / k);
            out = new ConvolveOp(new Kernel(k, 1, data), ConvolveOp.EDGE_NO_OP, null).filter(out, null);
            out = new ConvolveOp(new Kernel(1, k, data), ConvolveOp.EDGE_NO_OP, null).filter(out, null);
        }
        return out;
    }

    private static int clamp(float v) { return v < 0 ? 0 : v > 255 ? 255 : (int) v; }
}
