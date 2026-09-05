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

/**
 * Internal helper for reading fixed-length chunks off a blocking
 * {@link InputStream}, treating a premature end-of-stream as a protocol
 * failure rather than silently returning a short buffer.
 */
final class IoUtil {

    private IoUtil() {
    }

    static byte[] readFully(final InputStream in, final int length) throws IOException {
        final byte[] buffer = new byte[length];
        int read = 0;
        while (read < length) {
            final int n = in.read(buffer, read, length - read);
            if (n < 0) {
                throw new HandshakeException(
                        "connection closed while reading " + length + " byte(s), got " + read);
            }
            read += n;
        }
        return buffer;
    }

    static int readUnsignedByte(final InputStream in) throws IOException {
        final int b = in.read();
        if (b < 0) {
            throw new HandshakeException("connection closed while reading a byte");
        }
        return b;
    }

    static int readUnsignedShortBigEndian(final InputStream in) throws IOException {
        final byte[] bytes = readFully(in, 2);
        return ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
    }

    static long readUnsignedIntBigEndian(final InputStream in) throws IOException {
        final byte[] bytes = readFully(in, 4);
        return ((long) (bytes[0] & 0xFF) << 24)
                | ((bytes[1] & 0xFF) << 16)
                | ((bytes[2] & 0xFF) << 8)
                | (bytes[3] & 0xFF);
    }

    /**
     * Reads a signed 32-bit big-endian integer, as used for the
     * FramebufferUpdate rectangle encoding-type field, which can be
     * negative for pseudo-encodings.
     */
    static int readIntBigEndian(final InputStream in) throws IOException {
        final byte[] bytes = readFully(in, 4);
        return ((bytes[0] & 0xFF) << 24)
                | ((bytes[1] & 0xFF) << 16)
                | ((bytes[2] & 0xFF) << 8)
                | (bytes[3] & 0xFF);
    }

    /**
     * Reads a single pixel value directly off the stream, honouring the
     * given format's bits-per-pixel and endianness.
     */
    static long readPixelValue(final InputStream in, final PixelFormat format) throws IOException {
        final int bytesPerPixel = format.bytesPerPixel();
        final byte[] bytes = readFully(in, bytesPerPixel);

        long value = 0;
        if (format.isBigEndian()) {
            for (int i = 0; i < bytesPerPixel; i++) {
                value = (value << 8) | (bytes[i] & 0xFF);
            }
        } else {
            for (int i = bytesPerPixel - 1; i >= 0; i--) {
                value = (value << 8) | (bytes[i] & 0xFF);
            }
        }
        return value;
    }

    /**
     * Reads a 4-byte big-endian length prefix followed by that many
     * UTF-8 bytes, as used for failure-reason strings throughout the
     * RFB handshake.
     */
    static String readLengthPrefixedString(final InputStream in) throws IOException {
        final long length = readUnsignedIntBigEndian(in);
        if (length > Integer.MAX_VALUE) {
            throw new HandshakeException("implausibly long string length: " + length);
        }
        final byte[] bytes = readFully(in, (int) length);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }
}
