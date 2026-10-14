/*
 * This file is part of the Alchemy project - http://al.chemy.org
 *
 *  Alchemy is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Alchemy is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Alchemy.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.alchemy.core;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import javax.swing.ImageIcon;

/**
 * A one-color image of the classic interface (module icons and options),
 * recolored with the modern theme so it stays readable in light and dark.
 *
 * Only images drawn in one color (black, grey, or the green of the selected
 * state) are recolored: colorful images, cursors and pickers are left as they are.
 */
final class AlcTintedIcon extends ImageIcon {

    /** Images used as pictures, never recolored */
    private static final String[] KEEP = {"cursor-", "color-", "alchemy-logo", "back", "simple-", "palette", "spinner", "sub-spinner"};
    private static final Map<String, BufferedImage> MASKS = new HashMap<String, BufferedImage>();

    private final BufferedImage mask;
    private final int state;

    private AlcTintedIcon(BufferedImage mask, int state) {
        this.mask = mask;
        this.state = state;
    }

    static ImageIcon forUrl(URL url, String base, int state) {
        for (String keep : KEEP) {
            if (base.startsWith(keep)) {
                return null;
            }
        }
        BufferedImage mask = mask(url);
        return mask == null ? null : new AlcTintedIcon(mask, state);
    }

    /**
     * The pixels of a grey image (plus the green of the selected state), as
     * alpha / luminance / green flag, or null if the image is colorful or missing
     */
    private static synchronized BufferedImage mask(URL url) {
        String key = url.toString();
        if (MASKS.containsKey(key)) {
            return MASKS.get(key);
        }
        BufferedImage result = null;
        ImageIcon original = new ImageIcon(url);
        int w = original.getIconWidth(), h = original.getIconHeight();
        if (w > 0 && h > 0) {
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.drawImage(original.getImage(), 0, 0, null);
            g.dispose();
            if (isGrey(img)) {
                // Packed as ARGB: A = alpha, R = luminance, G = 255 for the green pixels
                result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int argb = img.getRGB(x, y);
                        int r = argb >> 16 & 0xFF, gr = argb >> 8 & 0xFF, b = argb & 0xFF;
                        boolean green = gr > 150 && r < 120 && b < 120;
                        int lum = (r + gr + b) / 3;
                        result.setRGB(x, y, (argb & 0xFF000000) | (lum << 16) | ((green ? 255 : 0) << 8));
                    }
                }
            }
        }
        MASKS.put(key, result);
        return result;
    }

    /** True for images drawn in greys only (and the green of the selected state) */
    private static boolean isGrey(BufferedImage img) {
        int opaque = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int argb = img.getRGB(x, y);
                if ((argb >>> 24) < 40) {
                    continue;
                }
                opaque++;
                int r = argb >> 16 & 0xFF, g = argb >> 8 & 0xFF, b = argb & 0xFF;
                boolean grey = Math.abs(r - g) < 24 && Math.abs(g - b) < 24 && Math.abs(r - b) < 24;
                boolean green = g > 150 && r < 120 && b < 120;
                if (!grey && !green) {
                    return false;
                }
            }
        }
        return opaque > 0;
    }

    @Override
    public int getIconWidth() {
        return mask.getWidth();
    }

    @Override
    public int getIconHeight() {
        return mask.getHeight();
    }

    @Override
    public Image getImage() {
        BufferedImage img = new BufferedImage(mask.getWidth(), mask.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        paintIcon(null, g, 0, 0);
        g.dispose();
        return img;
    }

    @Override
    public synchronized void paintIcon(Component c, Graphics g, int x, int y) {
        // Dark pixels take the icon color, light pixels the background color
        Color ink = AlcVectorIcon.color(state);
        Color paper = AlcTheme.bar();
        Color accent = AlcTheme.accent();
        int w = mask.getWidth(), h = mask.getHeight();
        BufferedImage tinted = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                int m = mask.getRGB(px, py);
                int a = m >>> 24;
                if (a == 0) {
                    continue;
                }
                Color c0;
                if ((m >> 8 & 0xFF) == 255) {
                    c0 = accent;
                } else {
                    float t = (m >> 16 & 0xFF) / 255f;
                    c0 = new Color(
                            Math.round(ink.getRed() + (paper.getRed() - ink.getRed()) * t),
                            Math.round(ink.getGreen() + (paper.getGreen() - ink.getGreen()) * t),
                            Math.round(ink.getBlue() + (paper.getBlue() - ink.getBlue()) * t));
                }
                tinted.setRGB(px, py, (a << 24) | (c0.getRGB() & 0xFFFFFF));
            }
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.drawImage(tinted, x, y, null);
        g2.dispose();
    }
}
