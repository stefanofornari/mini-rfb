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
 * The ServerInit message (RFC 6143 §7.3.2): the server's initial
 * framebuffer dimensions, its native pixel format, and a display name.
 */
public final class ServerInit {

    private final int framebufferWidth;
    private final int framebufferHeight;
    private final PixelFormat pixelFormat;
    private final String name;

    public ServerInit(
            final int framebufferWidth,
            final int framebufferHeight,
            final PixelFormat pixelFormat,
            final String name) {
        this.framebufferWidth = framebufferWidth;
        this.framebufferHeight = framebufferHeight;
        this.pixelFormat = Objects.requireNonNull(pixelFormat, "pixelFormat must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
    }

    public static ServerInit read(final InputStream in) throws IOException {
        final int width = IoUtil.readUnsignedShortBigEndian(in);
        final int height = IoUtil.readUnsignedShortBigEndian(in);
        final byte[] pixelFormatBytes = IoUtil.readFully(in, PixelFormat.WIRE_LENGTH);
        final PixelFormat pixelFormat = PixelFormat.fromBytes(pixelFormatBytes);
        final String name = IoUtil.readLengthPrefixedString(in);
        return new ServerInit(width, height, pixelFormat, name);
    }

    public int getFramebufferWidth() {
        return framebufferWidth;
    }

    public int getFramebufferHeight() {
        return framebufferHeight;
    }

    public PixelFormat getPixelFormat() {
        return pixelFormat;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ServerInit)) {
            return false;
        }
        final ServerInit other = (ServerInit) o;
        return framebufferWidth == other.framebufferWidth
                && framebufferHeight == other.framebufferHeight
                && pixelFormat.equals(other.pixelFormat)
                && name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(framebufferWidth, framebufferHeight, pixelFormat, name);
    }

    @Override
    public String toString() {
        return "ServerInit{" + framebufferWidth + "x" + framebufferHeight
                + ", pixelFormat=" + pixelFormat + ", name='" + name + "'}";
    }
}
