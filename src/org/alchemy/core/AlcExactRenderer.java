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
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.image.*;
import java.util.List;

/**
 * High precision renderer: composites the shapes in floating point.
 *
 * The classic canvas blends every shape into an 8-bit image. With very faint
 * opacities the rounding stalls the accumulation (thousands of layers at 1/255
 * never reach the chosen color) and shifts the hue, because each channel stalls
 * at a different distance from its target. This renderer keeps 32-bit floats per
 * channel, so the result matches the exact maths of the vector drawing (what the
 * SVG export describes), whatever the number of layers and their opacity.
 *
 * Shapes are composited incrementally: only the shapes added since the last call
 * are drawn, the whole image is recomputed only when the drawing changes otherwise.
 */
final class AlcExactRenderer {

    private int width = -1, height = -1;
    /** Linear accumulation buffer, 3 floats (r, g, b in 0-1) per pixel */
    private float[] acc;
    private Color background;
    private Image underImage;
    private Point underImageLocation;
    private boolean smoothing;
    /** Canvas to display transform (zoom and pan), null for none */
    private AffineTransform view;
    /** Number of shapes already composited, and the last one, to detect changes */
    private int done = 0;
    private AlcShape lastDone = null;
    private BufferedImage display;
    /** Area of the display image that needs to be refreshed from the buffer */
    private Rectangle dirty;

    /** Forget everything: the next render recomputes the whole image */
    void invalidate() {
        acc = null;
    }

    /**
     * Render the shapes, compositing only the ones added since the last call
     *
     * @return An 8-bit image of the exact result, for display
     */
    BufferedImage render(List<AlcShape> shapes, Color bg, Image image, Point imageLocation,
            int w, int h, boolean smooth) {
        return render(shapes, bg, image, imageLocation, w, h, smooth, null);
    }

    /**
     * Render the shapes seen through a view (zoom and pan)
     *
     * @param v Canvas to display transform, or null
     * @return  An 8-bit image of the exact result, for display
     */
    BufferedImage render(List<AlcShape> shapes, Color bg, Image image, Point imageLocation,
            int w, int h, boolean smooth, AffineTransform v) {
        if (v != null && v.isIdentity()) {
            v = null;
        }
        boolean reset = acc == null || w != width || h != height
                || !bg.equals(background) || image != underImage
                || (imageLocation != null && !imageLocation.equals(underImageLocation))
                || smooth != smoothing
                || !(v == null ? view == null : v.equals(view))
                || shapes.size() < done
                || (done > 0 && shapes.get(done - 1) != lastDone);
        if (reset) {
            view = v == null ? null : new AffineTransform(v);
            reset(bg, image, imageLocation, w, h, smooth);
        }
        for (int i = done; i < shapes.size(); i++) {
            composite(shapes.get(i));
        }
        done = shapes.size();
        lastDone = done > 0 ? shapes.get(done - 1) : null;
        updateDisplay();
        return display;
    }

    private void reset(Color bg, Image image, Point imageLocation, int w, int h, boolean smooth) {
        width = Math.max(1, w);
        height = Math.max(1, h);
        background = bg;
        underImage = image;
        underImageLocation = imageLocation == null ? null : new Point(imageLocation);
        smoothing = smooth;
        acc = new float[width * height * 3];
        display = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        done = 0;
        lastDone = null;

        // Start from the background, and the image shown under the drawing if any
        BufferedImage base = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = base.createGraphics();
        g.setColor(bg);
        g.fillRect(0, 0, width, height);
        if (image != null) {
            Point p = imageLocation != null ? imageLocation : new Point(0, 0);
            if (view != null) {
                g.transform(view);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            }
            g.drawImage(image, p.x, p.y, null);
        }
        g.dispose();
        int[] rgb = base.getRGB(0, 0, width, height, null, 0, width);
        for (int i = 0; i < rgb.length; i++) {
            acc[i * 3] = ((rgb[i] >> 16) & 0xFF) / 255f;
            acc[i * 3 + 1] = ((rgb[i] >> 8) & 0xFF) / 255f;
            acc[i * 3 + 2] = (rgb[i] & 0xFF) / 255f;
        }
        dirty = new Rectangle(0, 0, width, height);
    }

    /** Composite one shape into the float buffer */
    private void composite(AlcShape shape) {
        Rectangle changed = composite(acc, width, height, 0, 0, shape, view, smoothing);
        if (changed != null) {
            dirty = dirty == null ? changed : dirty.union(changed);
        }
    }

    /**
     * Composite one shape into a float buffer, in high precision
     *
     * @param acc       Buffer, 3 floats (r, g, b in 0-1) per pixel
     * @param accW      Width of the buffer
     * @param accH      Height of the buffer
     * @param offX      Position of the buffer in the target image (for band rendering)
     * @param offY      Position of the buffer in the target image
     * @param shape     Shape, in canvas coordinates
     * @param at        Canvas to target image transform (scale for exports, view for zoom), or null
     * @param smoothing Anti-aliasing
     * @return          The area changed, in target image coordinates, or null
     */
    static Rectangle composite(float[] acc, int accW, int accH, int offX, int offY,
            AlcShape shape, AffineTransform at, boolean smoothing) {
        if (shape == null || shape.path == null) {
            return null;
        }
        Shape outline;
        if (shape.style == AlcConstants.STYLE_STROKE) {
            outline = new BasicStroke(shape.lineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL).createStrokedShape(shape.path);
        } else {
            outline = shape.path;
        }
        Rectangle region = new Rectangle(offX, offY, accW, accH);
        Shape target = at == null ? outline : at.createTransformedShape(outline);
        Rectangle bounds = target.getBounds();
        // Anti-aliasing can touch one more pixel around the shape
        bounds = new Rectangle(bounds.x - 1, bounds.y - 1, bounds.width + 2, bounds.height + 2).intersection(region);
        if (bounds.isEmpty()) {
            return null;
        }

        // Draw the shape with an opaque paint: the alpha of each pixel is then the coverage
        Paint paint = shape.getPaint();
        float opacity;
        if (paint instanceof GradientPaint) {
            // Gradients carry their own (8-bit) opacity
            opacity = 1f;
        } else {
            Color c = shape.color != null ? shape.color : Color.BLACK;
            paint = new Color(c.getRed(), c.getGreen(), c.getBlue());
            opacity = shape.getOpacity();
        }
        if (opacity <= 0f) {
            return null;
        }
        BufferedImage mask = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = mask.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                smoothing ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.translate(-bounds.x, -bounds.y);
        if (at != null) {
            g.transform(at);
        }
        g.setPaint(paint);
        g.fill(outline);
        g.dispose();

        int[] px = ((DataBufferInt) mask.getRaster().getDataBuffer()).getData();
        for (int y = 0; y < bounds.height; y++) {
            int row = (bounds.y - offY + y) * accW + (bounds.x - offX);
            for (int x = 0; x < bounds.width; x++) {
                int argb = px[y * bounds.width + x];
                int a = argb >>> 24;
                if (a == 0) {
                    continue;
                }
                float alpha = (a / 255f) * opacity;
                int i = (row + x) * 3;
                acc[i] += (((argb >> 16) & 0xFF) / 255f - acc[i]) * alpha;
                acc[i + 1] += (((argb >> 8) & 0xFF) / 255f - acc[i + 1]) * alpha;
                acc[i + 2] += ((argb & 0xFF) / 255f - acc[i + 2]) * alpha;
            }
        }
        return bounds;
    }

    /** Copy the dirty area of the float buffer into the display image, rounding once */
    private void updateDisplay() {
        if (dirty == null) {
            return;
        }
        int[] px = ((DataBufferInt) display.getRaster().getDataBuffer()).getData();
        for (int y = dirty.y; y < dirty.y + dirty.height; y++) {
            for (int x = dirty.x; x < dirty.x + dirty.width; x++) {
                int p = y * width + x;
                int i = p * 3;
                px[p] = (to8(acc[i]) << 16) | (to8(acc[i + 1]) << 8) | to8(acc[i + 2]);
            }
        }
        dirty = null;
    }

    private static int to8(float v) {
        int c = Math.round(v * 255f);
        return c < 0 ? 0 : (c > 255 ? 255 : c);
    }

    /** The exact result as a 16 bits per channel image, for export (always without view) */
    BufferedImage toImage16() {
        ColorModel cm = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB),
                new int[]{16, 16, 16}, false, false, Transparency.OPAQUE, DataBuffer.TYPE_USHORT);
        WritableRaster raster = cm.createCompatibleWritableRaster(width, height);
        short[] data = ((DataBufferUShort) raster.getDataBuffer()).getData();
        for (int i = 0; i < acc.length; i++) {
            int c = Math.round(acc[i] * 65535f);
            data[i] = (short) (c < 0 ? 0 : (c > 65535 ? 65535 : c));
        }
        return new BufferedImage(cm, raster, false, null);
    }
}
