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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Writes a PNG image row by row, so very large images (prints) never need to be
 * held in memory whole. RGB, 8 or 16 bits per channel, with the print
 * resolution (pHYs) so print software knows the physical size.
 */
final class AlcPngWriter implements Closeable {

    private final DataOutputStream file;
    private final DeflaterOutputStream image;
    private final int width, bitDepth;
    private final byte[] row;
    private int rowsWritten = 0;
    private final int height;

    /**
     * @param out       Destination
     * @param width     Width in pixels
     * @param height    Height in pixels
     * @param bitDepth  8 or 16 bits per channel
     * @param dpi       Print resolution, stored in the file (0 for none)
     */
    AlcPngWriter(OutputStream out, int width, int height, int bitDepth, double dpi) throws IOException {
        if (bitDepth != 8 && bitDepth != 16) {
            throw new IllegalArgumentException("8 or 16 bits per channel");
        }
        this.file = new DataOutputStream(new BufferedOutputStream(out, 1 << 16));
        this.width = width;
        this.height = height;
        this.bitDepth = bitDepth;
        this.row = new byte[1 + width * 3 * (bitDepth / 8)];

        file.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(ihdr);
        d.writeInt(width);
        d.writeInt(height);
        d.writeByte(bitDepth);
        d.writeByte(2);     // RGB
        d.writeByte(0);     // deflate
        d.writeByte(0);     // adaptive filtering
        d.writeByte(0);     // no interlace
        chunk("IHDR", ihdr.toByteArray());
        if (dpi > 0) {
            ByteArrayOutputStream phys = new ByteArrayOutputStream();
            DataOutputStream p = new DataOutputStream(phys);
            int perMeter = (int) Math.round(dpi / 0.0254);
            p.writeInt(perMeter);
            p.writeInt(perMeter);
            p.writeByte(1);  // unit: meter
            chunk("pHYs", phys.toByteArray());
        }
        chunk("tEXt", "Software\0Alchemy".getBytes(StandardCharsets.ISO_8859_1));
        image = new DeflaterOutputStream(new IdatStream(), new Deflater(6), 1 << 16);
    }

    /** Write one row of 8-bit values, r g b per pixel (width * 3 values, 0-255) */
    void writeRow8(int[] rgb) throws IOException {
        row[0] = 1;     // "Sub" filter: compresses smooth images better
        int prev0 = 0, prev1 = 0, prev2 = 0;
        for (int x = 0; x < width; x++) {
            int r = rgb[x * 3], g = rgb[x * 3 + 1], b = rgb[x * 3 + 2];
            row[1 + x * 3] = (byte) (r - prev0);
            row[2 + x * 3] = (byte) (g - prev1);
            row[3 + x * 3] = (byte) (b - prev2);
            prev0 = r;
            prev1 = g;
            prev2 = b;
        }
        image.write(row);
        rowsWritten++;
    }

    /** Write one row of 16-bit values, r g b per pixel (width * 3 values, 0-65535) */
    void writeRow16(int[] rgb) throws IOException {
        row[0] = 0;
        for (int i = 0; i < width * 3; i++) {
            int v = rgb[i];
            row[1 + i * 2] = (byte) (v >> 8);
            row[2 + i * 2] = (byte) v;
        }
        image.write(row);
        rowsWritten++;
    }

    int getBitDepth() {
        return bitDepth;
    }

    @Override
    public void close() throws IOException {
        if (rowsWritten != height) {
            // Cancelled or failed: close the file as it is, the caller deletes it
            file.close();
            return;
        }
        image.close();
        chunk("IEND", new byte[0]);
        file.close();
    }

    private void chunk(String type, byte[] data) throws IOException {
        byte[] t = type.getBytes(StandardCharsets.ISO_8859_1);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(data);
        file.writeInt(data.length);
        file.write(t);
        file.write(data);
        file.writeInt((int) crc.getValue());
    }

    /** Cuts the compressed data into IDAT chunks */
    private final class IdatStream extends OutputStream {

        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream(1 << 20);

        @Override
        public void write(int b) throws IOException {
            buffer.write(b);
            if (buffer.size() >= 1 << 20) {
                flushChunk();
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            buffer.write(b, off, len);
            if (buffer.size() >= 1 << 20) {
                flushChunk();
            }
        }

        private void flushChunk() throws IOException {
            if (buffer.size() > 0) {
                chunk("IDAT", buffer.toByteArray());
                buffer.reset();
            }
        }

        @Override
        public void close() throws IOException {
            flushChunk();
        }
    }
}
