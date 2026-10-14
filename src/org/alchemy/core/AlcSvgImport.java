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
import java.awt.Shape;
import java.awt.geom.*;
import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.batik.ext.awt.geom.ExtendedGeneralPath;
import org.w3c.dom.*;

/**
 * Reads the shapes of an SVG file, to be used as stamps.
 *
 * Supports what illustration software exports: paths, basic shapes, groups,
 * transforms, &lt;use&gt;, fill and stroke colors and opacities, from attributes,
 * style attributes and simple &lt;style&gt; sheets (element, .class and #id
 * selectors). Text, images, clipping, masks and filters are ignored; gradients
 * are reduced to the average of their stops.
 */
final class AlcSvgImport {

    /** Computed style, inherited down the tree */
    private static final class Style implements Cloneable {
        String fill = "black";
        String stroke = "none";
        float fillOpacity = 1f;
        float strokeOpacity = 1f;
        float opacity = 1f;         // product of the "opacity" of the ancestors
        float strokeWidth = 1f;
        boolean evenOdd = false;
        String color = "black";     // for currentColor
        boolean visible = true;

        @Override
        protected Style clone() {
            try {
                return (Style) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private final Map<String, Element> ids = new HashMap<String, Element>();
    /** Simple CSS rules: selector (tag, .class or #id) -> declarations */
    private final Map<String, String> css = new LinkedHashMap<String, String>();
    private final List<AlcShape> shapes = new ArrayList<AlcShape>();
    private int useDepth = 0;

    private AlcSvgImport() {
    }

    /**
     * Read the shapes of an SVG file, in document order, in the file's own
     * coordinates, each with its original color, opacity and style
     *
     * @return The shapes, or an empty list if the file cannot be read
     */
    static List<AlcShape> read(File file) {
        AlcSvgImport importer = new AlcSvgImport();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setValidating(false);
            // Illustration software adds a DOCTYPE pointing to the W3C: never fetch it
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(file);
            Element root = doc.getDocumentElement();
            importer.collect(root);
            Style style = new Style();
            importer.walk(root, new AffineTransform(), style);
        } catch (Exception ex) {
            System.err.println("Could not read the SVG file " + file + ": " + ex);
        }
        return importer.shapes;
    }

    //////////////////////////////////////////////////////////////
    // DOCUMENT
    //////////////////////////////////////////////////////////////
    private static String name(Node n) {
        String local = n.getLocalName();
        return local != null ? local : n.getNodeName().replaceFirst("^.*:", "");
    }

    /** Index the ids and the style sheets */
    private void collect(Element e) {
        String id = e.getAttribute("id");
        if (!id.isEmpty()) {
            ids.put(id, e);
        }
        if (name(e).equals("style")) {
            parseCss(e.getTextContent());
        }
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element) {
                collect((Element) n);
            }
        }
    }

    private void parseCss(String text) {
        text = text.replaceAll("(?s)/\\*.*?\\*/", "");
        Matcher m = Pattern.compile("([^{}]+)\\{([^}]*)\\}").matcher(text);
        while (m.find()) {
            for (String selector : m.group(1).split(",")) {
                selector = selector.trim();
                if (!selector.isEmpty()) {
                    String previous = css.get(selector);
                    css.put(selector, previous == null ? m.group(2) : previous + ";" + m.group(2));
                }
            }
        }
    }

    private void walk(Element e, AffineTransform parent, Style parentStyle) {
        String tag = name(e);
        switch (tag) {
            // Not drawn, or not supported
            case "defs": case "symbol": case "clipPath": case "mask": case "pattern": case "marker":
            case "linearGradient": case "radialGradient": case "style": case "title": case "desc":
            case "metadata": case "text": case "image": case "filter": case "foreignObject": case "script":
                return;
            default:
                break;
        }

        AffineTransform at = new AffineTransform(parent);
        String transform = e.getAttribute("transform");
        if (!transform.isEmpty()) {
            at.concatenate(parseTransform(transform));
        }
        Style style = computeStyle(e, parentStyle);
        if (!style.visible) {
            return;
        }

        switch (tag) {
            case "svg":
                if (e.getParentNode() instanceof Element) {
                    // Nested svg: only its position is taken into account
                    at.translate(length(e, "x"), length(e, "y"));
                }
                walkChildren(e, at, style);
                break;
            case "g": case "a": case "switch":
                walkChildren(e, at, style);
                break;
            case "use":
                String href = e.getAttribute("href");
                if (href.isEmpty()) {
                    href = e.getAttributeNS("http://www.w3.org/1999/xlink", "href");
                }
                Element target = href.startsWith("#") ? ids.get(href.substring(1)) : null;
                if (target != null && useDepth < 10) {
                    AffineTransform useAt = new AffineTransform(at);
                    useAt.translate(length(e, "x"), length(e, "y"));
                    useDepth++;
                    if (name(target).equals("symbol")) {
                        walkChildren(target, useAt, computeStyle(target, style));
                    } else {
                        walk(target, useAt, style);
                    }
                    useDepth--;
                }
                break;
            default:
                Shape geometry = geometry(e, tag);
                if (geometry != null) {
                    addShape(geometry, at, style);
                }
                break;
        }
    }

    private void walkChildren(Element e, AffineTransform at, Style style) {
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element) {
                walk((Element) n, at, style);
            }
        }
    }

    //////////////////////////////////////////////////////////////
    // GEOMETRY
    //////////////////////////////////////////////////////////////
    private static Shape geometry(Element e, String tag) {
        switch (tag) {
            case "path":
                return parsePath(e.getAttribute("d"));
            case "rect": {
                double x = length(e, "x"), y = length(e, "y");
                double w = length(e, "width"), h = length(e, "height");
                if (w <= 0 || h <= 0) {
                    return null;
                }
                double rx = length(e, "rx"), ry = length(e, "ry");
                if (rx <= 0 && ry <= 0) {
                    return new Rectangle2D.Double(x, y, w, h);
                }
                if (rx <= 0) {
                    rx = ry;
                }
                if (ry <= 0) {
                    ry = rx;
                }
                return new RoundRectangle2D.Double(x, y, w, h, Math.min(rx * 2, w), Math.min(ry * 2, h));
            }
            case "circle": {
                double r = length(e, "r");
                return r <= 0 ? null : new Ellipse2D.Double(length(e, "cx") - r, length(e, "cy") - r, r * 2, r * 2);
            }
            case "ellipse": {
                double rx = length(e, "rx"), ry = length(e, "ry");
                return rx <= 0 || ry <= 0 ? null
                        : new Ellipse2D.Double(length(e, "cx") - rx, length(e, "cy") - ry, rx * 2, ry * 2);
            }
            case "line":
                return new Line2D.Double(length(e, "x1"), length(e, "y1"), length(e, "x2"), length(e, "y2"));
            case "polyline":
            case "polygon": {
                double[] p = numbers(e.getAttribute("points"));
                if (p.length < 4) {
                    return null;
                }
                Path2D.Double path = new Path2D.Double();
                path.moveTo(p[0], p[1]);
                for (int i = 2; i + 1 < p.length; i += 2) {
                    path.lineTo(p[i], p[i + 1]);
                }
                if (tag.equals("polygon")) {
                    path.closePath();
                }
                return path;
            }
            default:
                return null;
        }
    }

    private void addShape(Shape geometry, AffineTransform at, Style style) {
        Color fill = paint(style.fill, style);
        Color stroke = paint(style.stroke, style);
        if (fill != null) {
            GeneralPath path = new GeneralPath(geometry);
            path.transform(at);
            AlcShape shape = new AlcShape(path, Color.BLACK, 255, AlcConstants.STYLE_FILL, 1f);
            shape.path.setWindingRule(style.evenOdd ? PathIterator.WIND_EVEN_ODD : PathIterator.WIND_NON_ZERO);
            shape.setSource(opaque(fill), style.fillOpacity * style.opacity * fill.getAlpha() / 255f, AlcConstants.STYLE_FILL, 1f);
            shapes.add(shape);
        }
        if (stroke != null && style.strokeWidth > 0) {
            GeneralPath path = new GeneralPath(geometry);
            path.transform(at);
            float scale = (float) Math.sqrt(Math.abs(at.getDeterminant()));
            AlcShape shape = new AlcShape(path, Color.BLACK, 255, AlcConstants.STYLE_FILL, 1f);
            shape.setSource(opaque(stroke), style.strokeOpacity * style.opacity * stroke.getAlpha() / 255f,
                    AlcConstants.STYLE_STROKE, style.strokeWidth * scale);
            shapes.add(shape);
        }
    }

    private static Color opaque(Color c) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue());
    }

    /** Reads the commands, numbers and arc flags of SVG path data */
    private static final class PathScanner {

        private static final Pattern NUM = Pattern.compile("[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?");
        private final String d;
        private final Matcher matcher;
        private int i = 0;

        PathScanner(String d) {
            this.d = d;
            this.matcher = NUM.matcher(d);
        }

        private void skipSeparators() {
            while (i < d.length() && (Character.isWhitespace(d.charAt(i)) || d.charAt(i) == ',')) {
                i++;
            }
        }

        boolean hasMore() {
            skipSeparators();
            return i < d.length();
        }

        boolean atCommand() {
            skipSeparators();
            if (i >= d.length()) {
                return false;
            }
            char c = d.charAt(i);
            return Character.isLetter(c) && c != 'e' && c != 'E';
        }

        char command() {
            return d.charAt(i++);
        }

        double number() {
            skipSeparators();
            matcher.region(i, d.length());
            if (!matcher.lookingAt()) {
                throw new NoSuchElementException();
            }
            i = matcher.end();
            return Double.parseDouble(matcher.group());
        }

        /** Arc flags can be packed without separators ("a10 10 0 01 5 5") */
        boolean flag() {
            skipSeparators();
            if (i < d.length() && (d.charAt(i) == '0' || d.charAt(i) == '1')) {
                return d.charAt(i++) == '1';
            }
            throw new NoSuchElementException();
        }
    }

    /** Parse SVG path data */
    static Shape parsePath(String d) {
        ExtendedGeneralPath path = new ExtendedGeneralPath();
        PathScanner sc = new PathScanner(d);
        double x = 0, y = 0, startX = 0, startY = 0;
        double ctrlX = 0, ctrlY = 0;    // last control point, for S and T
        char cmd = 0, prev = 0;
        boolean started = false;
        try {
            while (sc.hasMore()) {
                if (sc.atCommand()) {
                    cmd = sc.command();
                    if (cmd == 'Z' || cmd == 'z') {
                        if (started) {
                            path.closePath();
                        }
                        x = startX;
                        y = startY;
                        prev = cmd;
                        continue;
                    }
                } else if (cmd == 0 || cmd == 'Z' || cmd == 'z') {
                    // Numbers without a command: malformed data
                    break;
                }
                boolean rel = Character.isLowerCase(cmd);
                double ox = rel ? x : 0, oy = rel ? y : 0;
                switch (Character.toUpperCase(cmd)) {
                    case 'M':
                        x = ox + sc.number();
                        y = oy + sc.number();
                        path.moveTo((float) x, (float) y);
                        started = true;
                        startX = x;
                        startY = y;
                        // Following pairs are implicit line-tos
                        cmd = rel ? 'l' : 'L';
                        break;
                    case 'L':
                        x = ox + sc.number();
                        y = oy + sc.number();
                        path.lineTo((float) x, (float) y);
                        break;
                    case 'H':
                        x = ox + sc.number();
                        path.lineTo((float) x, (float) y);
                        break;
                    case 'V':
                        y = oy + sc.number();
                        path.lineTo((float) x, (float) y);
                        break;
                    case 'C': {
                        double x1 = ox + sc.number(), y1 = oy + sc.number();
                        double x2 = ox + sc.number(), y2 = oy + sc.number();
                        x = ox + sc.number();
                        y = oy + sc.number();
                        path.curveTo((float) x1, (float) y1, (float) x2, (float) y2, (float) x, (float) y);
                        ctrlX = x2;
                        ctrlY = y2;
                        break;
                    }
                    case 'S': {
                        double x1 = x, y1 = y;
                        char p = Character.toUpperCase(prev);
                        if (p == 'C' || p == 'S') {
                            x1 = 2 * x - ctrlX;
                            y1 = 2 * y - ctrlY;
                        }
                        double x2 = ox + sc.number(), y2 = oy + sc.number();
                        x = ox + sc.number();
                        y = oy + sc.number();
                        path.curveTo((float) x1, (float) y1, (float) x2, (float) y2, (float) x, (float) y);
                        ctrlX = x2;
                        ctrlY = y2;
                        break;
                    }
                    case 'Q': {
                        double x1 = ox + sc.number(), y1 = oy + sc.number();
                        x = ox + sc.number();
                        y = oy + sc.number();
                        path.quadTo((float) x1, (float) y1, (float) x, (float) y);
                        ctrlX = x1;
                        ctrlY = y1;
                        break;
                    }
                    case 'T': {
                        double x1 = x, y1 = y;
                        char p = Character.toUpperCase(prev);
                        if (p == 'Q' || p == 'T') {
                            x1 = 2 * x - ctrlX;
                            y1 = 2 * y - ctrlY;
                        }
                        x = ox + sc.number();
                        y = oy + sc.number();
                        path.quadTo((float) x1, (float) y1, (float) x, (float) y);
                        ctrlX = x1;
                        ctrlY = y1;
                        break;
                    }
                    case 'A': {
                        double rx = Math.abs(sc.number()), ry = Math.abs(sc.number());
                        double angle = sc.number();
                        boolean large = sc.flag();
                        boolean sweep = sc.flag();
                        double ex = ox + sc.number(), ey = oy + sc.number();
                        if (rx == 0 || ry == 0) {
                            path.lineTo((float) ex, (float) ey);
                        } else {
                            path.arcTo((float) rx, (float) ry, (float) angle, large, sweep, (float) ex, (float) ey);
                        }
                        x = ex;
                        y = ey;
                        break;
                    }
                    default:
                        // Unknown command: stop here
                        return path;
                }
                prev = cmd;
            }
        } catch (NoSuchElementException ex) {
            // Truncated or malformed data: keep what was read
        }
        return path;
    }

    //////////////////////////////////////////////////////////////
    // TRANSFORMS AND NUMBERS
    //////////////////////////////////////////////////////////////
    static AffineTransform parseTransform(String text) {
        AffineTransform at = new AffineTransform();
        Matcher m = Pattern.compile("(matrix|translate|scale|rotate|skewX|skewY)\\s*\\(([^)]*)\\)").matcher(text);
        while (m.find()) {
            double[] v = numbers(m.group(2));
            switch (m.group(1)) {
                case "matrix":
                    if (v.length == 6) {
                        at.concatenate(new AffineTransform(v[0], v[1], v[2], v[3], v[4], v[5]));
                    }
                    break;
                case "translate":
                    if (v.length >= 1) {
                        at.translate(v[0], v.length > 1 ? v[1] : 0);
                    }
                    break;
                case "scale":
                    if (v.length >= 1) {
                        at.scale(v[0], v.length > 1 ? v[1] : v[0]);
                    }
                    break;
                case "rotate":
                    if (v.length == 3) {
                        at.rotate(Math.toRadians(v[0]), v[1], v[2]);
                    } else if (v.length >= 1) {
                        at.rotate(Math.toRadians(v[0]));
                    }
                    break;
                case "skewX":
                    if (v.length >= 1) {
                        at.shear(Math.tan(Math.toRadians(v[0])), 0);
                    }
                    break;
                case "skewY":
                    if (v.length >= 1) {
                        at.shear(0, Math.tan(Math.toRadians(v[0])));
                    }
                    break;
            }
        }
        return at;
    }

    private static final Pattern NUMBER = Pattern.compile("[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?");

    static double[] numbers(String text) {
        Matcher m = NUMBER.matcher(text);
        List<Double> list = new ArrayList<Double>();
        while (m.find()) {
            list.add(Double.parseDouble(m.group()));
        }
        double[] out = new double[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }

    /** A length attribute, ignoring units */
    private static double length(Element e, String attribute) {
        String v = e.getAttribute(attribute);
        Matcher m = NUMBER.matcher(v);
        return m.find() ? Double.parseDouble(m.group()) : 0;
    }

    //////////////////////////////////////////////////////////////
    // STYLE
    //////////////////////////////////////////////////////////////
    private Style computeStyle(Element e, Style parent) {
        Style s = parent.clone();
        // The opacity property is not inherited: it multiplies down the tree
        Map<String, String> props = new LinkedHashMap<String, String>();
        // Lowest priority first: presentation attributes, CSS (tag, class, id), style attribute
        for (String p : new String[]{"fill", "stroke", "fill-opacity", "stroke-opacity", "opacity",
            "stroke-width", "fill-rule", "color", "display", "visibility"}) {
            String v = e.getAttribute(p);
            if (!v.isEmpty()) {
                props.put(p, v);
            }
        }
        declarations(css.get(name(e)), props);
        for (String cls : e.getAttribute("class").trim().split("\\s+")) {
            if (!cls.isEmpty()) {
                declarations(css.get("." + cls), props);
                declarations(css.get(name(e) + "." + cls), props);
            }
        }
        if (!e.getAttribute("id").isEmpty()) {
            declarations(css.get("#" + e.getAttribute("id")), props);
        }
        declarations(e.getAttribute("style"), props);

        for (Map.Entry<String, String> entry : props.entrySet()) {
            String v = entry.getValue().replace("!important", "").trim();
            if (v.equals("inherit")) {
                continue;
            }
            switch (entry.getKey()) {
                case "fill":
                    s.fill = v;
                    break;
                case "stroke":
                    s.stroke = v;
                    break;
                case "fill-opacity":
                    s.fillOpacity = fraction(v);
                    break;
                case "stroke-opacity":
                    s.strokeOpacity = fraction(v);
                    break;
                case "opacity":
                    s.opacity *= fraction(v);
                    break;
                case "stroke-width":
                    Matcher m = NUMBER.matcher(v);
                    if (m.find()) {
                        s.strokeWidth = Float.parseFloat(m.group());
                    }
                    break;
                case "fill-rule":
                    s.evenOdd = v.equals("evenodd");
                    break;
                case "color":
                    s.color = v;
                    break;
                case "display":
                    if (v.equals("none")) {
                        s.visible = false;
                    }
                    break;
                case "visibility":
                    if (v.equals("hidden") || v.equals("collapse")) {
                        s.visible = false;
                    }
                    break;
            }
        }
        return s;
    }

    private static void declarations(String text, Map<String, String> props) {
        if (text == null) {
            return;
        }
        for (String decl : text.split(";")) {
            int colon = decl.indexOf(':');
            if (colon > 0) {
                props.put(decl.substring(0, colon).trim().toLowerCase(), decl.substring(colon + 1).trim());
            }
        }
    }

    private static float fraction(String v) {
        try {
            v = v.trim();
            if (v.endsWith("%")) {
                return clamp(Float.parseFloat(v.substring(0, v.length() - 1)) / 100f);
            }
            return clamp(Float.parseFloat(v));
        } catch (NumberFormatException e) {
            return 1f;
        }
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    /** Resolve a paint value to a color, null for none */
    private Color paint(String value, Style style) {
        if (value == null) {
            return null;
        }
        value = value.trim();
        if (value.equals("none") || value.isEmpty()) {
            return null;
        }
        if (value.equals("currentColor")) {
            return parseColor(style.color);
        }
        if (value.startsWith("url(")) {
            Matcher m = Pattern.compile("url\\(\\s*['\"]?#([^)'\"]+)['\"]?\\s*\\)\\s*(.*)").matcher(value);
            if (m.find()) {
                Color c = gradientColor(ids.get(m.group(1)), 0);
                if (c != null) {
                    return c;
                }
                // Fallback color after the url
                return m.group(2).isEmpty() ? null : parseColor(m.group(2));
            }
            return null;
        }
        return parseColor(value);
    }

    /** Average color of a gradient's stops (following href chains) */
    private Color gradientColor(Element gradient, int depth) {
        if (gradient == null || depth > 5) {
            return null;
        }
        float r = 0, g = 0, b = 0, a = 0;
        int count = 0;
        for (Node n = gradient.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element && name(n).equals("stop")) {
                Element stop = (Element) n;
                Map<String, String> props = new HashMap<String, String>();
                String sc = stop.getAttribute("stop-color");
                if (!sc.isEmpty()) {
                    props.put("stop-color", sc);
                }
                String so = stop.getAttribute("stop-opacity");
                if (!so.isEmpty()) {
                    props.put("stop-opacity", so);
                }
                for (String cls : stop.getAttribute("class").trim().split("\\s+")) {
                    declarations(css.get("." + cls), props);
                }
                declarations(stop.getAttribute("style"), props);
                Color c = parseColor(props.containsKey("stop-color") ? props.get("stop-color") : "black");
                if (c == null) {
                    continue;
                }
                float opacity = props.containsKey("stop-opacity") ? fraction(props.get("stop-opacity")) : 1f;
                r += c.getRed();
                g += c.getGreen();
                b += c.getBlue();
                a += opacity * c.getAlpha() / 255f;
                count++;
            }
        }
        if (count == 0) {
            String href = gradient.getAttribute("href");
            if (href.isEmpty()) {
                href = gradient.getAttributeNS("http://www.w3.org/1999/xlink", "href");
            }
            return href.startsWith("#") ? gradientColor(ids.get(href.substring(1)), depth + 1) : null;
        }
        return new Color(Math.round(r / count), Math.round(g / count), Math.round(b / count), Math.round(a / count * 255));
    }

    /** CSS color: #rgb, #rrggbb, #rrggbbaa, rgb(), rgba(), named colors */
    static Color parseColor(String v) {
        if (v == null) {
            return null;
        }
        v = v.trim().toLowerCase();
        try {
            if (v.startsWith("#")) {
                String h = v.substring(1);
                if (h.length() == 3 || h.length() == 4) {
                    StringBuilder sb = new StringBuilder();
                    for (char c : h.toCharArray()) {
                        sb.append(c).append(c);
                    }
                    h = sb.toString();
                }
                int r = Integer.parseInt(h.substring(0, 2), 16);
                int g = Integer.parseInt(h.substring(2, 4), 16);
                int b = Integer.parseInt(h.substring(4, 6), 16);
                int a = h.length() >= 8 ? Integer.parseInt(h.substring(6, 8), 16) : 255;
                return new Color(r, g, b, a);
            }
            if (v.startsWith("rgb")) {
                String inside = v.substring(v.indexOf('(') + 1, v.lastIndexOf(')'));
                String[] parts = inside.split("[,\\s/]+");
                int[] c = new int[3];
                for (int i = 0; i < 3; i++) {
                    String p = parts[i].trim();
                    c[i] = p.endsWith("%") ? Math.round(Float.parseFloat(p.substring(0, p.length() - 1)) * 2.55f)
                            : Math.round(Float.parseFloat(p));
                    c[i] = Math.max(0, Math.min(255, c[i]));
                }
                int a = parts.length > 3 ? Math.round(fraction(parts[3]) * 255) : 255;
                return new Color(c[0], c[1], c[2], a);
            }
        } catch (RuntimeException e) {
            return Color.BLACK;
        }
        Integer named = NAMED.get(v);
        return named != null ? new Color(named) : Color.BLACK;
    }

    private static final Map<String, Integer> NAMED = new HashMap<String, Integer>();

    static {
        String[] names = {
            "black:000000", "white:ffffff", "red:ff0000", "lime:00ff00", "blue:0000ff", "yellow:ffff00",
            "cyan:00ffff", "aqua:00ffff", "magenta:ff00ff", "fuchsia:ff00ff", "silver:c0c0c0", "gray:808080",
            "grey:808080", "maroon:800000", "olive:808000", "green:008000", "purple:800080", "teal:008080",
            "navy:000080", "orange:ffa500", "pink:ffc0cb", "brown:a52a2a", "gold:ffd700", "indigo:4b0082",
            "violet:ee82ee", "coral:ff7f50", "salmon:fa8072", "tomato:ff6347", "crimson:dc143c", "khaki:f0e68c",
            "beige:f5f5dc", "ivory:fffff0", "lavender:e6e6fa", "turquoise:40e0d0", "tan:d2b48c", "plum:dda0dd",
            "orchid:da70d6", "chocolate:d2691e", "sienna:a0522d", "peru:cd853f", "darkred:8b0000",
            "darkgreen:006400", "darkblue:00008b", "darkgray:a9a9a9", "darkgrey:a9a9a9", "lightgray:d3d3d3",
            "lightgrey:d3d3d3", "dimgray:696969", "dimgrey:696969", "skyblue:87ceeb", "steelblue:4682b4",
            "royalblue:4169e1", "slategray:708090", "forestgreen:228b22", "seagreen:2e8b57", "limegreen:32cd32",
            "olivedrab:6b8e23", "firebrick:b22222", "hotpink:ff69b4", "deeppink:ff1493", "orangered:ff4500",
            "darkorange:ff8c00", "goldenrod:daa520", "wheat:f5deb3", "linen:faf0e6", "snow:fffafa",
            "whitesmoke:f5f5f5", "gainsboro:dcdcdc", "midnightblue:191970", "darkviolet:9400d3",
            "darkmagenta:8b008b", "mediumpurple:9370db", "lightblue:add8e6", "lightgreen:90ee90",
            "lightpink:ffb6c1", "lightyellow:ffffe0", "darkslategray:2f4f4f", "cornflowerblue:6495ed",
            "dodgerblue:1e90ff", "deepskyblue:00bfff", "aquamarine:7fffd4", "chartreuse:7fff00",
            "lawngreen:7cfc00", "springgreen:00ff7f", "mintcream:f5fffa", "honeydew:f0fff0", "azure:f0ffff",
            "aliceblue:f0f8ff", "ghostwhite:f8f8ff", "seashell:fff5ee", "oldlace:fdf5e6", "cornsilk:fff8dc",
            "lemonchiffon:fffacd", "moccasin:ffe4b5", "peachpuff:ffdab9", "mistyrose:ffe4e1",
            "rosybrown:bc8f8f", "indianred:cd5c5c", "saddlebrown:8b4513", "sandybrown:f4a460",
            "burlywood:deb887", "darkkhaki:bdb76b", "darkolivegreen:556b2f", "darkcyan:008b8b",
            "cadetblue:5f9ea0", "slateblue:6a5acd", "darkslateblue:483d8b", "mediumblue:0000cd",
            "blueviolet:8a2be2", "mediumorchid:ba55d3", "thistle:d8bfd8", "palevioletred:db7093",
            "mediumvioletred:c71585", "lightcoral:f08080", "darksalmon:e9967a", "lightsalmon:ffa07a"
        };
        for (String n : names) {
            String[] p = n.split(":");
            NAMED.put(p[0], Integer.parseInt(p[1], 16));
        }
    }
}
