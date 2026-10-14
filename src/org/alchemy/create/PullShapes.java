/*
 * This file is part of the Alchemy project - http://al.chemy.org
 * 
 * Copyright (c) 2007-2010 Karl D.D. Willis
 * 
 * Alchemy is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Alchemy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Alchemy.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.alchemy.create;

import eu.medsea.util.MimeUtil;
import java.awt.Desktop;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Rectangle;
import javax.swing.JOptionPane;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.awt.event.*;
import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.util.ArrayList;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.alchemy.core.*;

/**
 *
 * PullShapes.java
 */
public class PullShapes extends AlcModule implements AlcConstants {

    private AlcToolBarSubSection subToolBarSection;
    //
    // Timing
    private long mouseDelayGap = 51;
    private long mouseDelayTime;
    //
    private boolean hasFolders,  hasRootShapes,  hasShapes;
    private int currentFolder;
    private String[] folderNames;
    /** The folders of stamps, in the order of the folder selector (after "All Shapes") */
    private File[] folderFiles = new File[0];
    private ArrayList[] shapeLists;
    /** Whole illustrations (all the shapes of a PDF page or an SVG file), per folder */
    private ArrayList[] wholeLists;
    /** Stamp whole illustrations instead of single shapes */
    private boolean whole = false;
    /** Keep the colors of the illustrations instead of the current color */
    private boolean originalColors = false;
    /** Numbers the stamps, so the shapes of one stamp can be grouped in the SVG export */
    private static int stampCounter = 0;
    private AlcSubComboBox folderSelector;
    //
    private boolean scale = true;
    private boolean rotate = true;
    private float size = 2F;

    public PullShapes() {
    }

    @Override
    protected void setup() {
        loadShapes();
        createSubToolBarSection();
        toolBar.addSubToolBarSection(subToolBarSection);

    }

    @Override
    protected void reselect() {
        loadShapes();
        toolBar.addSubToolBarSection(subToolBarSection);
    }

    private void createSubToolBarSection() {
        subToolBarSection = new AlcToolBarSubSection(this);


        // Only add if there are folders present
        if (hasFolders) {
            folderSelector = new AlcSubComboBox(null);
            folderSelector.setToolTipText("Select which folder of shapes to use");
            setupFolderSelector();
            subToolBarSection.add(folderSelector);
        }

        // Reload
        AlcSubButton reloadButton = new AlcSubButton("Reload", AlcUtil.getUrlPath("reload.png", getClassLoader()));
        reloadButton.setToolTipText("Reload the stamps (PDF and SVG files) from the shapes folder: " + AlcPaths.shapesDir());
        reloadButton.addActionListener(
                new ActionListener() {

                    public void actionPerformed(ActionEvent e) {
                        reloadShapes(null);
                    }
                });
        subToolBarSection.add(reloadButton);

        // Add stamps: copy PDF and SVG files into the stamps folder
        AlcSubButton addButton = new AlcSubButton("Add", AlcUtil.getUrlPath("import.png", getClassLoader()));
        addButton.setToolTipText("Add PDF or SVG files to the stamps, in an existing or a new folder");
        addButton.addActionListener(
                new ActionListener() {

                    public void actionPerformed(ActionEvent e) {
                        importStamps();
                    }
                });
        subToolBarSection.add(addButton);

        // Show the stamps folder in the Finder / file manager
        AlcSubButton openButton = new AlcSubButton("Open", AlcUtil.getUrlPath("folder.png", getClassLoader()));
        openButton.setToolTipText("Open the stamps folder: " + AlcPaths.shapesDir());
        openButton.addActionListener(
                new ActionListener() {

                    public void actionPerformed(ActionEvent e) {
                        try {
                            Desktop.getDesktop().open(AlcPaths.shapesDir());
                        } catch (Exception ex) {
                            JOptionPane.showMessageDialog(null, "The stamps folder is:\n" + AlcPaths.shapesDir());
                        }
                    }
                });
        subToolBarSection.add(openButton);


        // Spacing Slider
        int initialSliderValue = 25;
        final AlcSubSlider spacingSlider = new AlcSubSlider("Spacing", 0, 100, initialSliderValue);
        spacingSlider.setToolTipText("Adjust the spacing interval");
        spacingSlider.addChangeListener(
                new ChangeListener() {

                    public void stateChanged(ChangeEvent e) {
                        if (!spacingSlider.getValueIsAdjusting()) {
                            int value = spacingSlider.getValue();
                            mouseDelayGap = 1 + value * 2;
                        }
                    }
                });
        subToolBarSection.add(spacingSlider);

        // Scale button
//        AlcSubToggleButton scaleButton = new AlcSubToggleButton("Scale", AlcUtil.getUrlPath("scale.png", getClassLoader()));
//        scaleButton.setSelected(scale);
//        scaleButton.setToolTipText("Toggle random scaling");
//        scaleButton.addActionListener(
//                new ActionListener() {
//
//                    public void actionPerformed(ActionEvent e) {
//                        scale = !scale;
//                    }
//                });
//        subToolBarSection.add(scaleButton);


        // Size Slider
        final AlcSubSlider sizeSlider = new AlcSubSlider("Size", 1, 50, (int) size * 10);
        sizeSlider.setToolTipText("Adjust the size of shapes created");
        sizeSlider.addChangeListener(
                new ChangeListener() {

                    public void stateChanged(ChangeEvent e) {
                        if (!sizeSlider.getValueIsAdjusting()) {
                            size = 0.1F + sizeSlider.getValue() / 10F;
                        }
                    }
                });
        subToolBarSection.add(sizeSlider);

        // Rotate button
        AlcSubToggleButton rotateButton = new AlcSubToggleButton("Rotate", AlcUtil.getUrlPath("rotate.png", getClassLoader()));
        rotateButton.setSelected(rotate);
        rotateButton.setToolTipText("Toggle random rotation");
        rotateButton.addActionListener(
                new ActionListener() {

                    public void actionPerformed(ActionEvent e) {
                        rotate = !rotate;
                    }
                });
        subToolBarSection.add(rotateButton);

        // Whole illustrations
        AlcSubToggleButton wholeButton = new AlcSubToggleButton("Whole", AlcUtil.getUrlPath("whole.png", getClassLoader()));
        wholeButton.setSelected(whole);
        wholeButton.setToolTipText("Stamp whole illustrations (every shape of a PDF page or an SVG file) instead of single shapes");
        wholeButton.addActionListener(
                new ActionListener() {

                    public void actionPerformed(ActionEvent e) {
                        whole = !whole;
                    }
                });
        subToolBarSection.add(wholeButton);

        // Original colors
        AlcSubToggleButton colorsButton = new AlcSubToggleButton("Colours", AlcUtil.getUrlPath("colours.png", getClassLoader()));
        colorsButton.setSelected(originalColors);
        colorsButton.setToolTipText("Keep the colours of the stamps (the current opacity still applies) instead of the current colour");
        colorsButton.addActionListener(
                new ActionListener() {

                    public void actionPerformed(ActionEvent e) {
                        originalColors = !originalColors;
                    }
                });
        subToolBarSection.add(colorsButton);
    }

    private void setupFolderSelector() {
        for (int i = 0; i < folderNames.length; i++) {
            folderSelector.addItem(folderNames[i]);
        }
    }

    private void loadShapes() {
        // Initialise variables
        hasFolders = false;
        hasRootShapes = false;
        hasShapes = false;
        currentFolder = 0;
        shapeLists = null;
        wholeLists = null;
        folderNames = null;

        // Folder of the stamps
        File shapesDir = AlcPaths.shapesDir();

        // Filter for the folders not starting with '.'
        FileFilter folderFilter = new FileFilter() {

            public boolean accept(File pathname) {
                return pathname.isDirectory() && !pathname.getName().startsWith(".");
            }
        };

        // Filter for the stamp files: PDF and SVG
        FilenameFilter stampFilter = new FilenameFilter() {

            public boolean accept(File dir, String name) {
                if (name.startsWith(".")) {
                    return false;
                }
                String lower = name.toLowerCase();
                if (lower.endsWith(".svg") || lower.endsWith(".pdf")) {
                    return true;
                }
                // PDF files without the extension (MIME types are not working on Linux)
                if (Alchemy.OS == OS_LINUX) {
                    return false;
                }
                try {
                    File file = new File(dir, name);
                    return file.isFile() && "application/pdf".equals(MimeUtil.getMimeType(file.getAbsoluteFile()));
                } catch (Exception ex) {
                    return false;
                }
            }
        };

        File[] folders = shapesDir.listFiles(folderFilter);
        if (folders == null) {
            folders = new File[0];
        }
        java.util.Arrays.sort(folders);
        folderFiles = folders;
        File[] rootFiles = shapesDir.listFiles(stampFilter);

        ArrayList<ArrayList<AlcShape>> pieces = new ArrayList<ArrayList<AlcShape>>();
        ArrayList<ArrayList<AlcShape[]>> wholes = new ArrayList<ArrayList<AlcShape[]>>();

        if (rootFiles != null && rootFiles.length > 0) {
            hasRootShapes = true;
            java.util.Arrays.sort(rootFiles);
            ArrayList<AlcShape> rootPieces = new ArrayList<AlcShape>();
            ArrayList<AlcShape[]> rootWholes = new ArrayList<AlcShape[]>();
            loadFiles(rootFiles, rootPieces, rootWholes);
            pieces.add(rootPieces);
            wholes.add(rootWholes);
        }

        if (folders.length > 0) {
            hasFolders = true;
            // Add an extra slot for 'ALL' shapes
            folderNames = new String[folders.length + 1];
            folderNames[0] = "All Shapes";
            for (int i = 0; i < folders.length; i++) {
                File[] files = AlcUtil.listFilesAsArray(folders[i], stampFilter, true);
                java.util.Arrays.sort(files);
                ArrayList<AlcShape> folderPieces = new ArrayList<AlcShape>();
                ArrayList<AlcShape[]> folderWholes = new ArrayList<AlcShape[]>();
                loadFiles(files, folderPieces, folderWholes);
                pieces.add(folderPieces);
                wholes.add(folderWholes);
                folderNames[i + 1] = folders[i].getName() + " Folder";
            }
        }

        shapeLists = pieces.toArray(new ArrayList[pieces.size()]);
        wholeLists = wholes.toArray(new ArrayList[wholes.size()]);
        for (ArrayList<AlcShape> list : pieces) {
            if (!list.isEmpty()) {
                hasShapes = true;
            }
        }
        if (!hasShapes) {
            AlcUtil.showNoShapesDialog();
        }
    }

    /**
     * Reload the stamps and refresh the folder selector
     * @param selectFolder  Folder to select afterwards, or null to keep "All Shapes"
     */
    private void reloadShapes(File selectFolder) {
        loadShapes();
        if (folderSelector == null && hasFolders) {
            // First folder created: the selector did not exist yet
            folderSelector = new AlcSubComboBox(null);
            folderSelector.setToolTipText("Select which folder of shapes to use");
            subToolBarSection.add(folderSelector);
            subToolBarSection.revalidate();
        }
        if (folderSelector != null) {
            folderSelector.removeAllItems();
            if (folderNames != null) {
                setupFolderSelector();
            }
            if (selectFolder != null) {
                for (int i = 0; i < folderFiles.length; i++) {
                    if (folderFiles[i].equals(selectFolder)) {
                        folderSelector.setSelectedIndex(i + 1);
                    }
                }
            }
        }
    }

    /** Choose PDF and SVG files and copy them into a folder of stamps */
    private void importStamps() {
        File shapesDir = AlcPaths.shapesDir();
        // 1. The files
        FileDialog chooser = new FileDialog((Frame) null, "Add stamps (PDF or SVG)", FileDialog.LOAD);
        chooser.setMultipleMode(true);
        chooser.setFilenameFilter(new FilenameFilter() {

            public boolean accept(File dir, String name) {
                String lower = name.toLowerCase();
                return lower.endsWith(".pdf") || lower.endsWith(".svg");
            }
        });
        chooser.setVisible(true);
        File[] files = chooser.getFiles();
        java.util.List<File> stamps = new ArrayList<File>();
        for (File f : files) {
            String lower = f.getName().toLowerCase();
            if (f.isFile() && (lower.endsWith(".pdf") || lower.endsWith(".svg"))) {
                stamps.add(f);
            }
        }
        if (stamps.isEmpty()) {
            if (files.length > 0) {
                JOptionPane.showMessageDialog(null, "Only PDF and SVG files can be used as stamps.");
            }
            return;
        }

        // 2. The destination: the selected folder by default, or a new one
        String newFolder = "New folder...";
        java.util.List<String> choices = new ArrayList<String>();
        for (File folder : folderFiles) {
            choices.add(folder.getName());
        }
        choices.add(newFolder);
        String preselected = newFolder;
        int selected = folderSelector != null ? folderSelector.getSelectedIndex() : 0;
        if (selected > 0 && selected - 1 < folderFiles.length) {
            preselected = folderFiles[selected - 1].getName();
        } else if (folderFiles.length > 0) {
            preselected = folderFiles[0].getName();
        }
        Object choice = JOptionPane.showInputDialog(null,
                stamps.size() + (stamps.size() > 1 ? " files" : " file") + " to add. Folder of stamps:",
                "Add stamps", JOptionPane.PLAIN_MESSAGE, null, choices.toArray(), preselected);
        if (choice == null) {
            return;
        }
        File destination;
        if (choice.equals(newFolder)) {
            String name = JOptionPane.showInputDialog(null, "Name of the new folder:", "Add stamps", JOptionPane.PLAIN_MESSAGE);
            if (name == null || name.trim().isEmpty()) {
                return;
            }
            destination = new File(shapesDir, name.trim().replaceAll("[/\\\\:]", "-"));
        } else {
            destination = new File(shapesDir, choice.toString());
        }

        // 3. Copy, then reload and select the folder
        try {
            java.util.List<String> unreadable = importFiles(stamps, destination);
            if (!unreadable.isEmpty()) {
                JOptionPane.showMessageDialog(null, stamps.size() + " file(s) added to " + destination.getName()
                        + ".\nNo shape could be read from: " + String.join(", ", unreadable));
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null, "Could not copy the files into " + destination + ":\n" + ex.getMessage());
        }
    }

    /**
     * Copy stamp files into a folder (created if needed) without overwriting
     * files of the same name, then reload the stamps and select that folder
     *
     * @return The names of the files from which no shape could be read
     */
    java.util.List<String> importFiles(java.util.List<File> stamps, File destination) throws java.io.IOException {
        java.util.List<String> unreadable = new ArrayList<String>();
        java.nio.file.Files.createDirectories(destination.toPath());
        for (File f : stamps) {
            File target = new File(destination, f.getName());
            int n = 2;
            while (target.exists()) {
                String name = f.getName();
                int dot = name.lastIndexOf('.');
                target = new File(destination, name.substring(0, dot) + " " + n + name.substring(dot));
                n++;
            }
            java.nio.file.Files.copy(f.toPath(), target.toPath());
            // Check that the file gives at least one shape
            boolean readable = target.getName().toLowerCase().endsWith(".svg")
                    ? !AlcUtil.getSVGShapes(target).isEmpty()
                    : AlcUtil.getPDFShapes(target, true, 0) != null;
            if (!readable) {
                unreadable.add(f.getName());
            }
        }
        reloadShapes(destination);
        return unreadable;
    }

    /** Read the stamps of PDF and SVG files: single shapes, and whole illustrations */
    private void loadFiles(File[] files, ArrayList<AlcShape> pieces, ArrayList<AlcShape[]> wholes) {
        for (File file : files) {
            if (file.getName().toLowerCase().endsWith(".svg")) {
                List<AlcShape> shapes = AlcUtil.getSVGShapes(file);
                for (AlcShape shape : shapes) {
                    pieces.add(moveToOrigin(new AlcShape[]{shape})[0]);
                }
                if (!shapes.isEmpty()) {
                    wholes.add(moveToOrigin(shapes.toArray(new AlcShape[shapes.size()])));
                }
            } else {
                java.util.Collection<AlcShape> shapes = AlcUtil.getPDFShapes(file, true, 0);
                if (shapes != null) {
                    pieces.addAll(shapes);
                }
                for (List<AlcShape> page : AlcUtil.getPDFIllustrations(file)) {
                    wholes.add(moveToOrigin(page.toArray(new AlcShape[page.size()])));
                }
            }
        }
    }

    /** Copies of the shapes, moved together so their bounds start at 0,0 */
    private static AlcShape[] moveToOrigin(AlcShape[] group) {
        Rectangle2D bounds = groupBounds(group);
        AffineTransform at = AffineTransform.getTranslateInstance(-bounds.getX(), -bounds.getY());
        AlcShape[] out = new AlcShape[group.length];
        for (int i = 0; i < group.length; i++) {
            out[i] = (AlcShape) group[i].clone();
            out[i].setPath(transformed(group[i].getPath(), at));
        }
        return out;
    }

    private static Rectangle2D groupBounds(AlcShape[] group) {
        Rectangle2D bounds = null;
        for (AlcShape shape : group) {
            Rectangle2D b = shape.getPath().getBounds2D();
            if (bounds == null) {
                bounds = b;
            } else {
                bounds.add(b);
            }
        }
        return bounds == null ? new Rectangle2D.Double() : bounds;
    }

    private static GeneralPath transformed(GeneralPath path, AffineTransform at) {
        GeneralPath out = (GeneralPath) path.clone();
        out.transform(at);
        return out;
    }

    /** The list index for the current folder ("All Shapes" picks a random one) */
    private int listIndex() {
        int folder = currentFolder;
        if (!hasRootShapes) {
            folder = currentFolder - 1;
        }
        // If set to "All Shapes"
        if (currentFolder == 0 && hasFolders) {
            folder = (int) math.random(shapeLists.length);
        }
        return Math.max(0, Math.min(shapeLists.length - 1, folder));
    }

    private void addRandomShape(MouseEvent e) {
        int folder = listIndex();
        if (whole && wholeLists[folder].size() > 0) {
            addRandomIllustration(e, folder);
            return;
        }
        if (shapeLists[folder].size() == 0) {
            return;
        }
        int rand = (int) math.random(shapeLists[folder].size());
        AlcShape shape = (AlcShape) shapeLists[folder].get(rand);
        // Clone the shape
        AlcShape cloneShape = (AlcShape) shape.clone();
        float scaleFactor = 1F;
        if (scale) {
            // Scale it
            scaleFactor = math.random(0.1F, size);
            cloneShape.scale(scaleFactor, scaleFactor);
        }
        if (rotate) {
            // Rotate it
            float rotation = math.random(0.1F, 57.2958F);
            cloneShape.rotate(rotation);
        }
        // Move it into place
        Rectangle bounds = cloneShape.getBounds();
        Point2D.Float p = point(e);
        double x = p.x - (bounds.width >> 1);
        double y = p.y - (bounds.height >> 1);
        cloneShape.move(x, y);

        cloneShape.setup();
        if (originalColors && cloneShape.hasSourceColor()) {
            cloneShape.applySource(canvas.getOpacity());
            cloneShape.setLineWidth(cloneShape.getLineWidth() * scaleFactor);
        }
        cloneShape.setStampId(++stampCounter);
        canvas.createShapes.add(cloneShape);
        canvas.redraw();
    }

    /** Stamp a whole illustration, scaled and rotated as one */
    private void addRandomIllustration(MouseEvent e, int folder) {
        int rand = (int) math.random(wholeLists[folder].size());
        AlcShape[] group = (AlcShape[]) wholeLists[folder].get(rand);
        Rectangle2D bounds = groupBounds(group);

        float scaleFactor = scale ? math.random(0.1F, size) : 1F;
        double rotation = rotate ? math.random(0.1F, 57.2958F) : 0;
        AffineTransform at = new AffineTransform();
        Point2D.Float p = point(e);
        at.translate(p.x, p.y);
        at.rotate(rotation);
        at.scale(scaleFactor, scaleFactor);
        at.translate(-bounds.getCenterX(), -bounds.getCenterY());

        int stampId = ++stampCounter;
        for (AlcShape shape : group) {
            AlcShape cloneShape = (AlcShape) shape.clone();
            cloneShape.setPath(transformed(shape.getPath(), at));
            cloneShape.setup();
            if (originalColors && cloneShape.hasSourceColor()) {
                cloneShape.applySource(canvas.getOpacity());
                cloneShape.setLineWidth(cloneShape.getLineWidth() * scaleFactor);
            }
            cloneShape.setStampId(stampId);
            canvas.createShapes.add(cloneShape);
        }
        canvas.redraw();
    }
    //expandedPath = (GeneralPath) currentPath.createTransformedShape(getScaleTransform(adjustedLevel, rect));
//    private GeneralPath scaleShape(GeneralPath gp){
//        
//    }
    /* Gets a scale transform based on the sound level */
//    private AffineTransform getScaleTransform(double level, Rectangle rect) {
//        AffineTransform scaleTransform = new AffineTransform();
//
//        double offsetX = rect.x + (rect.width / 2);
//        double offsetY = rect.y + (rect.height / 2);
//
//        scaleTransform.translate(offsetX, offsetY);
//        scaleTransform.scale(level, level);
//        scaleTransform.translate(-offsetX, -offsetY);
//
//        return scaleTransform;
//    }
    private int getFolder() {
        int folder = 0;
        if (hasFolders) {
            folder = folderSelector.getSelectedIndex();
        }
        return folder;
    }

    @Override
    public void mousePressed(MouseEvent e) {

        if (hasShapes) {
            currentFolder = getFolder();
            //System.out.println("Current Folder = " + currentFolder);
            mouseDelayTime = System.currentTimeMillis();
            addRandomShape(e);
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (hasShapes) {

            if (System.currentTimeMillis() - mouseDelayTime >= mouseDelayGap) {
                mouseDelayTime = System.currentTimeMillis();
                //System.out.println(e.getPoint());
                addRandomShape(e);
            }
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (hasShapes) {
            canvas.commitShapes();
        }
    }
}
