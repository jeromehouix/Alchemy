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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * Resolves the folders Alchemy reads from, independently of the working directory.
 *
 * Alchemy historically used paths relative to the working directory ("modules",
 * "shapes"...). A packaged macOS application is launched with "/" as working
 * directory, so the packager passes the application folder in the
 * {@code alchemy.home} system property instead.
 */
public final class AlcPaths {

    private AlcPaths() {
    }

    /** True when running from a packaged application (alchemy.home is set) */
    public static boolean isPackaged() {
        return System.getProperty("alchemy.home") != null;
    }

    /** Folder containing Alchemy.jar, modules/, shapes/ and the manual */
    public static File home() {
        String home = System.getProperty("alchemy.home");
        if (home == null) {
            home = System.getProperty("user.dir");
        }
        return new File(home);
    }

    /** A file or folder inside the Alchemy home folder */
    public static File file(String name) {
        return new File(home(), name);
    }

    /**
     * Default folder to load the stamps (Pull Shapes...) from.
     *
     * When packaged, the application bundle is read-only, so the stamps live in
     * ~/Documents/Alchemy/shapes. On first launch that folder is created and
     * filled with a copy of the shapes shipped with Alchemy. When not packaged,
     * the "shapes" folder next to Alchemy.jar is used, as before.
     */
    public static File defaultShapesDir() {
        File bundled = file("shapes");
        if (!isPackaged()) {
            return bundled;
        }
        File userShapes = new File(System.getProperty("user.home"), "Documents" + File.separator + "Alchemy" + File.separator + "shapes");
        if (!userShapes.exists() && bundled.isDirectory()) {
            try {
                copyFolder(bundled.toPath(), userShapes.toPath());
            } catch (IOException ex) {
                System.err.println("Could not copy the default shapes to " + userShapes);
                ex.printStackTrace();
                return bundled;
            }
        }
        return userShapes;
    }

    /** The folder to load stamps from: the preference if it exists, else the default */
    public static File shapesDir() {
        if (Alchemy.preferences != null && Alchemy.preferences.shapesPath != null) {
            File pref = new File(Alchemy.preferences.shapesPath);
            if (pref.isDirectory()) {
                return pref;
            }
        }
        return defaultShapesDir();
    }

    private static void copyFolder(Path source, Path target) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path p : (Iterable<Path>) paths::iterator) {
                Path dest = target.resolve(source.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(dest);
                } else {
                    Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
