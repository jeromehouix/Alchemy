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
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.*;
import java.util.List;

/**
 * Renders the drawing at any size from its vectors, for prints, and writes it
 * as a PNG with the print resolution.
 *
 * The image is computed in horizontal bands, so its size is not limited by the
 * memory: a 100 x 70 cm print at 300 dpi (11811 x 8268 pixels) uses about 64 MB
 * at a time. Two renders:
 * - exact: the layers composited in floating point, written in 16 bits per channel;
 * - classic: the 8-bit render of the screen, as in the original Alchemy.
 */
final class AlcHighResExport {

    /** Receives the progress, and can cancel the export */
    interface Progress {

        void update(double fraction);

        boolean isCancelled();
    }

    /** Memory used by one band */
    private static final long BAND_BYTES = 64L << 20;

    private AlcHighResExport() {
    }

    /**
     * @param file          Destination PNG
     * @param shapes        Shapes in canvas coordinates, in painting order
     * @param background    Background color
     * @param image         Image shown under the drawing, or null
     * @param imageLocation Location of the image on the canvas
     * @param canvasWidth   Width of the canvas (the drawing area)
     * @param outWidth      Width of the image to write, in pixels (the height follows)
     * @param dpi           Print resolution stored in the file
     * @param exact         Exact 16-bit render, or classic 8-bit render
     * @param smoothing     Anti-aliasing
     * @return              False if cancelled
     */
    static boolean export(File file, List<AlcShape> shapes, Color background, Image image, Point imageLocation,
            int canvasWidth, int canvasHeight, int outWidth, double dpi, boolean exact, boolean smoothing,
            Progress progress) throws IOException {
        double scale = outWidth / (double) canvasWidth;
        int outHeight = (int) Math.round(canvasHeight * scale);
        AffineTransform at = AffineTransform.getScaleInstance(scale, scale);

        // Bounds of each shape in the output, to skip the shapes outside a band
        Rectangle[] bounds = new Rectangle[shapes.size()];
        for (int i = 0; i < shapes.size(); i++) {
            AlcShape shape = shapes.get(i);
            if (shape == null || shape.path == null) {
                continue;
            }
            Rectangle b = at.createTransformedShape(shape.path.getBounds2D()).getBounds();
            // Room for the line width and the anti-aliasing
            int grow = (int) Math.ceil(shape.lineWidth * scale) + 2;
            b.grow(grow, grow);
            bounds[i] = b;
        }

        int bytesPerPixel = exact ? 12 : 4;
        int bandRows = (int) Math.max(8, Math.min(outHeight, BAND_BYTES / ((long) outWidth * bytesPerPixel)));
        int[] row = new int[outWidth * 3];

        boolean ok = false;
        File partial = new File(file.getPath() + ".part");
        try (AlcPngWriter png = new AlcPngWriter(new FileOutputStream(partial), outWidth, outHeight, exact ? 16 : 8, dpi)) {
            for (int y0 = 0; y0 < outHeight; y0 += bandRows) {
                if (progress != null && progress.isCancelled()) {
                    return false;
                }
                int rows = Math.min(bandRows, outHeight - y0);
                // Background and image of the band
                BufferedImage base = new BufferedImage(outWidth, rows, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = base.createGraphics();
                g.setColor(background);
                g.fillRect(0, 0, outWidth, rows);
                g.translate(0, -y0);
                g.transform(at);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        smoothing ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                if (image != null) {
                    Point p = imageLocation != null ? imageLocation : new Point(0, 0);
                    g.drawImage(image, p.x, p.y, null);
                }
                Rectangle band = new Rectangle(0, y0, outWidth, rows);
                int[] px = ((DataBufferInt) base.getRaster().getDataBuffer()).getData();

                if (exact) {
                    g.dispose();
                    float[] acc = new float[outWidth * rows * 3];
                    for (int i = 0; i < px.length; i++) {
                        acc[i * 3] = ((px[i] >> 16) & 0xFF) / 255f;
                        acc[i * 3 + 1] = ((px[i] >> 8) & 0xFF) / 255f;
                        acc[i * 3 + 2] = (px[i] & 0xFF) / 255f;
                    }
                    for (int i = 0; i < shapes.size(); i++) {
                        if (bounds[i] != null && bounds[i].intersects(band)) {
                            AlcExactRenderer.composite(acc, outWidth, rows, 0, y0, shapes.get(i), at, smoothing);
                        }
                    }
                    for (int r = 0; r < rows; r++) {
                        int base3 = r * outWidth * 3;
                        for (int i = 0; i < outWidth * 3; i++) {
                            int v = Math.round(acc[base3 + i] * 65535f);
                            row[i] = v < 0 ? 0 : (v > 65535 ? 65535 : v);
                        }
                        png.writeRow16(row);
                    }
                } else {
                    // Classic: the same 8-bit drawing as the screen, at a larger scale
                    for (int i = 0; i < shapes.size(); i++) {
                        if (bounds[i] == null || !bounds[i].intersects(band)) {
                            continue;
                        }
                        AlcShape shape = shapes.get(i);
                        g.setPaint(shape.getPaint());
                        if (shape.style == AlcConstants.STYLE_STROKE) {
                            g.setStroke(new BasicStroke(shape.lineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL));
                            g.draw(shape.path);
                        } else {
                            g.fill(shape.path);
                        }
                    }
                    g.dispose();
                    for (int r = 0; r < rows; r++) {
                        for (int x = 0; x < outWidth; x++) {
                            int c = px[r * outWidth + x];
                            row[x * 3] = (c >> 16) & 0xFF;
                            row[x * 3 + 1] = (c >> 8) & 0xFF;
                            row[x * 3 + 2] = c & 0xFF;
                        }
                        png.writeRow8(row);
                    }
                }
                if (progress != null) {
                    progress.update((y0 + rows) / (double) outHeight);
                }
            }
            ok = true;
        } finally {
            if (ok) {
                if (file.exists()) {
                    file.delete();
                }
                if (!partial.renameTo(file)) {
                    throw new IOException("Could not write " + file);
                }
            } else {
                partial.delete();
            }
        }
        return true;
    }
}
