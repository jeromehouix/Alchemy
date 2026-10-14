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

import com.sun.pdfview.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.nio.channels.FileChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Reads the look of the shapes of a PDF (fill and stroke colors, opacities,
 * line width, transformations) from the PDFRenderer command list.
 *
 * PDFRenderer does not expose these, so like the original shape import this reads
 * the private fields of its command objects.
 */
final class AlcPdfImport implements AlcConstants {

    /** Graphics state of the PDF while walking its commands */
    static final class State implements Cloneable {

        Color fill = Color.BLACK, stroke = Color.BLACK;
        float fillAlpha = 1f, strokeAlpha = 1f, lineWidth = 1f;
        AffineTransform ctm = new AffineTransform();
        private Deque<State> stack;

        State() {
            stack = new ArrayDeque<State>();
        }

        private State copy() {
            State s = new State();
            s.fill = fill;
            s.stroke = stroke;
            s.fillAlpha = fillAlpha;
            s.strokeAlpha = strokeAlpha;
            s.lineWidth = lineWidth;
            s.ctm = new AffineTransform(ctm);
            return s;
        }

        /** Update the state with a command (colors, opacity, stroke, transform, save/restore) */
        void update(PDFCmd cmd) {
            String name = cmd.getClass().getSimpleName();
            try {
                switch (name) {
                    case "PDFFillPaintCmd":
                        fill = color(field(cmd, "p"), fill);
                        break;
                    case "PDFStrokePaintCmd":
                        stroke = color(field(cmd, "p"), stroke);
                        break;
                    case "PDFFillAlphaCmd":
                        fillAlpha = (Float) field(cmd, "a");
                        break;
                    case "PDFStrokeAlphaCmd":
                        strokeAlpha = (Float) field(cmd, "a");
                        break;
                    case "PDFChangeStrokeCmd":
                        float w = (Float) field(cmd, "w");
                        // PDFRenderer marks an unchanged width with a negative value
                        if (w >= 0) {
                            lineWidth = w;
                        }
                        break;
                    case "PDFXformCmd":
                        ctm.concatenate((AffineTransform) field(cmd, "at"));
                        break;
                    case "PDFPushCmd":
                        stack.push(copy());
                        break;
                    case "PDFPopCmd":
                        if (!stack.isEmpty()) {
                            State s = stack.pop();
                            fill = s.fill;
                            stroke = s.stroke;
                            fillAlpha = s.fillAlpha;
                            strokeAlpha = s.strokeAlpha;
                            lineWidth = s.lineWidth;
                            ctm = s.ctm;
                        }
                        break;
                    default:
                        break;
                }
            } catch (Exception ex) {
                // Unknown version of the command: keep the current state
            }
        }

        /** Remember the original look of a shape drawn with this state */
        void applySource(AlcShape shape, int pdfStyle, double scale) {
            if (pdfStyle == STYLE_STROKE) {
                shape.setSource(stroke, strokeAlpha * stroke.getAlpha() / 255f, STYLE_STROKE, (float) (lineWidth * scale));
            } else {
                shape.setSource(fill, fillAlpha * fill.getAlpha() / 255f, STYLE_FILL, (float) (lineWidth * scale));
            }
        }

        private static Object field(Object o, String name) throws Exception {
            Field f = o.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(o);
        }

        private static Color color(Object pdfPaint, Color previous) {
            if (pdfPaint instanceof PDFPaint) {
                Paint p = ((PDFPaint) pdfPaint).getPaint();
                if (p instanceof Color) {
                    Color c = (Color) p;
                    return new Color(c.getRed(), c.getGreen(), c.getBlue());
                }
            }
            return previous;
        }
    }

    private AlcPdfImport() {
    }

    /**
     * Read each page of a PDF as a whole illustration: all its shapes, in page
     * coordinates (y pointing down), with their original look
     *
     * @return One list of shapes per page (empty pages are skipped)
     */
    static List<List<AlcShape>> readIllustrations(File file) {
        List<List<AlcShape>> pages = new ArrayList<List<AlcShape>>();
        PDFFile pdfFile;
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            FileChannel channel = raf.getChannel();
            java.nio.ByteBuffer buf = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
            pdfFile = new PDFFile(buf);
        } catch (Exception ex) {
            System.err.println("Failed to load file " + file + ": " + ex);
            return pages;
        }

        for (int p = 0; p < pdfFile.getNumPages(); p++) {
            PDFPage page = pdfFile.getPage(p);
            BufferedImage token = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = token.createGraphics();
            try {
                // Run the renderer once to populate the command list
                PDFRenderer renderer = new PDFRenderer(page, g2, new Rectangle(0, 0, 1, 1), null, null);
                page.waitForFinish();
                renderer.run();

                double pageHeight = page.getHeight();
                // PDF space has y pointing up: flip it
                AffineTransform flip = new AffineTransform(1, 0, 0, -1, 0, pageHeight);
                State state = new State();
                List<AlcShape> shapes = new ArrayList<AlcShape>();
                int total = page.getCommandCount();
                for (int c = 0; c < total; c++) {
                    PDFCmd command = page.getCommand(c);
                    if (!(command instanceof PDFShapeCmd)) {
                        state.update(command);
                        continue;
                    }
                    GeneralPath gp = (GeneralPath) State.field(command, "gp");
                    int style = (Integer) State.field(command, "style");
                    if (style == STYLE_CLIP || gp == null) {
                        continue;
                    }
                    AffineTransform at = new AffineTransform(flip);
                    at.concatenate(state.ctm);
                    double scale = Math.sqrt(Math.abs(at.getDeterminant()));
                    GeneralPath path = (GeneralPath) gp.createTransformedShape(at);
                    // Skip a background covering the whole page
                    Rectangle bounds = path.getBounds();
                    if (bounds.width >= page.getWidth() && bounds.height >= pageHeight) {
                        continue;
                    }
                    if (style == STYLE_FILL || style == STYLE_BOTH) {
                        AlcShape shape = new AlcShape(path, Color.BLACK, 255, STYLE_FILL, 1f);
                        shape.path.setWindingRule(gp.getWindingRule());
                        state.applySource(shape, STYLE_FILL, scale);
                        shapes.add(shape);
                    }
                    if (style == STYLE_STROKE || style == STYLE_BOTH) {
                        AlcShape shape = new AlcShape((GeneralPath) path.clone(), Color.BLACK, 255, STYLE_STROKE, 1f);
                        state.applySource(shape, STYLE_STROKE, scale);
                        shapes.add(shape);
                    }
                }
                if (!shapes.isEmpty()) {
                    pages.add(shapes);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                g2.dispose();
            }
        }
        return pages;
    }
}
