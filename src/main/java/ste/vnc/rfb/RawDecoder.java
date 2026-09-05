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
 * Decodes the Raw encoding (RFC 6143 §7.7.1): pixel values for the
 * rectangle in left-to-right, top-to-bottom order, with no compression.
 */
public final class RawDecoder extends Decoder {

    public RawDecoder() {
    }

    @Override
    public int[] decode(final PixelFormat format, final int width, final int height, final InputStream in)
            throws IOException {
        Objects.requireNonNull(format, "format must not be null");
        Objects.requireNonNull(in, "in must not be null");
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("width and height must not be negative");
        }

        final int pixelCount = width * height;
        final int expectedBytes = pixelCount * format.bytesPerPixel();
        final byte[] data = IoUtil.readFully(in, expectedBytes);

        final int[] rgb = new int[pixelCount];
        final ByteCursor cursor = new ByteCursor(data);
        for (int i = 0; i < pixelCount; i++) {
            rgb[i] = format.toRgb(cursor.readPixelValue(format));
        }
        return rgb;
    }
}
