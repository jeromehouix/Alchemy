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
import java.text.NumberFormat;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;

/**
 * Opacity slider with a logarithmic scale.
 *
 * The original slider mapped 80 pixels to 255 alpha values: about 3 values per pixel,
 * so the faintest opacities (1/255, 2/255) could not be reached with the mouse.
 * Here each pixel is a constant ratio, from {@link #MIN_OPACITY} to 1: the faint
 * values get most of the travel, which is where layering thousands of shapes needs
 * precision. The far left is 0 (invisible).
 *
 * Double click to type an exact value, scroll to nudge it.
 */
class AlcOpacitySlider extends JPanel implements AlcConstants {

    /** Faintest opacity reachable with the slider (0.05 %), finer than 1/255 */
    static final float MIN_OPACITY = 0.0005f;
    private static final int SLIDER_WIDTH = 120;
    private static final int SLIDER_HEIGHT = 25;

    private final Track track;
    private final JLabel label;
    private final String title;
    private float opacity = 1f;
    private boolean adjusting = false;
    private final EventListenerList listeners = new EventListenerList();

    AlcOpacitySlider(String title, String toolTip, float initialOpacity) {
        this.title = title;
        this.setOpaque(false);
        this.setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));

        track = new Track();
        this.add(track);

        label = new JLabel();
        label.setFont(FONT_MEDIUM);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(1, 0, 0, 0));
        this.add(label);

        if (toolTip != null) {
            track.setToolTipText(toolTip);
            label.setToolTipText(toolTip);
        }
        setOpacity(initialOpacity);
    }

    //////////////////////////////////////////////////////////////
    // VALUE
    //////////////////////////////////////////////////////////////
    float getOpacity() {
        return opacity;
    }

    /** Set the value without notifying the listeners */
    void setOpacity(float opacity) {
        this.opacity = Math.max(0f, Math.min(1f, opacity));
        label.setText(title + " " + format(this.opacity));
        track.repaint();
    }

    boolean getValueIsAdjusting() {
        return adjusting;
    }

    void addChangeListener(ChangeListener listener) {
        listeners.add(ChangeListener.class, listener);
    }

    private void userSet(float value) {
        setOpacity(value);
        ChangeEvent event = new ChangeEvent(this);
        for (ChangeListener l : listeners.getListeners(ChangeListener.class)) {
            l.stateChanged(event);
        }
    }

    /** Opacity for a position on the track */
    static float positionToOpacity(int x, int width) {
        if (x <= 0) {
            return 0f;
        }
        if (x >= width) {
            return 1f;
        }
        double t = (x - 1) / (double) (width - 1);
        return (float) (MIN_OPACITY * Math.pow(1.0 / MIN_OPACITY, t));
    }

    /** Position on the track for an opacity */
    static int opacityToPosition(float opacity, int width) {
        if (opacity <= 0f) {
            return 0;
        }
        if (opacity <= MIN_OPACITY) {
            return 1;
        }
        double t = Math.log(opacity / MIN_OPACITY) / Math.log(1.0 / MIN_OPACITY);
        return (int) Math.round(1 + t * (width - 1));
    }

    /** One notch up or down the logarithmic scale, for the keyboard shortcuts */
    static float nudge(float opacity, int notches) {
        int x = opacityToPosition(opacity, SLIDER_WIDTH) + notches * 3;
        return positionToOpacity(Math.max(0, Math.min(SLIDER_WIDTH, x)), SLIDER_WIDTH);
    }

    /** Opacity as a percentage, with more decimals for the faint values */
    static String format(float opacity) {
        float percent = opacity * 100f;
        NumberFormat nf = NumberFormat.getNumberInstance();
        if (percent == 0f || percent >= 10f) {
            nf.setMaximumFractionDigits(0);
        } else if (percent >= 1f) {
            nf.setMaximumFractionDigits(1);
        } else if (percent >= 0.1f) {
            nf.setMaximumFractionDigits(2);
        } else {
            nf.setMaximumFractionDigits(3);
        }
        return nf.format(percent) + " %";
    }

    private void askExactValue() {
        String current = format(opacity).replace(" %", "");
        String input = (String) JOptionPane.showInputDialog(this,
                Alchemy.bundle.getString("opacityDialogMessage"),
                title, JOptionPane.PLAIN_MESSAGE, null, null, current);
        if (input == null) {
            return;
        }
        try {
            String clean = input.replace("%", "").replace(',', '.').trim();
            userSet(Float.parseFloat(clean) / 100f);
        } catch (NumberFormatException ex) {
            Toolkit.getDefaultToolkit().beep();
        }
    }

    //////////////////////////////////////////////////////////////
    // TRACK
    //////////////////////////////////////////////////////////////
    private class Track extends JComponent implements MouseListener, MouseMotionListener, MouseWheelListener {

        private boolean over = false;

        Track() {
            Dimension size = new Dimension(SLIDER_WIDTH, SLIDER_HEIGHT);
            setPreferredSize(size);
            setMinimumSize(size);
            setMaximumSize(size);
            addMouseListener(this);
            addMouseMotionListener(this);
            addMouseWheelListener(this);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            int w = SLIDER_WIDTH, h = SLIDER_HEIGHT;
            int x = opacityToPosition(opacity, w);
            if (AlcTheme.isModern()) {
                paintModern(g2, w, h, x);
                return;
            }
            g2.setColor(COLOR_UI_START);
            g2.fillRect(1, 1, w - 2, h - 2);
            g2.setColor(Color.LIGHT_GRAY);
            g2.fillRect(1, 1, x, h - 2);
            // Marks at 1/255 (finest step of the classic 8-bit render) and 10 %
            g2.setColor(new Color(0, 0, 0, 40));
            int x255 = opacityToPosition(1 / 255f, w);
            int x10 = opacityToPosition(0.1f, w);
            g2.drawLine(x255, h - 6, x255, h - 2);
            g2.drawLine(x10, h - 6, x10, h - 2);
            AlcUtil.drawSoftRect(g2, 0, 0, w, h);
            g2.setColor(over ? Color.BLACK : Color.GRAY);
            g2.drawLine(x, 0, x, h);
        }

        private void paintModern(Graphics2D g2, int w, int h, int x) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(AlcTheme.field());
            g2.fillRoundRect(0, 0, w - 1, h - 1, 8, 8);
            Color a = AlcTheme.accent();
            g2.setColor(new Color(a.getRed(), a.getGreen(), a.getBlue(), 70));
            g2.fillRoundRect(0, 0, Math.max(8, x), h - 1, 8, 8);
            g2.setColor(AlcTheme.line());
            g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);
            // Marks at 1/255 and 10 %
            g2.setColor(AlcTheme.muted());
            int x255 = opacityToPosition(1 / 255f, w);
            int x10 = opacityToPosition(0.1f, w);
            g2.drawLine(x255, h - 6, x255, h - 3);
            g2.drawLine(x10, h - 6, x10, h - 3);
            g2.setColor(over ? AlcTheme.accent() : AlcTheme.text());
            g2.fillRect(Math.max(1, Math.min(w - 3, x - 1)), 2, 2, h - 4);
        }

        private void track(MouseEvent e) {
            userSet(positionToOpacity(e.getX(), SLIDER_WIDTH));
        }

        public void mousePressed(MouseEvent e) {
            if (e.getClickCount() >= 2) {
                askExactValue();
                return;
            }
            adjusting = true;
            setOpacity(positionToOpacity(e.getX(), SLIDER_WIDTH));
        }

        public void mouseDragged(MouseEvent e) {
            if (adjusting) {
                setOpacity(positionToOpacity(e.getX(), SLIDER_WIDTH));
            }
        }

        public void mouseReleased(MouseEvent e) {
            if (adjusting) {
                adjusting = false;
                track(e);
            }
        }

        public void mouseWheelMoved(MouseWheelEvent e) {
            userSet(nudge(opacity, -e.getWheelRotation()));
        }

        public void mouseEntered(MouseEvent e) {
            over = true;
            repaint();
        }

        public void mouseExited(MouseEvent e) {
            over = false;
            repaint();
        }

        public void mouseClicked(MouseEvent e) {
        }

        public void mouseMoved(MouseEvent e) {
        }
    }
}
