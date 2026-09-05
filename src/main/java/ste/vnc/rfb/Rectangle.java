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
 * A rectangular region of the framebuffer, as used by FramebufferUpdate
 * rectangle headers (RFC 6143 §7.6.1). Coordinates and dimensions are
 * unsigned 16-bit values on the wire.
 */
public final class Rectangle {

    private final int x;
    private final int y;
    private final int width;
    private final int height;

    public Rectangle(final int x, final int y, final int width, final int height) {
        if (x < 0 || x > 0xFFFF) {
            throw new IllegalArgumentException("x out of range: " + x);
        }
        if (y < 0 || y > 0xFFFF) {
            throw new IllegalArgumentException("y out of range: " + y);
        }
        if (width < 0 || width > 0xFFFF) {
            throw new IllegalArgumentException("width out of range: " + width);
        }
        if (height < 0 || height > 0xFFFF) {
            throw new IllegalArgumentException("height out of range: " + height);
        }
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int area() {
        return width * height;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Rectangle)) {
            return false;
        }
        final Rectangle other = (Rectangle) o;
        return x == other.x && y == other.y && width == other.width && height == other.height;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, width, height);
    }

    @Override
    public String toString() {
        return "Rectangle{x=" + x + ", y=" + y + ", width=" + width + ", height=" + height + '}';
    }
}
