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
 * A simple forward-only cursor over a byte array, used to parse
 * variable-length wire structures such as the Hextile encoding where the
 * amount of data consumed per tile isn't known in advance.
 */
public final class ByteCursor {

    private final byte[] data;
    private int position;

    public ByteCursor(final byte[] data) {
        this.data = Objects.requireNonNull(data, "data must not be null");
        this.position = 0;
    }

    public int readUnsignedByte() {
        requireRemaining(1);
        return data[position++] & 0xFF;
    }

    /**
     * Reads a pixel value according to the given format's bits-per-pixel
     * and endianness, advancing the cursor by {@code format.bytesPerPixel()}.
     */
    public long readPixelValue(final PixelFormat format) {
        final int bytesPerPixel = format.bytesPerPixel();
        requireRemaining(bytesPerPixel);

        long value = 0;
        if (format.isBigEndian()) {
            for (int i = 0; i < bytesPerPixel; i++) {
                value = (value << 8) | (data[position + i] & 0xFF);
            }
        } else {
            for (int i = bytesPerPixel - 1; i >= 0; i--) {
                value = (value << 8) | (data[position + i] & 0xFF);
            }
        }
        position += bytesPerPixel;
        return value;
    }

    public int remaining() {
        return data.length - position;
    }

    public boolean hasRemaining(final int count) {
        return remaining() >= count;
    }

    public int position() {
        return position;
    }

    private void requireRemaining(final int count) {
        if (!hasRemaining(count)) {
            throw new IllegalStateException(
                    "unexpected end of data: needed " + count + " byte(s), " + remaining() + " remaining");
        }
    }
}
