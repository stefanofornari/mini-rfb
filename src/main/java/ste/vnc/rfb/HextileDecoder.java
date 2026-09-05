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

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Decodes the Hextile encoding (RFC 6143 §7.7.4). The rectangle is
 * divided into 16x16 tiles (smaller at the right/bottom edges when the
 * rectangle isn't an exact multiple of 16), scanned left-to-right,
 * top-to-bottom. Each tile carries a subencoding mask byte describing
 * how it is encoded.
 */
public final class HextileDecoder extends Decoder {

    private static final int TILE_SIZE = 16;

    private static final int SUBENCODING_RAW = 1;
    private static final int SUBENCODING_BACKGROUND_SPECIFIED = 2;
    private static final int SUBENCODING_FOREGROUND_SPECIFIED = 4;
    private static final int SUBENCODING_ANY_SUBRECTS = 8;
    private static final int SUBENCODING_SUBRECTS_COLOURED = 16;

    public HextileDecoder() {
    }

    @Override
    public int[] decode(final PixelFormat format, final int width, final int height, final InputStream in)
            throws IOException {
        Objects.requireNonNull(in, "in must not be null");
        return decode(format, width, height, new StreamTileSource(in));
    }

    private int[] decode(
            final PixelFormat format,
            final int width,
            final int height,
            final TileSource source) throws IOException {
        Objects.requireNonNull(format, "format must not be null");
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("width and height must not be negative");
        }

        final int[] canvas = new int[width * height];

        long background = 0;
        long foreground = 0;

        for (int tileY = 0; tileY < height; tileY += TILE_SIZE) {
            final int tileHeight = Math.min(TILE_SIZE, height - tileY);
            for (int tileX = 0; tileX < width; tileX += TILE_SIZE) {
                final int tileWidth = Math.min(TILE_SIZE, width - tileX);

                final int subencoding = source.readUnsignedByte();

                if ((subencoding & SUBENCODING_RAW) != 0) {
                    decodeRawTile(format, canvas, width, tileX, tileY, tileWidth, tileHeight, source);
                    continue;
                }

                if ((subencoding & SUBENCODING_BACKGROUND_SPECIFIED) != 0) {
                    background = source.readPixelValue(format);
                }
                if ((subencoding & SUBENCODING_FOREGROUND_SPECIFIED) != 0) {
                    foreground = source.readPixelValue(format);
                }

                fillRect(canvas, width, tileX, tileY, tileWidth, tileHeight, format.toRgb(background));

                if ((subencoding & SUBENCODING_ANY_SUBRECTS) != 0) {
                    final boolean subrectsColoured = (subencoding & SUBENCODING_SUBRECTS_COLOURED) != 0;
                    final int subrectCount = source.readUnsignedByte();
                    for (int i = 0; i < subrectCount; i++) {
                        decodeSubrect(format, canvas, width, tileX, tileY, tileWidth, tileHeight,
                                source, subrectsColoured, foreground);
                    }
                }
            }
        }

        return canvas;
    }

    private void decodeRawTile(
            final PixelFormat format,
            final int[] canvas,
            final int canvasWidth,
            final int tileX,
            final int tileY,
            final int tileWidth,
            final int tileHeight,
            final TileSource source) throws IOException {
        for (int y = 0; y < tileHeight; y++) {
            for (int x = 0; x < tileWidth; x++) {
                final long pixel = source.readPixelValue(format);
                final int rgb = format.toRgb(pixel);
                canvas[(tileY + y) * canvasWidth + (tileX + x)] = rgb;
            }
        }
    }

    private void decodeSubrect(
            final PixelFormat format,
            final int[] canvas,
            final int canvasWidth,
            final int tileX,
            final int tileY,
            final int tileWidth,
            final int tileHeight,
            final TileSource source,
            final boolean subrectsColoured,
            final long foreground) throws IOException {
        final long color = subrectsColoured ? source.readPixelValue(format) : foreground;

        final int xy = source.readUnsignedByte();
        final int subX = (xy >> 4) & 0x0F;
        final int subY = xy & 0x0F;

        final int wh = source.readUnsignedByte();
        final int subWidth = (wh >> 4) & 0x0F;
        final int subHeight = wh & 0x0F;

        if (subX + subWidth + 1 > tileWidth || subY + subHeight + 1 > tileHeight) {
            throw new IllegalArgumentException(
                    "subrect at (" + subX + "," + subY + ") size " + (subWidth + 1) + "x" + (subHeight + 1)
                            + " exceeds tile bounds " + tileWidth + "x" + tileHeight);
        }

        fillRect(canvas, canvasWidth, tileX + subX, tileY + subY, subWidth + 1, subHeight + 1, format.toRgb(color));
    }

    private void fillRect(
            final int[] canvas,
            final int canvasWidth,
            final int x,
            final int y,
            final int w,
            final int h,
            final int rgb) {
        for (int row = 0; row < h; row++) {
            final int rowOffset = (y + row) * canvasWidth + x;
            for (int col = 0; col < w; col++) {
                canvas[rowOffset + col] = rgb;
            }
        }
    }

    private interface TileSource {
        int readUnsignedByte() throws IOException;
        long readPixelValue(PixelFormat format) throws IOException;
    }

    private static final class ArrayTileSource implements TileSource {
        private final ByteCursor cursor;

        ArrayTileSource(final byte[] data) {
            this.cursor = new ByteCursor(data);
        }

        @Override
        public int readUnsignedByte() {
            return cursor.readUnsignedByte();
        }

        @Override
        public long readPixelValue(final PixelFormat format) {
            return cursor.readPixelValue(format);
        }
    }

    private static final class StreamTileSource implements TileSource {
        private final InputStream in;

        StreamTileSource(final InputStream in) {
            this.in = in;
        }

        @Override
        public int readUnsignedByte() throws IOException {
            return IoUtil.readUnsignedByte(in);
        }

        @Override
        public long readPixelValue(final PixelFormat format) throws IOException {
            return IoUtil.readPixelValue(in, format);
        }
    }
}
