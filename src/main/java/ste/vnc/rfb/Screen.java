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

public final class Screen {

    public int id;
    public Rectangle dimensions;
    public int flags;

    public Screen() {
        this.id = 0;
        this.dimensions = new Rectangle(0, 0, 0, 0);
        this.flags = 0;
    }

    public Screen(final int id, final Rectangle dimensions, final int flags) {
        this.id = id;
        this.dimensions = new Rectangle(
                dimensions.x(), dimensions.y(),
                dimensions.width(), dimensions.height());
        this.flags = flags;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Screen)) {
            return false;
        }
        final Screen other = (Screen) o;
        return id == other.id
                && dimensions.equals(other.dimensions)
                && flags == other.flags;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, dimensions, flags);
    }

    @Override
    public String toString() {
        return "Screen{id=" + id + ", dimensions=" + dimensions + ", flags=" + flags + "}";
    }
}
