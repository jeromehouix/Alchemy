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

import java.awt.Color;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import javax.swing.UIManager;

/**
 * Colors of the modern interface, light or dark.
 *
 * The classic interfaces (standard and simple) keep their own 2010 colors:
 * every method here is only called when {@link #isModern()} is true, or
 * returns the classic value otherwise.
 */
final class AlcTheme {

    static final int APPEARANCE_SYSTEM = 0;
    static final int APPEARANCE_LIGHT = 1;
    static final int APPEARANCE_DARK = 2;

    private static boolean modern = false;
    private static boolean dark = false;

    private AlcTheme() {
    }

    /** Set up the theme and the look and feel, before any window is created */
    static void setup(boolean modernInterface, int appearance) {
        modern = modernInterface;
        if (!modern) {
            return;
        }
        if (appearance == APPEARANCE_DARK) {
            dark = true;
        } else if (appearance == APPEARANCE_LIGHT) {
            dark = false;
        } else {
            dark = systemIsDark();
        }
        if (Alchemy.OS == AlcConstants.OS_MAC) {
            // Title bar and native parts follow the chosen appearance
            System.setProperty("apple.awt.application.appearance", dark ? "NSAppearanceNameDarkAqua" : "NSAppearanceNameAqua");
        }
        try {
            // FlatLaf: menus, dialogs and fields that match the toolbar, sharp on Retina screens
            com.formdev.flatlaf.FlatLaf.setGlobalExtraDefaults(java.util.Collections.singletonMap("@accentColor", hex(accent())));
            String laf = dark ? "com.formdev.flatlaf.themes.FlatMacDarkLaf" : "com.formdev.flatlaf.themes.FlatMacLightLaf";
            UIManager.setLookAndFeel(laf);
        } catch (Throwable ex) {
            System.err.println("FlatLaf not available, keeping the system look and feel: " + ex);
        }
    }

    static boolean isModern() {
        return modern;
    }

    static boolean isDark() {
        return modern && dark;
    }

    /** macOS dark mode, read from the user defaults */
    private static boolean systemIsDark() {
        if (Alchemy.OS != AlcConstants.OS_MAC) {
            return false;
        }
        try {
            Process p = new ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle").redirectErrorStream(true).start();
            String line;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                line = r.readLine();
            }
            p.waitFor(2, TimeUnit.SECONDS);
            return line != null && line.trim().equalsIgnoreCase("Dark");
        } catch (Exception ex) {
            return false;
        }
    }

    private static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    //////////////////////////////////////////////////////////////
    // COLORS
    //////////////////////////////////////////////////////////////
    /** Main toolbar background */
    static Color bar() {
        return dark ? new Color(0x2a2a2d) : new Color(0xf8f7f5);
    }

    /** Module options row background */
    static Color subBar() {
        return dark ? new Color(0x232325) : new Color(0xefeeea);
    }

    /** Thin lines between areas */
    static Color line() {
        return dark ? new Color(0x3a3a3e) : new Color(0xd6d4cf);
    }

    /** Main text and icons */
    static Color text() {
        return dark ? new Color(0xececee) : new Color(0x1d1d1f);
    }

    /** Secondary text and resting icons */
    static Color muted() {
        return dark ? new Color(0xa3a3aa) : new Color(0x5f5f63);
    }

    /** Active state */
    static Color accent() {
        return dark ? new Color(0x5fd39a) : new Color(0x1f7a52);
    }

    /** Pressed state */
    static Color accentDown() {
        return dark ? new Color(0x3fae78) : new Color(0x155c3d);
    }

    /** Fields: spinners, sliders, combo boxes */
    static Color field() {
        return dark ? new Color(0x1c1c1e) : new Color(0xffffff);
    }

    /** Track of the sliders */
    static Color track() {
        return dark ? new Color(0x4a4a50) : new Color(0xd2d0ca);
    }

    /** Hover background of the buttons */
    static Color hover() {
        return dark ? new Color(255, 255, 255, 22) : new Color(0, 0, 0, 14);
    }
}
