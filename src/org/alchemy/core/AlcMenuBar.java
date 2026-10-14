/*
 *  This file is part of the Alchemy project - http://al.chemy.org
 * 
 *  Copyright (c) 2007-2010 Karl D.D. Willis
 *  Copyright (c) 2009 Steren Giannini (steren.giannini@gmail.com)
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
 * 
 */
package org.alchemy.core;

import java.awt.*;
import java.awt.event.*;
import java.awt.print.*;
import java.io.File;
import java.io.IOException;
import javax.swing.*;

/** 
 * Menubar for Alchemy
 * Housing the usual file, print etc... commands 
 */
class AlcMenuBar extends JMenuBar implements AlcConstants {

    private final static int height = 27;
    private File platformAppDir;
    private PrinterJob printer = null;
    private PageFormat page = null;
    private PageFormat defaultPage = null;
    /** Session stuff global so it can be enabled/disabled */
    private AlcMenuItem nextPageItem,  previousPageItem,  unloadSessionItem;
    /** Unload Background Image - Disabled from Canvas */
    AlcMenuItem unloadBackgroundImageItem;
    private AlcCheckBoxMenuItem linkSessionItem;
    /** Fullscreen toggle global so it can be set on startup */
    AlcCheckBoxMenuItem fullScreenItem, transparentItem;
    /** File > Export for print */
    private AlcHighResDialog hiresDialog;
    /** View > Exact render, kept in sync with the toolbar button */
    private AlcCheckBoxMenuItem exactRenderItem;

    void refreshExactRender() {
        if (exactRenderItem != null) {
            exactRenderItem.setState(Alchemy.canvas.isExactRender());
        }
    }
    /** Export dialog to set image size */
    private AlcExportDialog exportDialog;

    /** Creates a new instance of AlcMenuBar */
    AlcMenuBar() {

        // Default applications directory depending on the platform
        switch (Alchemy.OS) {
            case OS_MAC:
                platformAppDir = new File(File.separator + "Applications");
                this.setBorderPainted(false);
                break;
            case OS_WINDOWS:
                platformAppDir = new File(File.separator + "Program Files");
                break;
            case OS_LINUX:
                platformAppDir = new File(File.separator + "usr" + File.separator + "bin");
                this.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
                break;
        }
        if (!AlcTheme.isModern()) {
            this.setBackground(AlcToolBar.COLOR_UI_HIGHLIGHT);
        }

        // Recording interval array in milliseconds
        int[] recordingInterval = {500, 1000, 5000, 15000, 30000, 60000, 120000, 300000, 600000};
        // Recording interval array in readable form
        String[] recordingIntervalString = new String[recordingInterval.length];
        // Initialise the array of recording intervals from the bundle
        for (int i = 0; i < recordingIntervalString.length; i++) {
            recordingIntervalString[i] = getS("interval" + recordingInterval[i]);
        }

        //////////////////////////////////////////////////////////////
        // FILE MENU
        //////////////////////////////////////////////////////////////
        AlcMenu fileMenu = new AlcMenu(getS("fileTitle"));

        // New
        AbstractAction newAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                if (Alchemy.canvas.shapes.size() > 0) {
                    boolean result = AlcUtil.showConfirmDialogFromBundle("newWinDialogTitle", "newWinDialogMessage", "newMacDialogTitle", "newMacDialogMessage");
                    if (result) {
                        Alchemy.canvas.clear();
                    }
                }
            }
        };
        AlcMenuItem newItem = new AlcMenuItem(newAction);
        int newKey = Alchemy.shortcuts.setShortcut(newItem, KeyEvent.VK_N, "newTitle", newAction, KEY_MODIFIER);
        newItem.setup(getS("newTitle"), newKey);
        fileMenu.add(newItem);

        fileMenu.add(new JSeparator());

        // Import stamps into Pull Shapes
        AbstractAction importAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                importStamps();
            }
        };
        AlcMenuItem importItem = new AlcMenuItem(importAction);
        int importKey = Alchemy.shortcuts.setShortcut(importItem, KeyEvent.VK_I, "importStampsTitle", importAction, KEY_MODIFIER);
        importItem.setup(getS("importStampsTitle"), importKey);
        fileMenu.add(importItem);

        fileMenu.add(new JSeparator());

        // Export: one item per format
        AlcMenu exportMenu = submenu(new AlcMenu(getS("exportMenuTitle")));
        AbstractAction exportSvgAction = exportAction("SVG");
        AlcMenuItem exportSvgItem = new AlcMenuItem(exportSvgAction);
        int exportKey = Alchemy.shortcuts.setShortcut(exportSvgItem, KeyEvent.VK_E, "exportSvgTitle", exportSvgAction, KEY_MODIFIER);
        exportSvgItem.setup(getS("exportSvgTitle"), exportKey);
        exportMenu.add(exportSvgItem);
        exportMenu.add(exportItem("exportPdfTitle", "PDF"));
        exportMenu.add(new JSeparator());
        exportMenu.add(exportItem("exportPngTitle", "PNG"));
        exportMenu.add(exportItem("exportPng16MenuTitle", getS("exportPng16Title")));
        exportMenu.add(exportItem("exportJpgTitle", "JPG"));
        fileMenu.add(exportMenu);

        // Export for print: large images rendered from the vectors
        AbstractAction hiresAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                if (hiresDialog == null) {
                    hiresDialog = new AlcHighResDialog();
                }
                hiresDialog.showWindow();
            }
        };
        AlcMenuItem hiresItem = new AlcMenuItem(hiresAction);
        int hiresKey = Alchemy.shortcuts.setShortcut(hiresItem, KeyEvent.VK_E, "hiresTitle", hiresAction, KEY_MODIFIER | KeyEvent.SHIFT_MASK);
        hiresItem.setup(getS("hiresTitle"));
        hiresItem.setAccelerator(KeyStroke.getKeyStroke(hiresKey, KEY_MODIFIER | KeyEvent.SHIFT_MASK));
        fileMenu.add(hiresItem);

        // Open the drawing in another application
        fileMenu.add(switchMenu());

        fileMenu.add(new JSeparator());

        // Page Setup
        AbstractAction pageAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                pageSetup();
            }
        };
        AlcMenuItem pageItem = new AlcMenuItem(pageAction);
        pageItem.setup(getS("pageSetupTitle"));
        fileMenu.add(pageItem);

        // Print
        AbstractAction printAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                print();
            }
        };
        AlcMenuItem printItem = new AlcMenuItem(printAction);
        int printKey = Alchemy.shortcuts.setShortcut(printItem, KeyEvent.VK_P, "printTitle", printAction, KEY_MODIFIER);
        printItem.setup(getS("printTitle"), printKey);
        fileMenu.add(printItem);

        // Exit - in the application menu on a Mac
        if (Alchemy.OS != OS_MAC) {
            fileMenu.add(new JSeparator());
            AbstractAction exitAction = new AbstractAction() {

                public void actionPerformed(ActionEvent e) {
                    Alchemy.window.exitAlchemy();
                }
            };
            AlcMenuItem exitItem = new AlcMenuItem(exitAction);
            exitItem.setup(getS("exitTitle"));
            fileMenu.add(exitItem);
        }

        this.add(fileMenu);

        //////////////////////////////////////////////////////////////
        // EDIT MENU
        //////////////////////////////////////////////////////////////
        AlcMenu editMenu = new AlcMenu(getS("editTitle"));

        // Undo and clear: the actions of the toolbar
        editMenu.add(forwardItem("undoTitle"));

        AbstractAction copyAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                copy();
            }
        };
        AlcMenuItem copyItem = new AlcMenuItem(copyAction);
        int copyKey = Alchemy.shortcuts.setShortcut(copyItem, KeyEvent.VK_C, "copyTitle", copyAction, KEY_MODIFIER);
        copyItem.setup(getS("copyTitle"), copyKey);
        editMenu.add(copyItem);

        AlcMenuItem clearItem = forwardItem("clearTitle");
        clearItem.setText(getS("clearDescription"));
        editMenu.add(clearItem);

        editMenu.add(new JSeparator());

        // Flip Horizontal
        AbstractAction flipHorizontalAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                Alchemy.canvas.flipHorizontal();
            }
        };
        AlcMenuItem flipHorizontalItem = new AlcMenuItem(flipHorizontalAction);
        int flipHorizontalKey = Alchemy.shortcuts.setShortcut(flipHorizontalItem, KeyEvent.VK_SEMICOLON, "flipHorizontal", flipHorizontalAction, KEY_MODIFIER);
        flipHorizontalItem.setup(getS("flipHorizontal"), flipHorizontalKey);
        editMenu.add(flipHorizontalItem);

        // Flip Vertical
        AbstractAction flipVerticalAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                Alchemy.canvas.flipVertical();
            }
        };
        AlcMenuItem flipVerticalItem = new AlcMenuItem(flipVerticalAction);
        int flipVerticalKey = Alchemy.shortcuts.setShortcut(flipVerticalItem, KeyEvent.VK_QUOTE, "flipVertical", flipVerticalAction, KEY_MODIFIER);
        flipVerticalItem.setup(getS("flipVertical"), flipVerticalKey);
        editMenu.add(flipVerticalItem);

        this.add(editMenu);


        //////////////////////////////////////////////////////////////
        // VIEW MENU
        //////////////////////////////////////////////////////////////
        AlcMenu viewMenu = new AlcMenu(getS("viewTitle"));

        // Fullscreen
        String fullScreenTitle = getS("fullScreenTitle");
        fullScreenItem = new AlcCheckBoxMenuItem();
        AbstractAction fullScreenAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                String source = e.getSource().getClass().getName();
                if (!source.equals("org.alchemy.core.AlcCheckBoxMenuItem")) {
                    fullScreenItem.setState(!fullScreenItem.getState());
                }
                Alchemy.window.setFullscreen(!Alchemy.window.isFullscreen());
            }
        };

        fullScreenItem.setAction(fullScreenAction);
        // Shortcut - Modifier f
        int fullScreenKey = Alchemy.shortcuts.setShortcut(fullScreenItem, KeyEvent.VK_F, "fullScreenTitle", fullScreenAction, KEY_MODIFIER);
        fullScreenItem.setup(fullScreenTitle, fullScreenKey);
        viewMenu.add(fullScreenItem);


        // Transparent Fullscreen
        String transparentTitle = getS("transparentTitle");
        transparentItem = new AlcCheckBoxMenuItem();
        if (Alchemy.window.isTransparent()) {
            transparentItem.setSelected(true);
        }
        AbstractAction transparentAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                String source = e.getSource().getClass().getName();
                if (!source.equals("org.alchemy.core.AlcCheckBoxMenuItem")) {
                    transparentItem.setState(!transparentItem.getState());
                }
                Alchemy.window.setTransparent(!Alchemy.window.isTransparent());
            }
        };

        transparentItem.setAction(transparentAction);
        // Shortcut - Modifier t
        int transparentKey = Alchemy.shortcuts.setShortcut(transparentItem, KeyEvent.VK_T, "transparentTitle", transparentAction, KEY_MODIFIER);
        transparentItem.setup(transparentTitle, transparentKey);
        viewMenu.add(transparentItem);

        // Hide or show the toolbars (Space)
        viewMenu.add(forwardItem("toggleToolBar"));

        viewMenu.add(new JSeparator());

        // Exact render: composite the layers in high precision
        AbstractAction exactRenderAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                AlcCheckBoxMenuItem source = (AlcCheckBoxMenuItem) e.getSource();
                Alchemy.canvas.setExactRender(source.getState());
                Alchemy.toolBar.refreshExactRender();
            }
        };
        exactRenderItem = new AlcCheckBoxMenuItem(exactRenderAction);
        exactRenderItem.setup(getS("exactRenderTitle"));
        exactRenderItem.setToolTipText(getS("exactRenderDescription"));
        exactRenderItem.setState(Alchemy.canvas.isExactRender());
        viewMenu.add(exactRenderItem);

        // Smoothing (anti-aliasing) of the display and the bitmap exports
        AbstractAction smoothingAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                AlcCheckBoxMenuItem source = (AlcCheckBoxMenuItem) e.getSource();
                Alchemy.canvas.setSmoothing(source.getState());
                Alchemy.canvas.redraw();
            }
        };
        AlcCheckBoxMenuItem smoothingItem = new AlcCheckBoxMenuItem(smoothingAction);
        smoothingItem.setSelected(Alchemy.preferences.smoothing);
        smoothingItem.setup(getS("smoothingTitle"));
        viewMenu.add(smoothingItem);

        viewMenu.add(new JSeparator());

        // Zoom: the view only, the drawing and its exports do not change
        AbstractAction zoomInAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                Alchemy.canvas.zoomCentered(1.25);
            }
        };
        AlcMenuItem zoomInItem = new AlcMenuItem(zoomInAction);
        int zoomInKey = Alchemy.shortcuts.setShortcut(zoomInItem, KeyEvent.VK_EQUALS, "zoomInTitle", zoomInAction, KEY_MODIFIER);
        zoomInItem.setup(getS("zoomInTitle"), zoomInKey);
        viewMenu.add(zoomInItem);

        AbstractAction zoomOutAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                Alchemy.canvas.zoomCentered(1 / 1.25);
            }
        };
        AlcMenuItem zoomOutItem = new AlcMenuItem(zoomOutAction);
        int zoomOutKey = Alchemy.shortcuts.setShortcut(zoomOutItem, KeyEvent.VK_MINUS, "zoomOutTitle", zoomOutAction, KEY_MODIFIER);
        zoomOutItem.setup(getS("zoomOutTitle"), zoomOutKey);
        viewMenu.add(zoomOutItem);

        AbstractAction zoomResetAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                Alchemy.canvas.resetView();
            }
        };
        AlcMenuItem zoomResetItem = new AlcMenuItem(zoomResetAction);
        int zoomResetKey = Alchemy.shortcuts.setShortcut(zoomResetItem, KeyEvent.VK_0, "zoomResetTitle", zoomResetAction, KEY_MODIFIER | KeyEvent.SHIFT_MASK);
        zoomResetItem.setup(getS("zoomResetTitle"), zoomResetKey);
        zoomResetItem.setAccelerator(KeyStroke.getKeyStroke(zoomResetKey, KEY_MODIFIER | KeyEvent.SHIFT_MASK));
        viewMenu.add(zoomResetItem);

        // Other keyboards: + and - of the numeric keypad, Cmd Shift = (Cmd +)
        for (JRootPane root : new JRootPane[]{Alchemy.window.getRootPane(), Alchemy.palette.getRootPane()}) {
            InputMap map = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
            map.put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, KEY_MODIFIER), "zoomInExtra");
            map.put(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, KEY_MODIFIER), "zoomInExtra");
            map.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, KEY_MODIFIER | KeyEvent.SHIFT_MASK), "zoomInExtra");
            map.put(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, KEY_MODIFIER), "zoomOutExtra");
            root.getActionMap().put("zoomInExtra", zoomInAction);
            root.getActionMap().put("zoomOutExtra", zoomOutAction);
        }

        viewMenu.add(new JSeparator());

        // Load Background Image
        String loadBackgroundImageTitle = getS("loadBackgroundImageTitle");
        AlcMenuItem loadBackgroundImageItem = new AlcMenuItem(loadBackgroundImageTitle);
        AbstractAction loadBackgroundImageAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {

                File file = AlcUtil.showFileChooser(new File(DIR_DESKTOP), false);
                if (file != null && file.exists()) {
                    Image image = null;
                    try {
                        image = AlcUtil.getImage(file.toURI().toURL());
                    } catch (Exception ex) {
                        // Ignore
                        }
                    if (image != null) {
                        Rectangle visibleRect = Alchemy.canvas.getVisibleRect();
                        int x = (visibleRect.width - image.getWidth(null)) / 2;
                        int y = (visibleRect.height - image.getHeight(null)) / 2;
                        Alchemy.canvas.setImageLocation(x, y);
                        Alchemy.canvas.setImageDisplay(true);
                        Alchemy.canvas.setImage(AlcUtil.getBufferedImage(image));
                        Alchemy.canvas.redraw();

                    } else {
                        AlcUtil.showConfirmDialogFromBundle("imageErrorDialogTitle", "imageErrorDialogMessage");
                    }
                } else {
                    System.out.println("Error reading the file...");
                }

            }
        };
        loadBackgroundImageItem.setAction(loadBackgroundImageAction);
        // Shortcut - Modifier l
        int loadBackgroundImageKey = Alchemy.shortcuts.setShortcut(loadBackgroundImageItem, KeyEvent.VK_L, "loadBackgroundImageTitle", loadBackgroundImageAction, KEY_MODIFIER);
        loadBackgroundImageItem.setup(loadBackgroundImageTitle, loadBackgroundImageKey);
        viewMenu.add(loadBackgroundImageItem);

        // Unload Background Image
        AbstractAction unloadBackgroundImageAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {

                Alchemy.canvas.setImageLocation(0, 0);
                Alchemy.canvas.setImageDisplay(false);
                Alchemy.canvas.setImage(null);
                Alchemy.canvas.redraw();
            }
        };
        unloadBackgroundImageItem = new AlcMenuItem(unloadBackgroundImageAction);
        unloadBackgroundImageItem.setup(getS("unloadBackgroundImageTitle"));
        unloadBackgroundImageItem.setEnabled(false);

        viewMenu.add(unloadBackgroundImageItem);

        this.add(viewMenu);

        //////////////////////////////////////////////////////////////
        // SESSION MENU
        //////////////////////////////////////////////////////////////
        AlcMenu sessionMenu = new AlcMenu(getS("sessionTitle"));

        // Save PDF page
        String savePageTitle = getS("savePageTitle");
        AbstractAction savePageAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                Alchemy.session.manualSavePage();
            }
        };
        AlcMenuItem savePageItem = new AlcMenuItem(savePageAction);
        // Shortcut - Modifier s
        int savePageKey = Alchemy.shortcuts.setShortcut(savePageItem, KeyEvent.VK_S, "savePageTitle", savePageAction, KEY_MODIFIER);
        savePageItem.setup(savePageTitle, savePageKey);


        sessionMenu.add(savePageItem);

        // Save and clear PDF page
        String saveClearTitle = getS("saveClearTitle");
        AbstractAction saveClearPageAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                Alchemy.session.manualSaveClearPage();
            }
        };
        AlcMenuItem saveClearPageItem = new AlcMenuItem(saveClearPageAction);
        int saveClearKey = Alchemy.shortcuts.setShortcut(saveClearPageItem, KeyEvent.VK_D, "saveClearTitle", saveClearPageAction, KEY_MODIFIER);
        saveClearPageItem.setup(saveClearTitle, saveClearKey);
        sessionMenu.add(saveClearPageItem);

        sessionMenu.add(new JSeparator());

        // Recording
        String recordingTitle = getS("recordingTitle");
        final AlcCheckBoxMenuItem recordingItem = new AlcCheckBoxMenuItem();
        AbstractAction recordingAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                // If the source is from the key then we need to change the state of the menu item as well
                String source = e.getSource().getClass().getName();
                if (!source.equals("org.alchemy.core.AlcCheckBoxMenuItem")) {
                    //if (e.getActionCommand().equals("r")) {
                    recordingItem.setState(!recordingItem.getState());
                }
                Alchemy.session.setRecording(recordingItem.getState());
                System.out.println("STATE: " + recordingItem.getState());

            }
        };
        recordingItem.setAction(recordingAction);
        // Shortcut - Modifier r
        int recordingKey = Alchemy.shortcuts.setShortcut(recordingItem, KeyEvent.VK_R, "recordingTitle", recordingAction, KEY_MODIFIER);
        recordingItem.setup(recordingTitle, recordingKey);

        recordingItem.setState(Alchemy.preferences.sessionRecordingState);
        if (Alchemy.preferences.sessionRecordingState) {
            Alchemy.session.setRecording(true);
        }
        sessionMenu.add(recordingItem);

        // Interval submenu
        AlcMenu intervalMenu = new AlcMenu(getS("recordIntervalTitle"));
        // Set the opacity and color of this to overide the defaults used for the top menus
        if (Alchemy.OS != OS_LINUX) {
            intervalMenu.setOpaque(true);
            if (!AlcTheme.isModern()) {
                intervalMenu.setBackground(AlcToolBar.COLOR_UI_HIGHLIGHT);
            }
        }
        ButtonGroup group = new ButtonGroup();
        for (int i = 0; i < recordingIntervalString.length; i++) {
            AlcRadioButtonMenuItem intervalItem = new AlcRadioButtonMenuItem(recordingInterval[i], recordingIntervalString[i]);
            // Set the default value to selected
            if (Alchemy.preferences.sessionRecordingInterval == recordingInterval[i]) {
                intervalItem.setSelected(true);
            }
            intervalItem.addActionListener(new ActionListener() {

                public void actionPerformed(ActionEvent e) {

                    AlcRadioButtonMenuItem source = (AlcRadioButtonMenuItem) e.getSource();
                    Alchemy.session.setTimerInterval(source.getIndex());

                }
            });
            group.add(intervalItem);
            intervalMenu.add(intervalItem);
        }
        intervalMenu.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 5));
        submenu(intervalMenu);
        sessionMenu.add(intervalMenu);

        //sessionMenu.add(new JSeparator());

        // Auto Clear
        AbstractAction autoClearAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                AlcCheckBoxMenuItem source = (AlcCheckBoxMenuItem) e.getSource();
                Alchemy.preferences.sessionAutoClear = source.getState();
            }
        };
        AlcCheckBoxMenuItem autoClearItem = new AlcCheckBoxMenuItem(autoClearAction);
        autoClearItem.setup(getS("autoClearTitle"));
        autoClearItem.setState(Alchemy.preferences.sessionAutoClear);
        sessionMenu.add(autoClearItem);

        sessionMenu.add(new JSeparator());

        // Load Session PDF
        final String loadSessionTitle = getS("loadSessionTitle");
        AbstractAction loadSessionAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                File file = AlcUtil.showFileChooser(new File(DIR_DESKTOP), false);
                if (file != null && file.exists()) {
                    boolean loaded = Alchemy.session.loadSessionFile(file);
                    nextPageItem.setEnabled(loaded);
                    previousPageItem.setEnabled(loaded);
                    unloadSessionItem.setEnabled(loaded);
                    linkSessionItem.setEnabled(loaded);
                }
            }
        };
        AlcMenuItem loadSessionItem = new AlcMenuItem(loadSessionAction);
        loadSessionItem.setup(loadSessionTitle);
        sessionMenu.add(loadSessionItem);

        // Next Page
        final String nextPageTitle = getS("nextPageTitle");
        AbstractAction nextPageAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                Alchemy.session.nextPage();
            }
        };
        nextPageItem = new AlcMenuItem(nextPageAction);
        int nextPageKey = Alchemy.shortcuts.setShortcut(nextPageItem, KeyEvent.VK_RIGHT, "nextPageTitle", nextPageAction, KEY_MODIFIER);
        nextPageItem.setup(nextPageTitle, nextPageKey);
        sessionMenu.add(nextPageItem);
        nextPageItem.setEnabled(false);

        // Previous Page
        final String previousPageTitle = getS("previousPageTitle");
        AbstractAction previousPageAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                Alchemy.session.previousPage();
            }
        };
        previousPageItem = new AlcMenuItem(previousPageAction);
        int previousPageKey = Alchemy.shortcuts.setShortcut(previousPageItem, KeyEvent.VK_LEFT, "previousPageTitle", previousPageAction, KEY_MODIFIER);
        previousPageItem.setup(previousPageTitle, previousPageKey);
        sessionMenu.add(previousPageItem);
        previousPageItem.setEnabled(false);

        // Unload Session PDF
        final String unloadSessionTitle = getS("unloadSessionTitle");
        AbstractAction unloadSessionAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                Alchemy.session.unloadSessionFile();
                nextPageItem.setEnabled(false);
                previousPageItem.setEnabled(false);
                unloadSessionItem.setEnabled(false);
                linkSessionItem.setEnabled(false);
            }
        };
        unloadSessionItem = new AlcMenuItem(unloadSessionAction);
        unloadSessionItem.setup(unloadSessionTitle);
        sessionMenu.add(unloadSessionItem);
        unloadSessionItem.setEnabled(false);


        // Link Session
        String linkSessionTitle = getS("linkSessionTitle");
        linkSessionItem = new AlcCheckBoxMenuItem();
        AbstractAction linkSessionAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                AlcCheckBoxMenuItem source = (AlcCheckBoxMenuItem) e.getSource();
                Alchemy.preferences.sessionLink = source.getState();
            }
        };

        linkSessionItem.setAction(linkSessionAction);
        linkSessionItem.setup(linkSessionTitle);
        linkSessionItem.setSelected(Alchemy.preferences.sessionLink);
        sessionMenu.add(linkSessionItem);
        linkSessionItem.setEnabled(false);

        sessionMenu.add(new JSeparator());

        // Restart Session
        String restartTitle = getS("restartTitle");
        AbstractAction restartAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                Alchemy.session.restartSession();
            }
        };
        AlcMenuItem restartItem = new AlcMenuItem(restartAction);
        restartItem.setup(restartTitle);
        sessionMenu.add(restartItem);

        this.add(sessionMenu);


        //////////////////////////////////////////////////////////////
        // SETTINGS MENU
        //////////////////////////////////////////////////////////////
        AlcMenu settingsMenu = new AlcMenu(getS("settingsTitle"));

        // Line Smoothing
        AbstractAction lineSmoothingAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                AlcCheckBoxMenuItem source = (AlcCheckBoxMenuItem) e.getSource();
                AlcShape.setLineSmoothing(source.getState());
            }
        };
        AlcCheckBoxMenuItem lineSmoothingItem = new AlcCheckBoxMenuItem(lineSmoothingAction);
        lineSmoothingItem.setSelected(Alchemy.preferences.lineSmoothing);
        AlcShape.setLineSmoothing(Alchemy.preferences.lineSmoothing);
        lineSmoothingItem.setup(getS("lineSmoothingTitle"));
        settingsMenu.add(lineSmoothingItem);

        settingsMenu.add(new JSeparator());

        // Keyboard Shortcuts
        AbstractAction keyboardShortcutsAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                Alchemy.shortcuts.showWindow();
            }
        };
        AlcMenuItem keyboardShortcutsItem = new AlcMenuItem(keyboardShortcutsAction);
        keyboardShortcutsItem.setup(getS("keyboardShortcutsTitle"));
        settingsMenu.add(keyboardShortcutsItem);

        // Options - in the application menu on a Mac (Cmd ,)
        if (Alchemy.OS != OS_MAC) {
            AbstractAction optionsAction = new AbstractAction() {

                public void actionPerformed(ActionEvent e) {
                    Alchemy.preferences.showWindow();
                }
            };
            AlcMenuItem optionsItem = new AlcMenuItem(optionsAction);
            int optionsKey = Alchemy.shortcuts.setShortcut(optionsItem, KeyEvent.VK_O, "optionsTitle", optionsAction, KEY_MODIFIER);
            optionsItem.setup(getS("optionsTitle"), optionsKey);
            settingsMenu.add(optionsItem);
        }

        this.add(settingsMenu);

        //////////////////////////////////////////////////////////////
        // HELP MENU
        //////////////////////////////////////////////////////////////
        AlcMenu helpMenu = new AlcMenu(getS("helpTitle"));

        AbstractAction helpAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                File manual = AlcPaths.file("Alchemy.pdf");
                if (manual.exists()) {
                    AlcUtil.openPDF(manual);
                } else {
                    AlcUtil.openURL("http://al.chemy.org/documentation/");
                }
            }
        };
        AlcMenuItem helpItem = new AlcMenuItem(helpAction);
        helpItem.setup(getS("alchemyHelpTitle"));
        helpMenu.add(helpItem);

        AlcMenuItem shortcutsHelpItem = new AlcMenuItem(keyboardShortcutsAction);
        shortcutsHelpItem.setup(getS("keyboardShortcutsTitle"));
        helpMenu.add(shortcutsHelpItem);

        helpMenu.add(new JSeparator());

        // The original Alchemy website
        AbstractAction wwwAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                String locale = LOCALE.getLanguage().toLowerCase();
                AlcUtil.openURL(locale.startsWith("ja") ? "http://al.chemy.org/ja/" : "http://al.chemy.org/");
            }
        };
        AlcMenuItem wwwItem = new AlcMenuItem(wwwAction);
        wwwItem.setup(getS("alchemyWebsiteTitle"));
        helpMenu.add(wwwItem);

        // This version: source, releases, issues
        AbstractAction forkAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                AlcUtil.openURL("https://github.com/jeromehouix/Alchemy");
            }
        };
        AlcMenuItem forkItem = new AlcMenuItem(forkAction);
        forkItem.setup(getS("alchemyForkTitle"));
        helpMenu.add(forkItem);

        // About - in the application menu on a Mac
        if (Alchemy.OS != OS_MAC) {
            helpMenu.add(new JSeparator());
            AbstractAction aboutAction = new AbstractAction() {

                public void actionPerformed(ActionEvent e) {
                    Alchemy.window.showAbout();
                }
            };
            AlcMenuItem aboutItem = new AlcMenuItem(aboutAction);
            aboutItem.setup(getS("aboutAlchemyTitle"));
            helpMenu.add(aboutItem);
        }

        this.add(helpMenu);
    }

    /** A menu inside a menu: lined up with the items */
    private static AlcMenu submenu(AlcMenu menu) {
        menu.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        menu.setOpaque(!AlcTheme.isModern());
        return menu;
    }

    /** Submenu to open the drawing in another application */
    private AlcMenu switchMenu() {
        AlcMenu switchMenu = submenu(new AlcMenu(getS("switchTitle")));

        AbstractAction switchVectorAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                switchVector();
            }
        };
        AlcMenuItem switchVectorItem = new AlcMenuItem(switchVectorAction);
        int vectorKey = Alchemy.shortcuts.setShortcut(switchVectorItem, KeyEvent.VK_V, "switchVectorTitle", switchVectorAction, KEY_MODIFIER);
        switchVectorItem.setup(getS("switchVectorTitle"), vectorKey);
        switchMenu.add(switchVectorItem);

        AbstractAction switchBitmapAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                switchBitmap();
            }
        };
        AlcMenuItem switchBitmapItem = new AlcMenuItem(switchBitmapAction);
        int bitmapKey = Alchemy.shortcuts.setShortcut(switchBitmapItem, KeyEvent.VK_B, "switchBitmapTitle", switchBitmapAction, KEY_MODIFIER);
        switchBitmapItem.setup(getS("switchBitmapTitle"), bitmapKey);
        switchMenu.add(switchBitmapItem);

        switchMenu.add(new JSeparator());

        AbstractAction switchVectorAppAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                File file = AlcUtil.showFileChooser(platformAppDir);
                if (file != null) {
                    Alchemy.preferences.switchVectorApp = file.toString();
                }
            }
        };
        AlcMenuItem switchVectorAppItem = new AlcMenuItem(switchVectorAppAction);
        switchVectorAppItem.setup(getS("setVectorApp"));
        switchMenu.add(switchVectorAppItem);

        // Format submenu
        AlcMenu formatMenu = submenu(new AlcMenu(getS("setVectorFormat")));
        ButtonGroup formatGroup = new ButtonGroup();
        ActionListener formatListener = new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                AlcRadioButtonMenuItem source = (AlcRadioButtonMenuItem) e.getSource();
                Alchemy.preferences.switchVectorFormat = source.getIndex();
            }
        };
        AlcRadioButtonMenuItem pdfItem = new AlcRadioButtonMenuItem(Alchemy.preferences.FORMAT_PDF, "PDF");
        pdfItem.setSelected(Alchemy.preferences.switchVectorFormat == Alchemy.preferences.FORMAT_PDF);
        pdfItem.addActionListener(formatListener);
        formatGroup.add(pdfItem);
        formatMenu.add(pdfItem);
        AlcRadioButtonMenuItem svgItem = new AlcRadioButtonMenuItem(Alchemy.preferences.FORMAT_SVG, "SVG");
        svgItem.setSelected(Alchemy.preferences.switchVectorFormat == Alchemy.preferences.FORMAT_SVG);
        svgItem.addActionListener(formatListener);
        formatGroup.add(svgItem);
        formatMenu.add(svgItem);
        switchMenu.add(formatMenu);

        AbstractAction switchBitmapAppAction = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                File file = AlcUtil.showFileChooser(platformAppDir);
                if (file != null) {
                    Alchemy.preferences.switchBitmapApp = file.toString();
                }
            }
        };
        AlcMenuItem switchBitmapAppItem = new AlcMenuItem(switchBitmapAppAction);
        switchBitmapAppItem.setup(getS("setBitmapApp"));
        switchMenu.add(switchBitmapAppItem);
        return switchMenu;
    }

    /**
     * A menu item for an action of the toolbar (undo, clear...): same action,
     * and the keys chosen by the user shown in the menu
     */
    private AlcMenuItem forwardItem(final String key) {
        final Action target = Alchemy.shortcuts.getAction(key);
        AbstractAction action = new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                if (target != null) {
                    target.actionPerformed(e);
                }
            }
        };
        AlcMenuItem item = new AlcMenuItem(action);
        item.setup(getS(key));
        KeyStroke keys = Alchemy.shortcuts.getKeyStroke(key);
        if (keys != null && keys.getModifiers() != 0) {
            item.setAccelerator(keys);
        } else if (keys != null) {
            // A single key (Space): shown in the title, not as a menu key, so
            // the menu bar of the Mac never takes it from a text field
            item.setText(getS(key) + "  (" + AlcShortcuts.getShortcutString(keys.getKeyCode(), 0) + ")");
        }
        item.setEnabled(target != null);
        return item;
    }

    private AbstractAction exportAction(final String format) {
        return new AbstractAction() {

            public void actionPerformed(ActionEvent e) {
                exportAs(format);
            }
        };
    }

    private AlcMenuItem exportItem(String titleKey, String format) {
        AlcMenuItem item = new AlcMenuItem(exportAction(format));
        item.setup(getS(titleKey));
        return item;
    }

    /** Select Pull Shapes and ask for SVG or PDF files to add to the stamps */
    private void importStamps() {
        Action select = Alchemy.shortcuts.getAction("Pull Shapes");
        AlcModule pull = null;
        for (int i = 0; i < Alchemy.plugins.creates.length; i++) {
            if ("Pull Shapes".equals(Alchemy.plugins.creates[i].getName())) {
                pull = Alchemy.plugins.creates[i];
            }
        }
        if (pull == null) {
            JOptionPane.showMessageDialog(Alchemy.window, getS("importStampsNoModule"));
            return;
        }
        if (select != null && Alchemy.plugins.creates[Alchemy.plugins.currentCreate] != pull) {
            select.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "import"));
        }
        try {
            pull.getClass().getMethod("importStamps").invoke(pull);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /** Ask where to save, with the system dialog on a Mac */
    private File chooseSaveFile(String title, String extension) {
        String name = Alchemy.bundle.getString("defaultFileName") + "." + extension;
        File file;
        if (Alchemy.OS == OS_MAC) {
            FileDialog chooser = new FileDialog(Alchemy.window, title, FileDialog.SAVE);
            chooser.setDirectory(Alchemy.preferences.exportDirectory);
            chooser.setFile(name);
            chooser.setVisible(true);
            if (chooser.getFile() == null) {
                return null;
            }
            file = new File(chooser.getDirectory(), chooser.getFile());
        } else {
            AlcFileChooser fc = new AlcFileChooser(Alchemy.preferences.exportDirectory);
            fc.setDialogTitle(title);
            fc.setSelectedFile(new File(name));
            if (fc.showSaveDialog(this) != AlcFileChooser.APPROVE_OPTION) {
                return null;
            }
            file = fc.getSelectedFile();
        }
        if (!file.getName().toLowerCase().endsWith("." + extension)) {
            file = new File(file.getPath() + "." + extension);
        }
        if (file.getParent() != null) {
            Alchemy.preferences.exportDirectory = file.getParent();
        }
        return file;
    }

    /** Get a string from the resource bundle */
    private String getS(String stringName) {
        return Alchemy.bundle.getString(stringName);
    }

    /** Return the height of the menubar */
    @Override
    public int getHeight() {
        return height;
    }

    /** Print the canvas */
    private void print() {
        if (printer == null) {
            printer = PrinterJob.getPrinterJob();
        }
        if (page == null) {
            page = printer.defaultPage();
            page.setOrientation(PageFormat.LANDSCAPE);
        }

        printer.setPrintable(Alchemy.canvas, page);

        if (printer.printDialog()) {
            try {
                printer.print();
            } catch (Exception e) {
                System.err.println(e);
            }
        }

    }

    private void pageSetup() {
        if (printer == null) {
            printer = PrinterJob.getPrinterJob();
        }
        if (defaultPage == null) {
            //defaultPage = new PageFormat();
            defaultPage = printer.defaultPage();
            defaultPage.setOrientation(PageFormat.LANDSCAPE);
        } else {
            defaultPage = page;
        }
        //aset = new HashPrintRequestAttributeSet();
        page = printer.pageDialog(defaultPage);
    }

    /** Export the drawing in a format: SVG, PDF, PNG, the 16-bit PNG or JPG */
    private void exportAs(String format) {
        boolean png16 = format.equals(getS("exportPng16Title"));
        String extension = png16 ? "png" : format.toLowerCase();
        File file = chooseSaveFile(getS("exportFileTitle") + " (" + (png16 ? "PNG 16" : format) + ")", extension);
        if (file == null) {
            return;
        }
        if (png16) {
            try {
                Alchemy.canvas.setGuide(false);
                javax.imageio.ImageIO.write(Alchemy.canvas.renderExact().toImage16(), "png", file);
            } catch (IOException ex) {
                System.err.println(ex);
            } finally {
                Alchemy.canvas.setGuide(true);
            }
        } else if (format.equals("PDF")) {
            Alchemy.session.saveSinglePdf(file);
        } else if (format.equals("SVG")) {
            Alchemy.session.saveSVG(file);
        } else {
            if (exportDialog == null) {
                exportDialog = new AlcExportDialog();
            }
            exportDialog.showWindow(file, format);
        }
    }

    /** Copy the canvas to the clipboard as an image */
    private void copy() {
        // TODO - Implement Vector clipboard copy function
        // http://www.java2s.com/Code/Java/Swing-JFC/MimeClipboardTest.htm

        // A bug on mac with older versions of java can scramble the clipboard
        // fixed in Java 1.5 Release 3 (4238470)
        Alchemy.canvas.setGuide(false);
        AlcUtil.setClipboard(new AlcImageTransferable(Alchemy.canvas.renderCanvas(true)), Alchemy.window);
        Alchemy.canvas.setGuide(true);
    }

    /** Make a temporary file, create a PDF, and then open it */
    private void switchVector() {
        try {
            if(Alchemy.preferences.switchVectorFormat == Alchemy.preferences.FORMAT_SVG) {
                File tempVector = File.createTempFile("AlchemyTempVectorFile", ".svg");
                tempVector.deleteOnExit();
                if (Alchemy.session.saveSVG(tempVector)) {
                    openSwitch(tempVector.toString(), Alchemy.preferences.switchVectorApp);
                } else {
                    System.out.println("Didn't save SVG");
                }
            } else {  //Otherwise, save to pdf
                File tempVector = File.createTempFile("AlchemyTempVectorFile", ".pdf");
                tempVector.deleteOnExit();
                if (Alchemy.session.saveSinglePdf(tempVector)) {
                    openSwitch(tempVector.toString(), Alchemy.preferences.switchVectorApp);
                } else {
                    System.out.println("Didn't save PDF");
                }
            }


        } catch (IOException ex) {
            System.err.println(ex);
        }

    }

    /** Make a temporary file, create a PNG, and then open it */
    private void switchBitmap() {
        try {
            File tempBitmap = File.createTempFile("AlchemyTempBitmapFile", ".png");
            tempBitmap.deleteOnExit();
            if (Alchemy.canvas.saveBitmap(tempBitmap)) {
                openSwitch(tempBitmap.toString(), Alchemy.preferences.switchBitmapApp);
            } else {
                System.out.println("Didn't save???");
            }
        } catch (IOException ex) {
            System.err.println(ex);
        }

    }

    /** Open the appropriate application with the temp file and 'switch' */
    private void openSwitch(String file, String app) {
        File path = new File(app);
        try {
            String[] commands = null;
            switch (Alchemy.OS) {
                case OS_MAC:
                    commands = new String[]{"open", "-a", path.getName(), file};
                    break;
                case OS_WINDOWS:
                    //commands = new String[]{"cmd", "/c", "start",  "\""+path.getName()+"\"", file};
                    commands = new String[]{path.getAbsolutePath(), file};
                    break;
                case OS_LINUX:
                    commands = new String[]{path.getName(), file};
                    break;
            }
            if (commands != null) {
                Runtime.getRuntime().exec(commands);
            }
        //Runtime.getRuntime().exec("open "+file);
        } catch (IOException ex) {
            System.err.println(ex);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (Alchemy.OS != OS_MAC) {
            // Make sure this is not painted on Mac
            // The mac menubar is used instead
            super.paintComponent(g);
        }
    }
}

class ExportFileFilter extends javax.swing.filechooser.FileFilter {

    private final String format;

    ExportFileFilter(String format) {
        this.format = format;
    }

    public boolean accept(File f) {
        return true;
    }

    public String getDescription() {
        return format;
    }
}
