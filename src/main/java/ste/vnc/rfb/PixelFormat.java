/*
 * Copyright 2026 ste.vnc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ste.vnc.rfb;

import java.util.Objects;

/**
 * The 16-byte PIXEL_FORMAT structure from RFB 6143 §7.4.
 *
 * <p>This initial implementation is deliberately limited to the "modern"
 * 24-bit true-colour case: 32 bits per pixel, depth 24, little-endian,
 * true-colour, with 8-bit channels at the conventional shifts
 * (red=16, green=8, blue=0). This matches the default pixel format used
 * by the large majority of modern VNC servers.
 */
public final class PixelFormat {

    public static final int WIRE_LENGTH = 16;

    private final int bitsPerPixel;
    private final int depth;
    private final boolean bigEndian;
    private final boolean trueColor;
    private final int redMax;
    private final int greenMax;
    private final int blueMax;
    private final int redShift;
    private final int greenShift;
    private final int blueShift;

    public PixelFormat(
            final int bitsPerPixel,
            final int depth,
            final boolean bigEndian,
            final boolean trueColor,
            final int redMax,
            final int greenMax,
            final int blueMax,
            final int redShift,
            final int greenShift,
            final int blueShift) {
        this.bitsPerPixel = bitsPerPixel;
        this.depth = depth;
        this.bigEndian = bigEndian;
        this.trueColor = trueColor;
        this.redMax = redMax;
        this.greenMax = greenMax;
        this.blueMax = blueMax;
        this.redShift = redShift;
        this.greenShift = greenShift;
        this.blueShift = blueShift;
    }

    /**
     * The 32-bit true-colour RGB888 format this library requests and
     * expects for its initial, limited implementation.
     */
    public static PixelFormat rgb888() {
        return new PixelFormat(32, 24, false, true, 255, 255, 255, 16, 8, 0);
    }

    public static PixelFormat fromBytes(final byte[] wireBytes) {
        Objects.requireNonNull(wireBytes, "wireBytes must not be null");
        if (wireBytes.length != WIRE_LENGTH) {
            throw new IllegalArgumentException(
                    "expected " + WIRE_LENGTH + " bytes, got " + wireBytes.length);
        }

        final int bitsPerPixel = wireBytes[0] & 0xFF;
        final int depth = wireBytes[1] & 0xFF;
        final boolean bigEndian = wireBytes[2] != 0;
        final boolean trueColor = wireBytes[3] != 0;
        // red/green/blue-max are protocol metadata, always big-endian on the
        // wire regardless of the big-endian-flag (which governs pixel data only)
        final int redMax = readUnsignedShort(wireBytes, 4, true);
        final int greenMax = readUnsignedShort(wireBytes, 6, true);
        final int blueMax = readUnsignedShort(wireBytes, 8, true);
        final int redShift = wireBytes[10] & 0xFF;
        final int greenShift = wireBytes[11] & 0xFF;
        final int blueShift = wireBytes[12] & 0xFF;
        // bytes 13-15 are padding

        return new PixelFormat(
                bitsPerPixel, depth, bigEndian, trueColor,
                redMax, greenMax, blueMax,
                redShift, greenShift, blueShift);
    }

    public byte[] toBytes() {
        final byte[] wireBytes = new byte[WIRE_LENGTH];
        wireBytes[0] = (byte) bitsPerPixel;
        wireBytes[1] = (byte) depth;
        wireBytes[2] = (byte) (bigEndian ? 1 : 0);
        wireBytes[3] = (byte) (trueColor ? 1 : 0);
        // always big-endian on the wire - see note in fromBytes()
        writeUnsignedShort(wireBytes, 4, redMax, true);
        writeUnsignedShort(wireBytes, 6, greenMax, true);
        writeUnsignedShort(wireBytes, 8, blueMax, true);
        wireBytes[10] = (byte) redShift;
        wireBytes[11] = (byte) greenShift;
        wireBytes[12] = (byte) blueShift;
        // bytes 13-15 stay zero (padding)
        return wireBytes;
    }

    /**
     * Extracts the 0xRRGGBB packed colour for a single raw pixel value
     * read off the wire (already assembled into host byte order as an
     * {@code int} according to {@link #isBigEndian()}).
     */
    public int toRgb(final long pixelValue) {
        final int red = extractChannel(pixelValue, redShift, redMax);
        final int green = extractChannel(pixelValue, greenShift, greenMax);
        final int blue = extractChannel(pixelValue, blueShift, blueMax);
        return (scaleTo8Bit(red, redMax) << 16)
                | (scaleTo8Bit(green, greenMax) << 8)
                | scaleTo8Bit(blue, blueMax);
    }

    private static int extractChannel(final long pixelValue, final int shift, final int max) {
        return (int) ((pixelValue >>> shift) & max);
    }

    private static int scaleTo8Bit(final int value, final int max) {
        if (max == 255) {
            return value;
        }
        if (max == 0) {
            return 0;
        }
        return (value * 255) / max;
    }

    private static int readUnsignedShort(final byte[] bytes, final int offset, final boolean bigEndian) {
        final int b0 = bytes[offset] & 0xFF;
        final int b1 = bytes[offset + 1] & 0xFF;
        return bigEndian ? (b0 << 8) | b1 : (b1 << 8) | b0;
    }

    private static void writeUnsignedShort(
            final byte[] bytes, final int offset, final int value, final boolean bigEndian) {
        final byte high = (byte) ((value >> 8) & 0xFF);
        final byte low = (byte) (value & 0xFF);
        if (bigEndian) {
            bytes[offset] = high;
            bytes[offset + 1] = low;
        } else {
            bytes[offset] = low;
            bytes[offset + 1] = high;
        }
    }

    public int getBitsPerPixel() {
        return bitsPerPixel;
    }

    public int getDepth() {
        return depth;
    }

    public boolean isBigEndian() {
        return bigEndian;
    }

    public boolean isTrueColor() {
        return trueColor;
    }

    public int getRedMax() {
        return redMax;
    }

    public int getGreenMax() {
        return greenMax;
    }

    public int getBlueMax() {
        return blueMax;
    }

    public int getRedShift() {
        return redShift;
    }

    public int getGreenShift() {
        return greenShift;
    }

    public int getBlueShift() {
        return blueShift;
    }

    public int bytesPerPixel() {
        return bitsPerPixel / 8;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PixelFormat)) {
            return false;
        }
        final PixelFormat other = (PixelFormat) o;
        return bitsPerPixel == other.bitsPerPixel
                && depth == other.depth
                && bigEndian == other.bigEndian
                && trueColor == other.trueColor
                && redMax == other.redMax
                && greenMax == other.greenMax
                && blueMax == other.blueMax
                && redShift == other.redShift
                && greenShift == other.greenShift
                && blueShift == other.blueShift;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                bitsPerPixel, depth, bigEndian, trueColor,
                redMax, greenMax, blueMax, redShift, greenShift, blueShift);
    }

    @Override
    public String toString() {
        return "PixelFormat{bpp=" + bitsPerPixel + ", depth=" + depth
                + ", bigEndian=" + bigEndian + ", trueColor=" + trueColor
                + ", redMax=" + redMax + ", greenMax=" + greenMax + ", blueMax=" + blueMax
                + ", redShift=" + redShift + ", greenShift=" + greenShift + ", blueShift=" + blueShift + '}';
    }
}
