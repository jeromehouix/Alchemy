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
package org.alchemy.core;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.*;
import java.util.ArrayList;
import javax.swing.*;
import javax.swing.JTextField;

/**
 * AlcShortcuts
 * @author Karl D.D. Willis
 */
class AlcShortcuts extends JDialog implements AlcConstants {

    private ArrayList<AlcShortcutMapper> userShortcuts;
    private ArrayList<AlcShortcutMapper> defaultShortcuts;
    private JTextField[] textfields;
    private int index = 0;
    private boolean listenerActive = false;
    private JButton okButton;
    private boolean reloadShortcuts = false;
    private static final Font shortcutFont = new Font("sansserif", Font.PLAIN, 12);
    private JPanel shortcutPanel;

    AlcShortcuts(AlcWindow owner) {
        super(owner, Alchemy.bundle.getString("keyboardShortcutsWindowTitle"), true);
        userShortcuts = new ArrayList<AlcShortcutMapper>(50);
        defaultShortcuts = new ArrayList<AlcShortcutMapper>(50);
    }

    /** Search field of the window */
    private JTextField searchField;
    /** Rows of the list, to filter them */
    private final ArrayList<JComponent[]> rows = new ArrayList<JComponent[]>();

    void setupWindow() {

        // Update the tooltips with the user defined shortcuts
        refreshInterfaceElements();

        final JPanel masterPanel = new JPanel(new BorderLayout(0, 10));
        masterPanel.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        if (!AlcTheme.isModern()) {
            masterPanel.setOpaque(true);
            masterPanel.setBackground(AlcToolBar.COLOR_UI_HIGHLIGHT);
        }

        // Search, and how to change a shortcut
        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.setOpaque(false);
        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", Alchemy.bundle.getString("shortcutSearch"));
        searchField.putClientProperty("JTextField.showClearButton", Boolean.TRUE);
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {

            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                filter();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                filter();
            }

            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                filter();
            }
        });
        top.add(searchField, BorderLayout.NORTH);
        JLabel hint = new JLabel(Alchemy.bundle.getString("shortcutHint"));
        hint.setForeground(UIManager.getColor("Label.disabledForeground"));
        hint.setFont(hint.getFont().deriveFont(11f));
        top.add(hint, BorderLayout.SOUTH);
        masterPanel.add(top, BorderLayout.NORTH);

        //////////////////////////////////////////////////////////////
        // SCROLL PANE
        //////////////////////////////////////////////////////////////
        shortcutPanel = setupShortcutPanel();
        final JScrollPane scrollPane = new JScrollPane(shortcutPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setPreferredSize(new Dimension(480, 440));
        masterPanel.add(scrollPane, BorderLayout.CENTER);

        //////////////////////////////////////////////////////////////
        // RESTORE DEFAULT BUTTON
        //////////////////////////////////////////////////////////////
        JButton defaultButton = new JButton(Alchemy.bundle.getString("restoreDefaults"));
        defaultButton.addActionListener(
                new ActionListener() {

                    public void actionPerformed(ActionEvent e) {
                        // Copies, so editing a shortcut does not change the defaults
                        userShortcuts = new ArrayList<AlcShortcutMapper>();
                        for (AlcShortcutMapper d : defaultShortcuts) {
                            userShortcuts.add(new AlcShortcutMapper(d.component, d.index, d.key, d.title, d.titleEn, d.action, d.modifier));
                        }
                        reloadShortcuts();
                        refreshTextfields();
                        okButton.requestFocus();
                        reloadShortcuts = true;
                    }
                });

        //////////////////////////////////////////////////////////////
        // CANCEL BUTTON
        //////////////////////////////////////////////////////////////
        JButton cancelButton = new JButton(Alchemy.bundle.getString("cancel"));
        cancelButton.addActionListener(
                new ActionListener() {

                    public void actionPerformed(ActionEvent e) {
                        setVisible(false);
                    }
                });

        //////////////////////////////////////////////////////////////
        // OK BUTTON
        //////////////////////////////////////////////////////////////
        okButton = new JButton(Alchemy.bundle.getString("ok"));
        okButton.addActionListener(
                new ActionListener() {

                    public void actionPerformed(ActionEvent e) {
                        // If there have been changes
                        if (reloadShortcuts) {
                            reloadShortcuts();
                            saveShortcuts();
                            refreshInterfaceElements();
                        }
                        reloadShortcuts = false;
                        setVisible(false);
                    }
                });

        //Lay out the buttons from left to right.
        JPanel buttonPane = new JPanel();
        buttonPane.setOpaque(false);
        buttonPane.setLayout(new BoxLayout(buttonPane, BoxLayout.LINE_AXIS));
        buttonPane.add(defaultButton);
        buttonPane.add(Box.createHorizontalGlue());
        if (Alchemy.OS == OS_MAC) {
            buttonPane.add(cancelButton);
            buttonPane.add(Box.createRigidArea(new Dimension(10, 0)));
            buttonPane.add(okButton);
        } else {
            buttonPane.add(okButton);
            buttonPane.add(Box.createRigidArea(new Dimension(10, 0)));
            buttonPane.add(cancelButton);
        }
        masterPanel.add(buttonPane, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(okButton);

        AlcUtil.registerWindowCloseKeys(this.getRootPane(), new AbstractAction() {

            public void actionPerformed(ActionEvent actionEvent) {
                setVisible(false);
            }
        });

        this.setContentPane(masterPanel);
        this.pack();
    }

    /** Show and centre the shorcut window */
    void showWindow() {
        if (searchField != null) {
            searchField.setText("");
        }
        Point loc = AlcUtil.calculateCenter(this);
        this.setLocation(loc.x, loc.y);
        this.setVisible(true);
    }

    /** Show only the shortcuts whose name or keys contain the search */
    private void filter() {
        String q = searchField.getText().trim().toLowerCase();
        for (JComponent[] row : rows) {
            boolean show = q.isEmpty() || ((JLabel) row[0]).getText().toLowerCase().contains(q)
                    || ((JTextField) row[1]).getText().toLowerCase().contains(q);
            row[0].setVisible(show);
            row[1].setVisible(show);
        }
        shortcutPanel.revalidate();
        shortcutPanel.repaint();
    }

    private static Color fieldBackground() {
        Color c = UIManager.getColor("TextField.background");
        return c != null ? c : Color.WHITE;
    }

    private static Color errorBackground() {
        return AlcTheme.isDark() ? new Color(110, 40, 46) : new Color(255, 214, 218);
    }

    /** Create and return the shortcut panel*/
    JPanel setupShortcutPanel() {

        textfields = new JTextField[userShortcuts.size()];
        rows.clear();

        JPanel panel = new JPanel(new java.awt.GridBagLayout()) {

            // Follow the width of the scroll pane: no horizontal scrolling
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                if (getParent() != null && getParent().getWidth() > 0) {
                    d.width = getParent().getWidth();
                }
                return d;
            }
        };
        panel.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        if (!AlcTheme.isModern()) {
            panel.setOpaque(true);
            panel.setBackground(AlcToolBar.COLOR_UI_START);
        }
        java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
        c.insets = new java.awt.Insets(3, 0, 3, 0);
        c.fill = java.awt.GridBagConstraints.HORIZONTAL;

        for (int i = 0; i < userShortcuts.size(); i++) {
            final AlcShortcutMapper shortcut = userShortcuts.get(i);
            JLabel label = new JLabel(shortcut.title);
            label.setFont(shortcutFont);
            c.gridx = 0;
            c.gridy = i;
            c.weightx = 1;
            panel.add(label, c);

            final String originalShortcut = getShortcutString(shortcut.key, shortcut.modifier);
            final JTextField textfield = new JTextField(originalShortcut);
            textfield.setEditable(false);
            textfield.setHorizontalAlignment(JTextField.CENTER);
            textfield.setBackground(fieldBackground());
            textfield.setFont(shortcutFont);
            textfield.setColumns(9);
            textfield.setFocusTraversalKeysEnabled(false);
            textfield.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            textfield.setToolTipText(Alchemy.bundle.getString("shortcutHint"));

            textfield.addMouseListener(new MouseAdapter() {

                @Override
                public void mousePressed(MouseEvent e) {
                    listenerActive = true;
                    textfield.setText(Alchemy.bundle.getString("shortcutPress"));
                    textfield.requestFocusInWindow();
                }
            });

            textfield.addKeyListener(new KeyAdapter() {

                @Override
                public void keyPressed(KeyEvent e) {
                    if (!listenerActive) {
                        return;
                    }
                    int key = e.getKeyCode();
                    // Wait for the actual key, after the modifiers
                    if (key == KeyEvent.VK_META || key == KeyEvent.VK_ALT || key == KeyEvent.VK_CONTROL
                            || key == KeyEvent.VK_SHIFT || key == KeyEvent.VK_UNDEFINED) {
                        return;
                    }
                    e.consume();
                    int modifier = e.getModifiers();
                    // Make a text string of the shortcut
                    String shortcutString = getShortcutString(key, modifier);
                    // Check the shortcut is valid
                    int changeShortcut = validateShortcut(key, modifier, shortcut.index);

                    // Shortcut is valid
                    if (changeShortcut == 0) {
                        textfield.setText(shortcutString);
                        shortcut.key = key;
                        shortcut.modifier = modifier;
                        reloadShortcuts = true;
                        textfield.setBackground(fieldBackground());
                    } else {
                        textfield.setBackground(errorBackground());
                        if (changeShortcut == 1) {
                            textfield.setText(Alchemy.bundle.getString("invalidKeyError"));
                        } else if (changeShortcut == 2) {
                            textfield.setText(Alchemy.bundle.getString("keyAlreadyAssignedError"));
                        }
                        javax.swing.Timer timer = new javax.swing.Timer(1500, new ActionListener() {

                            public void actionPerformed(ActionEvent e) {
                                textfield.setBackground(fieldBackground());
                                textfield.setText(getShortcutString(shortcut.key, shortcut.modifier));
                            }
                        });
                        timer.setRepeats(false);
                        timer.start();
                    }
                    listenerActive = false;
                }
            });

            textfield.addFocusListener(new FocusAdapter() {

                @Override
                public void focusLost(FocusEvent e) {
                    if (listenerActive) {
                        listenerActive = false;
                        textfield.setText(getShortcutString(shortcut.key, shortcut.modifier));
                        textfield.setBackground(fieldBackground());
                    }
                }
            });

            textfields[i] = textfield;
            c.gridx = 1;
            c.weightx = 0;
            panel.add(textfield, c);
            rows.add(new JComponent[]{label, textfield});
        }
        // Keeps the rows at the top when filtered
        c.gridx = 0;
        c.gridy = userShortcuts.size();
        c.weighty = 1;
        panel.add(Box.createGlue(), c);
        return panel;
    }

    private int validateShortcut(int key, int modifier, int index) {
        if (modifier == 0) {
            // Make sure these keys are not used alone
            if (key == KeyEvent.VK_META ||
                    key == KeyEvent.VK_ALT ||
                    key == KeyEvent.VK_CONTROL ||
                    key == KeyEvent.VK_SHIFT) {
                return 1;
            }
        }

        // Check the shortcut is not the same as any other
        for (int i = 0; i < userShortcuts.size(); i++) {
            final AlcShortcutMapper shortcut = userShortcuts.get(i);
            if (i != index) {
                if (shortcut.modifier == modifier && shortcut.key == key) {
                    return 2;
                }
            }
        }

        return 0;
    }

    /** Reload the shortcuts into the textfields */
    private void refreshTextfields() {
        for (int i = 0; i < textfields.length; i++) {
            final AlcShortcutMapper shortcut = userShortcuts.get(i);
            // Make a text string of the shortcut
            String shortcutString = getShortcutString(shortcut.key, shortcut.modifier);
            textfields[i].setText(shortcutString);
            textfields[i].setBackground(fieldBackground());
        }
    }

    /** Set the keyboard shortcut to trigger an application wide action
     * 
     * @param component The component linked to the shortcut
     * @param key       The key to trigger the action
     * @param title     A unique title for the action
     * @param action    The name of the action to call
     * @return          The key actually used for this shortcut - user specified or default
     */
    int setShortcut(JComponent component, int key, String title, Action action) {
        return setShortcut(component, key, title, action, 0);
    }

    /** Set the keyboard shortcut to trigger an application wide action
     *  with the default modifier key
     * 
     * @param component The component linked to the shortcut
     * @param key       The key to trigger the action
     * @param title     A unique title for the action
     * @param action    The name of the action to call
     * @param modifier  Use the system modifier key - Win=Ctrl or Mac=Command
     * @return          The key actually used for this shortcut - user specified or default
     */
    int setShortcut(JComponent component, int key, String title, Action action, int modifier) {

        String bundleTitle, bundleTitleEn;
        try {
            // Get the localised string to display
            bundleTitle = Alchemy.bundle.getString(title);
            // Get the english string to store as a reference - the two may or may not be the same
            bundleTitleEn = Alchemy.bundleEn.getString(title);
        } catch (Exception e) {
            bundleTitle = title;
            bundleTitleEn = title;
        }

        // Keep track of the default shortcuts
        defaultShortcuts.add(new AlcShortcutMapper(component, index, key, bundleTitle, bundleTitleEn, action, modifier));

        // Look for the users key stored in the preferences
        // If not found go with the default
        int userKey = AlcPreferences.prefs.getInt(bundleTitleEn, key);
        // Look for the modifier key
        int modifierKey = AlcPreferences.prefs.getInt(bundleTitleEn + " Modifier", modifier);
        // Store the mappings into the user set of shortcuts
        userShortcuts.add(new AlcShortcutMapper(component, index, userKey, bundleTitle, bundleTitleEn, action, modifierKey));
        index++;

        Alchemy.window.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(userKey, modifierKey), bundleTitleEn);
        Alchemy.palette.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(userKey, modifierKey), bundleTitleEn);
        Alchemy.window.getRootPane().getActionMap().put(bundleTitleEn, action);
        Alchemy.palette.getRootPane().getActionMap().put(bundleTitleEn, action);
        // Return the key that will be used.
        return userKey;
    }

    /** Clear and reload all shortcuts */
    private void reloadShortcuts() {

        // Remove all shortcut mappings
        Alchemy.window.getRootPane().resetKeyboardActions();
        Alchemy.palette.getRootPane().resetKeyboardActions();

        for (int i = 0; i < userShortcuts.size(); i++) {
            AlcShortcutMapper shortcut = userShortcuts.get(i);
            Alchemy.window.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(shortcut.key, shortcut.modifier), shortcut.titleEn);
            Alchemy.palette.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(shortcut.key, shortcut.modifier), shortcut.titleEn);
            Alchemy.window.getRootPane().getActionMap().put(shortcut.titleEn, shortcut.action);
            Alchemy.palette.getRootPane().getActionMap().put(shortcut.titleEn, shortcut.action);
        }
    }

    /** Store the shortcuts in the preferences persistant storage */
    private void saveShortcuts() {
        for (int i = 0; i < userShortcuts.size(); i++) {
            AlcShortcutMapper shortcut = userShortcuts.get(i);
            AlcPreferences.prefs.putInt(shortcut.titleEn, shortcut.key);
            AlcPreferences.prefs.putInt(shortcut.titleEn + " Modifier", shortcut.modifier);
        }
    }

    /** Refresh the tooltip and accelerator key information */
    private void refreshInterfaceElements() {
        for (int i = 0; i < userShortcuts.size(); i++) {
            AlcShortcutMapper shortcut = userShortcuts.get(i);
            // Check the component is not null and that it implements the shortcut interface
            if (shortcut.component != null && shortcut.component instanceof AlcShortcutInterface) {
                AlcShortcutInterface shortcutComponent = (AlcShortcutInterface) shortcut.component;
                shortcutComponent.refreshShortcut(shortcut.key, shortcut.modifier);
            }
        }
    }

    /** Make a shortcut string from the shortcut key and modifier
     * 
     * @param key       The main key
     * @param modifier  Modifier key
     * @return          String in the format MODIFIER+KEY
     */
    static String getShortcutString(int key, int modifier) {
        boolean ctrl = (modifier & (InputEvent.CTRL_MASK | InputEvent.CTRL_DOWN_MASK)) != 0;
        boolean alt = (modifier & (InputEvent.ALT_MASK | InputEvent.ALT_DOWN_MASK)) != 0;
        boolean shift = (modifier & (InputEvent.SHIFT_MASK | InputEvent.SHIFT_DOWN_MASK)) != 0;
        boolean meta = (modifier & (InputEvent.META_MASK | InputEvent.META_DOWN_MASK)) != 0;
        String keyText = keyName(key);
        if (Alchemy.OS == OS_MAC) {
            // The order of the Mac menus: Control, Option, Shift, Command
            return (ctrl ? "\u2303" : "") + (alt ? "\u2325" : "") + (shift ? "\u21E7" : "") + (meta ? "\u2318" : "") + keyText;
        }
        StringBuilder b = new StringBuilder();
        if (ctrl) {
            b.append("Ctrl+");
        }
        if (alt) {
            b.append("Alt+");
        }
        if (shift) {
            b.append("Shift+");
        }
        if (meta) {
            b.append("Meta+");
        }
        return b.append(keyText).toString();
    }

    /** Short name of a key, as printed on the keyboard */
    private static String keyName(int key) {
        switch (key) {
            case KeyEvent.VK_BACK_SPACE:
                return Alchemy.OS == OS_MAC ? "\u232B" : "Backspace";
            case KeyEvent.VK_DELETE:
                return Alchemy.OS == OS_MAC ? "\u2326" : "Del";
            case KeyEvent.VK_TAB:
                return Alchemy.OS == OS_MAC ? "\u21E5" : "Tab";
            case KeyEvent.VK_ENTER:
                return Alchemy.OS == OS_MAC ? "\u21A9" : "Enter";
            case KeyEvent.VK_ESCAPE:
                return "Esc";
            case KeyEvent.VK_SPACE:
                return Alchemy.bundle.getString("spaceKey");
            case KeyEvent.VK_LEFT:
                return "\u2190";
            case KeyEvent.VK_RIGHT:
                return "\u2192";
            case KeyEvent.VK_UP:
                return "\u2191";
            case KeyEvent.VK_DOWN:
                return "\u2193";
            case KeyEvent.VK_OPEN_BRACKET:
                return "[";
            case KeyEvent.VK_CLOSE_BRACKET:
                return "]";
            case KeyEvent.VK_SEMICOLON:
                return ";";
            case KeyEvent.VK_QUOTE:
                return "'";
            case KeyEvent.VK_EQUALS:
                return "=";
            case KeyEvent.VK_MINUS:
                return "\u2212";
            case KeyEvent.VK_COMMA:
                return ",";
            case KeyEvent.VK_PERIOD:
                return ".";
            case KeyEvent.VK_SLASH:
                return "/";
            case KeyEvent.VK_MULTIPLY:
                return "*";
            case KeyEvent.VK_DIVIDE:
                return "/";
            case KeyEvent.VK_ADD:
                return "+";
            case KeyEvent.VK_SUBTRACT:
                return "\u2212";
            case KeyEvent.VK_DECIMAL:
                return ".";
            default:
                if (key >= KeyEvent.VK_NUMPAD0 && key <= KeyEvent.VK_NUMPAD9) {
                    return Alchemy.bundle.getString("numpadKey") + " " + (key - KeyEvent.VK_NUMPAD0);
                }
                return KeyEvent.getKeyText(key).toUpperCase();
        }
    }

    /** The action registered for a shortcut, by its bundle key (or its title) */
    Action getAction(String key) {
        AlcShortcutMapper m = find(key);
        return m == null ? null : m.action;
    }

    /** The keys of a shortcut chosen by the user, by its bundle key (or its title), or null */
    KeyStroke getKeyStroke(String key) {
        AlcShortcutMapper m = find(key);
        return m == null ? null : KeyStroke.getKeyStroke(m.key, m.modifier);
    }

    private AlcShortcutMapper find(String key) {
        String en;
        try {
            en = Alchemy.bundleEn.getString(key);
        } catch (Exception e) {
            en = key;
        }
        for (AlcShortcutMapper m : userShortcuts) {
            if (m.titleEn.equals(en) || m.titleEn.equals(key)) {
                return m;
            }
        }
        return null;
    }

    static String getShortcutString(int key, int modifier, String title) {
        return title + " (" + getShortcutString(key, modifier) + ")";
    }
}

/** 
 * ShortcutMapper class
 * Stores a set of shortcut mappings
 * 
 * @author karldd
 */
class AlcShortcutMapper {

    JComponent component;
    int index, key, modifier;
    String title, titleEn;
    Action action;

    AlcShortcutMapper(JComponent component, int index, int key, String title, String titleEn, Action action, int modifier) {
        this.component = component;
        this.index = index;
        this.key = key;
        this.title = title;
        this.titleEn = titleEn;
        this.action = action;
        this.modifier = modifier;
    }
}
