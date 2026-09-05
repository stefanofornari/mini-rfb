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

public final class FramebufferUpdateRectangle {

    public static final int ENCODING_RAW = 0;
    public static final int ENCODING_HEXTILE = 5;
    public static final int ENCODING_COPYRECT = 1;

    private final Rectangle rectangle;
    private final int[] pixels;
    private final int encodingType;
    private final int sourceX;
    private final int sourceY;

    public FramebufferUpdateRectangle(final Rectangle rectangle, final int[] pixels) {
        this(rectangle, pixels, ENCODING_RAW, 0, 0);
    }

    public FramebufferUpdateRectangle(final Rectangle rectangle, final int[] pixels, final int encodingType, final int sourceX, final int sourceY) {
        if (pixels == null) {
            throw new IllegalArgumentException("pixels must not be null");
        }
        if (rectangle == null) {
            throw new IllegalArgumentException("rectangle must not be null");
        }
        this.pixels = pixels;
        this.rectangle = rectangle;
        this.encodingType = encodingType;
        this.sourceX = sourceX;
        this.sourceY = sourceY;
        if (pixels.length != rectangle.area()) {
            throw new IllegalArgumentException(
                    "pixels length " + pixels.length + " does not match rectangle area " + rectangle.area());
        }
    }

    public Rectangle getRectangle() {
        return rectangle;
    }

    public int[] getPixels() {
        return pixels;
    }

    public int getEncodingType() {
        return encodingType;
    }

    public int getSourceX() {
        return sourceX;
    }

    public int getSourceY() {
        return sourceY;
    }

    public boolean isCopyRect() {
        return encodingType == ENCODING_COPYRECT;
    }
}
