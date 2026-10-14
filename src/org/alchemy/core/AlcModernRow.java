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
import javax.swing.JPanel;

/**
 * The single row of the modern toolbar: the tools on the left, the color,
 * opacity and swatch on the right, filling the rest of the width.
 * When the window is too narrow the color part goes on a second line.
 */
class AlcModernRow extends JPanel {

    static final int ROW_HEIGHT = 60;
    /** Minimum width left for the swatch strip next to the color tools */
    private static final int SWATCH_MIN = 160;

    private final Component tools, colors;
    private final Component colorTools;

    /**
     * @param tools         The tools (style, modules, zoom...)
     * @param colors        The color part (color, opacity, swatch)
     * @param colorTools    The fixed part of the color part, to compute its minimum width
     */
    AlcModernRow(Component tools, Component colors, Component colorTools) {
        this.tools = tools;
        this.colors = colors;
        this.colorTools = colorTools;
        this.setOpaque(false);
        this.setLayout(null);
        this.add(tools);
        this.add(colors);
    }

    private int toolsWidth() {
        return tools.getPreferredSize().width;
    }

    private int colorsMinWidth() {
        return colorTools.getPreferredSize().width + SWATCH_MIN;
    }

    /** True if the color part fits on the same line at this width */
    private boolean oneLine(int width) {
        return width - toolsWidth() >= colorsMinWidth();
    }

    /** Height of the row for a given width */
    int heightFor(int width) {
        return oneLine(width) ? ROW_HEIGHT : ROW_HEIGHT * 2;
    }

    @Override
    public Dimension getPreferredSize() {
        int w = getWidth() > 0 ? getWidth() : toolsWidth() + colorsMinWidth();
        return new Dimension(w, heightFor(w));
    }

    @Override
    public void doLayout() {
        int w = getWidth();
        int tw = Math.min(w, toolsWidth());
        tools.setBounds(0, 0, tw, ROW_HEIGHT);
        if (oneLine(w)) {
            colors.setBounds(tw, 0, w - tw, ROW_HEIGHT);
        } else {
            colors.setBounds(0, ROW_HEIGHT, w, ROW_HEIGHT);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(AlcTheme.bar());
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(AlcTheme.line());
        g.fillRect(0, getHeight() - 1, getWidth(), 1);
        // Line between the tools and the color part
        if (oneLine(getWidth())) {
            int x = Math.min(getWidth(), toolsWidth());
            g.fillRect(x, 12, 1, ROW_HEIGHT - 24);
        }
    }
}
