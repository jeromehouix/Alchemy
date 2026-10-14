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
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import javax.swing.ImageIcon;

/**
 * Icons of the modern interface.
 *
 * {@link #forUrl(URL)} is called for every icon Alchemy loads. In the modern
 * interface it returns, for the icons of the toolbar, a vector icon drawn with
 * Java2D (sharp on Retina screens, colored by the theme), and for the other
 * one-color icons (modules...) the original image recolored with the theme.
 * The state of an icon comes from its file name, as in the original images:
 * "-over" (mouse over), "-on" (selected), "-on-over", "-down" (pressed).
 * Vector icons keep the size of the image they replace, so layouts do not move.
 */
class AlcVectorIcon extends ImageIcon {

    static final int NORMAL = 0, OVER = 1, ON = 2, ON_OVER = 3, DOWN = 4;

    /** Draws one icon in a w x h box */
    interface Painter {

        void paint(Graphics2D g, float w, float h, int state);
    }

    private static final Map<String, Painter> PAINTERS = new HashMap<String, Painter>();
    private static final Map<String, Dimension> SIZES = new HashMap<String, Dimension>();

    private final Painter painter;
    private final int width, height, state;

    private AlcVectorIcon(Painter painter, int width, int height, int state) {
        this.painter = painter;
        this.width = width;
        this.height = height;
        this.state = state;
    }

    @Override
    public int getIconWidth() {
        return width;
    }

    @Override
    public int getIconHeight() {
        return height;
    }

    @Override
    public synchronized void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.translate(x, y);
            g2.setColor(color(state));
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            painter.paint(g2, width, height, state);
        } finally {
            g2.dispose();
        }
    }

    /** Some code asks for the image itself */
    @Override
    public Image getImage() {
        BufferedImage img = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        paintIcon(null, g, 0, 0);
        g.dispose();
        return img;
    }

    static Color color(int state) {
        switch (state) {
            case OVER:
                return AlcTheme.accent();
            case ON:
                return AlcTheme.accent();
            case ON_OVER:
            case DOWN:
                return AlcTheme.accentDown();
            default:
                return AlcTheme.text();
        }
    }

    //////////////////////////////////////////////////////////////
    // LOOKUP
    //////////////////////////////////////////////////////////////
    /**
     * The icon to use for an image file
     *
     * @return A vector or recolored icon in the modern interface, null otherwise
     *         (the caller then loads the original image)
     */
    static ImageIcon forUrl(URL url) {
        if (url == null || !AlcTheme.isModern()) {
            return null;
        }
        String path = url.getPath();
        String file = path.substring(path.lastIndexOf('/') + 1);
        int dot = file.lastIndexOf('.');
        String name = dot > 0 ? file.substring(0, dot) : file;
        int state = NORMAL;
        String base = name;
        if (name.endsWith("-on-over")) {
            state = ON_OVER;
            base = name.substring(0, name.length() - 8);
        } else if (name.endsWith("-over")) {
            state = OVER;
            base = name.substring(0, name.length() - 5);
        } else if (name.endsWith("-on")) {
            state = ON;
            base = name.substring(0, name.length() - 3);
        } else if (name.endsWith("-down")) {
            state = DOWN;
            base = name.substring(0, name.length() - 5);
        }
        Painter painter = PAINTERS.get(base);
        if (painter != null) {
            // Same size as the image it replaces (modules reuse names at other sizes)
            Dimension size = imageSize(url, name, base);
            if (size == null) {
                size = SIZES.get(base);
            }
            return new AlcVectorIcon(painter, size.width, size.height, state);
        }
        return AlcTintedIcon.forUrl(url, base, state);
    }

    private static final Map<String, Dimension> IMAGE_SIZES = new HashMap<String, Dimension>();

    /** Size of the original image, or of its base image when the variant does not exist */
    private static synchronized Dimension imageSize(URL url, String name, String base) {
        String baseUrl = url.toString().replace(name + ".", base + ".");
        if (IMAGE_SIZES.containsKey(baseUrl)) {
            return IMAGE_SIZES.get(baseUrl);
        }
        Dimension size = null;
        try {
            ImageIcon original = new ImageIcon(new URL(baseUrl));
            if (original.getIconWidth() > 0) {
                size = new Dimension(original.getIconWidth(), original.getIconHeight());
            }
        } catch (Exception ex) {
            // Unknown image: default size
        }
        IMAGE_SIZES.put(baseUrl, size);
        return size;
    }

    private static void icon(String name, int w, int h, Painter painter) {
        PAINTERS.put(name, painter);
        SIZES.put(name, new Dimension(w, h));
    }

    //////////////////////////////////////////////////////////////
    // DRAWING HELPERS (coordinates in a 24 x 24 box)
    //////////////////////////////////////////////////////////////
    private static Graphics2D box(Graphics2D g, float w, float h, float size) {
        float s = Math.min(w, h) / size;
        g.translate((w - size * s) / 2f, (h - size * s) / 2f);
        g.scale(s, s);
        return g;
    }

    private static Path2D path(float... xy) {
        Path2D.Float p = new Path2D.Float();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i + 1 < xy.length; i += 2) {
            p.lineTo(xy[i], xy[i + 1]);
        }
        return p;
    }

    static {
        // STYLE: outline square = line, filled square = solid (selected)
        icon("style", 24, 24, (g, w, h, s) -> {
            box(g, w, h, 24);
            RoundRectangle2D r = new RoundRectangle2D.Float(5, 5, 14, 14, 3, 3);
            if (s == ON || s == ON_OVER) {
                g.fill(r);
            } else {
                g.draw(r);
            }
        });
        // DRAW OVER / UNDER: stack of layers, the drawn one on top or below
        icon("underOver", 24, 24, (g, w, h, s) -> {
            box(g, w, h, 24);
            boolean under = s == ON || s == ON_OVER;
            Path2D top = path(12, 4, 20, 8, 12, 12, 4, 8);
            top.closePath();
            Path2D mid = path(4, 12, 12, 16, 20, 12);
            Path2D bottom = path(4, 16, 12, 20, 20, 16);
            if (under) {
                g.draw(top);
                g.draw(mid);
                g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(bottom);
            } else {
                g.fill(top);
                g.draw(top);
                g.draw(mid);
                g.draw(bottom);
            }
        });
        // CREATE: spark
        icon("create", 24, 24, (g, w, h, s) -> {
            box(g, w, h, 24);
            g.draw(path(12, 3, 12, 7));
            g.draw(path(12, 17, 12, 21));
            g.draw(path(3, 12, 7, 12));
            g.draw(path(17, 12, 21, 12));
            g.draw(path(6, 6, 8.5f, 8.5f));
            g.draw(path(15.5f, 15.5f, 18, 18));
            g.draw(path(6, 18, 8.5f, 15.5f));
            g.draw(path(15.5f, 8.5f, 18, 6));
        });
        // AFFECT: two opposite arrows
        icon("affect", 24, 24, (g, w, h, s) -> {
            box(g, w, h, 24);
            g.draw(path(8, 4, 8, 20));
            g.draw(path(5, 17, 8, 20, 11, 17));
            g.draw(path(16, 20, 16, 4));
            g.draw(path(13, 7, 16, 4, 19, 7));
        });
        // ZOOM
        icon("zoom", 24, 24, (g, w, h, s) -> {
            box(g, w, h, 24);
            Ellipse2D lens = new Ellipse2D.Float(5, 5, 11, 11);
            if (s == ON || s == ON_OVER) {
                g.setColor(new Color(g.getColor().getRed(), g.getColor().getGreen(), g.getColor().getBlue(), 60));
                g.fill(lens);
                g.setColor(color(s));
            }
            g.draw(lens);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(path(14.5f, 14.5f, 19.5f, 19.5f));
        });
        // CLEAR
        icon("clear", 24, 24, (g, w, h, s) -> {
            box(g, w, h, 24);
            g.draw(path(6, 6, 18, 18));
            g.draw(path(18, 6, 6, 18));
        });
        // UNDO
        icon("undo", 24, 24, (g, w, h, s) -> {
            box(g, w, h, 24);
            g.draw(path(8, 6, 4, 10, 8, 14));
            Path2D arc = new Path2D.Float();
            arc.moveTo(4, 10);
            arc.lineTo(14, 10);
            arc.curveTo(17.5f, 10, 20, 12.5f, 20, 15);
            arc.curveTo(20, 17.5f, 17.5f, 20, 14, 20);
            arc.lineTo(11, 20);
            g.draw(arc);
        });
        // SWATCH MENU: palette card
        icon("swatchMenu", 24, 24, (g, w, h, s) -> {
            box(g, w, h, 24);
            g.draw(new RoundRectangle2D.Float(4, 4, 16, 16, 4, 4));
            g.fill(new Ellipse2D.Float(7, 7, 4, 4));
            g.fill(new Ellipse2D.Float(13, 7, 4, 4));
            g.fill(new Ellipse2D.Float(7, 13, 4, 4));
            g.draw(new Ellipse2D.Float(13.5f, 13.5f, 3, 3));
        });
        // ADD / REMOVE A SWATCH COLOR
        icon("add", 24, 24, (g, w, h, s) -> {
            box(g, w, h, 24);
            g.draw(path(12, 6, 12, 18));
            g.draw(path(6, 12, 18, 12));
        });
        icon("remove", 24, 24, (g, w, h, s) -> {
            box(g, w, h, 24);
            g.draw(path(7, 7, 17, 17));
            g.draw(path(17, 7, 7, 17));
        });
        // MOVE THE SWATCH COLOR LEFT / RIGHT
        icon("swatchLeft", 24, 59, (g, w, h, s) -> {
            g.translate(0, (h - 24) / 2f);
            g.draw(path(15, 6, 9, 12, 15, 18));
        });
        icon("swatchRight", 24, 59, (g, w, h, s) -> {
            g.translate(0, (h - 24) / 2f);
            g.draw(path(9, 6, 15, 12, 9, 18));
        });
        // LOCK THE OPACITY
        icon("lock", 20, 36, (g, w, h, s) -> {
            g.translate((w - 16) / 2f, (h - 20) / 2f);
            boolean locked = s == ON || s == ON_OVER;
            g.draw(new RoundRectangle2D.Float(2, 9, 12, 10, 3, 3));
            Path2D shackle = new Path2D.Float();
            shackle.moveTo(4.5f, 9);
            shackle.lineTo(4.5f, 6);
            shackle.curveTo(4.5f, 1.5f, 11.5f, 1.5f, 11.5f, 6);
            if (locked) {
                shackle.lineTo(11.5f, 9);
            }
            g.draw(shackle);
        });
        // EXACT RENDER: a smooth ramp of layers (filled when on)
        icon("exact", 24, 24, (g, w, h, s) -> {
            box(g, w, h, 24);
            boolean on = s == ON || s == ON_OVER;
            Color c = g.getColor();
            g.draw(new RoundRectangle2D.Float(3.5f, 5.5f, 17, 13, 3, 3));
            for (int i = 0; i < 4; i++) {
                int alpha = on ? 60 + i * 65 : 35 + i * 40;
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.min(255, alpha)));
                g.fill(new Rectangle2D.Float(6 + i * 3.25f, 8, 3.25f, 8));
            }
            g.setColor(c);
        });
        // SEPARATORS
        icon("separator", 1, 40, (g, w, h, s) -> {
            g.setColor(AlcTheme.line());
            g.fill(new Rectangle2D.Float(0, 8, 1, h - 16));
        });
        icon("sub-separator", 1, 16, (g, w, h, s) -> {
            g.setColor(AlcTheme.line());
            g.fill(new Rectangle2D.Float(0, 1, 1, h - 2));
        });
        // ARROW OF A FOLDED MODULE SECTION
        icon("sub-section-arrow", 5, 8, (g, w, h, s) -> {
            g.setColor(AlcTheme.muted());
            Path2D p = path(0.5f, 0.5f, 4.5f, 4, 0.5f, 7.5f);
            p.closePath();
            g.fill(p);
        });

        // PULL SHAPES
        icon("reload", 15, 15, (g, w, h, s) -> {
            box(g, w, h, 24);
            g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Arc2D.Float(4, 4, 16, 16, 30, 280, Arc2D.OPEN));
            g.draw(path(20, 4, 20, 10, 14, 10));
        });
        icon("rotate", 15, 15, (g, w, h, s) -> {
            box(g, w, h, 24);
            g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Arc2D.Float(4, 4, 16, 16, 150, 280, Arc2D.OPEN));
            g.draw(path(4, 4, 4, 10, 10, 10));
        });
        icon("whole", 15, 15, (g, w, h, s) -> {
            box(g, w, h, 24);
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, new float[]{2.5f, 2.5f}, 0));
            g.draw(new RoundRectangle2D.Float(2.5f, 2.5f, 19, 19, 3, 3));
            g.fill(new Ellipse2D.Float(5.5f, 5.5f, 7, 7));
            g.fill(new Rectangle2D.Float(12.5f, 12.5f, 6, 6));
            Path2D t = path(5, 18.5f, 8, 13, 11, 18.5f);
            t.closePath();
            g.fill(t);
        });
        icon("colours", 15, 15, (g, w, h, s) -> {
            box(g, w, h, 24);
            boolean on = s == ON || s == ON_OVER;
            Color[] dots = {new Color(0xe63946), new Color(0x2a9d8f), new Color(0xe9c46a)};
            float[][] at = {{2.5f, 3, 10}, {11.5f, 3, 10}, {7, 11, 10}};
            for (int i = 0; i < 3; i++) {
                Ellipse2D e = new Ellipse2D.Float(at[i][0], at[i][1], at[i][2], at[i][2]);
                if (on) {
                    g.setColor(dots[i]);
                    g.fill(e);
                } else {
                    g.setColor(color(s));
                    g.draw(e);
                }
            }
        });
    }
}
