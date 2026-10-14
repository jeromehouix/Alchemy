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
import java.awt.event.*;
import java.io.File;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.miginfocom.swing.MigLayout;

/**
 * Export for print: the size of the print in centimeters and its resolution in
 * dpi give the size of the image, rendered from the vectors (exact 16-bit or
 * classic 8-bit render).
 */
class AlcHighResDialog extends JDialog {

    private final JTextField widthField = new JTextField(6);
    private final JTextField heightField = new JTextField(6);
    private final JComboBox<String> dpiBox = new JComboBox<String>(new String[]{"150", "200", "240", "300", "360", "600"});
    private final JRadioButton exactButton = new JRadioButton();
    private final JRadioButton classicButton = new JRadioButton();
    private final JLabel pixelsLabel = new JLabel();
    private final NumberFormat number = NumberFormat.getNumberInstance();
    private boolean updating = false;
    private int canvasWidth, canvasHeight;

    AlcHighResDialog() {
        super(Alchemy.window, Alchemy.bundle.getString("hiresTitle"), true);
        number.setMaximumFractionDigits(1);

        JPanel content = new JPanel(new MigLayout("insets 18, gap 8 10", "[right][grow,fill][]"));
        content.add(new JLabel(Alchemy.bundle.getString("hiresWidth")));
        content.add(widthField);
        content.add(new JLabel("cm"), "wrap");
        content.add(new JLabel(Alchemy.bundle.getString("hiresHeight")));
        content.add(heightField);
        content.add(new JLabel("cm"), "wrap");
        content.add(new JLabel(Alchemy.bundle.getString("hiresDpi")));
        dpiBox.setEditable(true);
        content.add(dpiBox);
        content.add(new JLabel("dpi"), "wrap");
        pixelsLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        content.add(pixelsLabel, "skip, span 2, wrap 14");

        content.add(new JLabel(Alchemy.bundle.getString("hiresRender")), "top");
        ButtonGroup group = new ButtonGroup();
        exactButton.setText(Alchemy.bundle.getString("hiresExact"));
        classicButton.setText(Alchemy.bundle.getString("hiresClassic"));
        group.add(exactButton);
        group.add(classicButton);
        content.add(exactButton, "span 2, wrap");
        content.add(classicButton, "skip, span 2, wrap 18");

        JButton cancel = new JButton(Alchemy.bundle.getString("cancel"));
        JButton export = new JButton(Alchemy.bundle.getString("hiresExport"));
        cancel.addActionListener(e -> setVisible(false));
        export.addActionListener(e -> chooseFileAndExport());
        content.add(cancel, "skip, split 2, right");
        content.add(export, "wrap");
        getRootPane().setDefaultButton(export);
        setContentPane(content);

        // Width and height keep the proportions of the drawing
        widthField.getDocument().addDocumentListener(listener(true));
        heightField.getDocument().addDocumentListener(listener(false));
        dpiBox.addActionListener(e -> updatePixels());
        ((JTextField) dpiBox.getEditor().getEditorComponent()).getDocument().addDocumentListener(listener(null));
        getRootPane().registerKeyboardAction(e -> setVisible(false),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /** Show the dialog for the current drawing */
    void showWindow() {
        Rectangle canvas = Alchemy.canvas.getVisibleRect();
        canvasWidth = Math.max(1, canvas.width);
        canvasHeight = Math.max(1, canvas.height);
        updating = true;
        widthField.setText(number.format(AlcPreferences.prefs.getDouble("Print Width", 50)));
        dpiBox.setSelectedItem(String.valueOf(AlcPreferences.prefs.getInt("Print DPI", 300)));
        updating = false;
        syncFrom(true);
        if (AlcPreferences.prefs.getBoolean("Print Exact", true)) {
            exactButton.setSelected(true);
        } else {
            classicButton.setSelected(true);
        }
        pack();
        setLocationRelativeTo(Alchemy.window);
        setVisible(true);
    }

    private DocumentListener listener(final Boolean fromWidth) {
        return new DocumentListener() {

            public void insertUpdate(DocumentEvent e) {
                changed();
            }

            public void removeUpdate(DocumentEvent e) {
                changed();
            }

            public void changedUpdate(DocumentEvent e) {
                changed();
            }

            private void changed() {
                if (!updating) {
                    SwingUtilities.invokeLater(() -> {
                        if (fromWidth == null) {
                            updatePixels();
                        } else {
                            syncFrom(fromWidth);
                        }
                    });
                }
            }
        };
    }

    private double parse(String text) {
        try {
            return number.parse(text.trim()).doubleValue();
        } catch (ParseException | NullPointerException e) {
            return -1;
        }
    }

    private double dpi() {
        Object item = dpiBox.isEditable() ? dpiBox.getEditor().getItem() : dpiBox.getSelectedItem();
        return item == null ? -1 : parse(item.toString());
    }

    private void syncFrom(boolean fromWidth) {
        updating = true;
        if (fromWidth) {
            double w = parse(widthField.getText());
            if (w > 0) {
                heightField.setText(number.format(w * canvasHeight / canvasWidth));
            }
        } else {
            double h = parse(heightField.getText());
            if (h > 0) {
                widthField.setText(number.format(h * canvasWidth / canvasHeight));
            }
        }
        updating = false;
        updatePixels();
    }

    /** Width of the image in pixels, or -1 if the fields are not valid */
    private int outWidth() {
        double w = parse(widthField.getText());
        double dpi = dpi();
        if (w <= 0 || dpi <= 0) {
            return -1;
        }
        return (int) Math.round(w / 2.54 * dpi);
    }

    private void updatePixels() {
        int w = outWidth();
        if (w <= 0) {
            pixelsLabel.setText(" ");
            return;
        }
        int h = (int) Math.round(w * (double) canvasHeight / canvasWidth);
        NumberFormat n = NumberFormat.getIntegerInstance();
        pixelsLabel.setText(n.format(w) + " × " + n.format(h) + " px · " + n.format(Math.round(w * (double) h / 1e6)) + " Mpx");
    }

    private void chooseFileAndExport() {
        final int outWidth = outWidth();
        final double dpi = dpi();
        if (outWidth <= 0) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        // PNG is limited to 2^31 pixels per side; keep a sane limit for print software
        if (outWidth > 100000 || outWidth * (double) canvasHeight / canvasWidth > 100000) {
            JOptionPane.showMessageDialog(this, Alchemy.bundle.getString("hiresTooBig"));
            return;
        }
        AlcPreferences.prefs.putDouble("Print Width", parse(widthField.getText()));
        AlcPreferences.prefs.putInt("Print DPI", (int) Math.round(dpi));
        AlcPreferences.prefs.putBoolean("Print Exact", exactButton.isSelected());

        FileDialog chooser = new FileDialog(this, Alchemy.bundle.getString("hiresTitle"), FileDialog.SAVE);
        chooser.setDirectory(Alchemy.preferences.exportDirectory);
        chooser.setFile(Alchemy.bundle.getString("defaultFileName") + ".png");
        chooser.setVisible(true);
        if (chooser.getFile() == null) {
            return;
        }
        File file = new File(chooser.getDirectory(), chooser.getFile());
        if (!file.getName().toLowerCase().endsWith(".png")) {
            file = new File(file.getPath() + ".png");
        }
        Alchemy.preferences.exportDirectory = chooser.getDirectory();
        setVisible(false);
        run(file, outWidth, dpi, exactButton.isSelected());
    }

    /** Export in the background, with a progress window */
    static void run(final File file, final int outWidth, final double dpi, final boolean exact) {
        Rectangle canvas = Alchemy.canvas.getVisibleRect();
        final int cw = canvas.width, ch = canvas.height;
        final List<AlcShape> shapes = new ArrayList<AlcShape>();
        for (ArrayList list : Alchemy.canvas.fullShapeList) {
            shapes.addAll((List<AlcShape>) list);
        }
        final Color bg = Alchemy.canvas.getBackgroundColor();
        final boolean showImage = Alchemy.canvas.isImageDisplayEnabled() && Alchemy.canvas.isImageSet();
        final Image image = showImage ? Alchemy.canvas.getImage() : null;
        final Point imageLocation = showImage ? Alchemy.canvas.getImageLocation() : null;
        final boolean smoothing = Alchemy.canvas.isSmoothing();

        final JDialog progressWindow = new JDialog(Alchemy.window, Alchemy.bundle.getString("hiresTitle"), false);
        final JProgressBar bar = new JProgressBar(0, 1000);
        bar.setStringPainted(true);
        final JButton cancel = new JButton(Alchemy.bundle.getString("cancel"));
        JPanel p = new JPanel(new MigLayout("insets 18, gap 10", "[300!]"));
        p.add(new JLabel(Alchemy.bundle.getString("hiresProgress") + " " + file.getName()), "wrap");
        p.add(bar, "growx, wrap");
        p.add(cancel, "right");
        progressWindow.setContentPane(p);
        progressWindow.pack();
        progressWindow.setLocationRelativeTo(Alchemy.window);

        final java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean(false);
        final SwingWorker<Boolean, Double> worker = new SwingWorker<Boolean, Double>() {

            @Override
            protected Boolean doInBackground() throws Exception {
                return AlcHighResExport.export(file, shapes, bg, image, imageLocation, cw, ch, outWidth, dpi, exact, smoothing,
                        new AlcHighResExport.Progress() {

                            public void update(double fraction) {
                                publish(fraction);
                            }

                            public boolean isCancelled() {
                                return cancelled.get();
                            }
                        });
            }

            @Override
            protected void process(List<Double> chunks) {
                bar.setValue((int) Math.round(chunks.get(chunks.size() - 1) * 1000));
            }

            @Override
            protected void done() {
                progressWindow.dispose();
                if (cancelled.get()) {
                    return;
                }
                try {
                    get();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(Alchemy.window, Alchemy.bundle.getString("hiresFailed") + "\n" + ex.getCause());
                }
            }
        };
        cancel.addActionListener(e -> {
            cancelled.set(true);
            cancel.setEnabled(false);
        });
        worker.execute();
        progressWindow.setVisible(true);
    }
}
