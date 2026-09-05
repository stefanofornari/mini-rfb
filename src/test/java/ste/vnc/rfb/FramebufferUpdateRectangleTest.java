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

import ste.vnc.rfb.FramebufferUpdateRectangle;
import ste.vnc.rfb.Rectangle;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

import org.junit.jupiter.api.Test;

class FramebufferUpdateRectangleTest {

    @Test
    void getters_return_constructor_values() {
        final Rectangle rectangle = new Rectangle(0, 0, 2, 1);
        final int[] pixels = {0xFF0000, 0x00FF00};

        final FramebufferUpdateRectangle update = new FramebufferUpdateRectangle(rectangle, pixels);

        then(update.getRectangle()).isEqualTo(rectangle);
        then(update.getPixels()).containsExactly(0xFF0000, 0x00FF00);
    }

    @Test
    void constructor_rejects_pixels_shorter_than_rectangle_area() {
        final Rectangle rectangle = new Rectangle(0, 0, 2, 2);

        thenThrownBy(() -> new FramebufferUpdateRectangle(rectangle, new int[] {1, 2, 3}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_rejects_pixels_longer_than_rectangle_area() {
        final Rectangle rectangle = new Rectangle(0, 0, 1, 1);

        thenThrownBy(() -> new FramebufferUpdateRectangle(rectangle, new int[] {1, 2}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_rejects_null_rectangle() {
        thenThrownBy(() -> new FramebufferUpdateRectangle(null, new int[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_rejects_null_pixels() {
        thenThrownBy(() -> new FramebufferUpdateRectangle(new Rectangle(0, 0, 0, 0), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
