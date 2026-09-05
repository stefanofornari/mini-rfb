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

import ste.vnc.rfb.Rectangle;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

import org.junit.jupiter.api.Test;

class RectangleTest {

    @Test
    void getters_return_constructor_values() {
        final Rectangle rectangle = new Rectangle(1, 2, 3, 4);

        then(rectangle.x()).isEqualTo(1);
        then(rectangle.y()).isEqualTo(2);
        then(rectangle.width()).isEqualTo(3);
        then(rectangle.height()).isEqualTo(4);
    }

    @Test
    void area_multiplies_width_and_height() {
        then(new Rectangle(0, 0, 16, 16).area()).isEqualTo(256);
    }

    @Test
    void constructor_rejects_negative_x() {
        thenThrownBy(() -> new Rectangle(-1, 0, 1, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_rejects_negative_y() {
        thenThrownBy(() -> new Rectangle(0, -1, 1, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_rejects_negative_width() {
        thenThrownBy(() -> new Rectangle(0, 0, -1, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_rejects_negative_height() {
        thenThrownBy(() -> new Rectangle(0, 0, 1, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_rejects_width_beyond_16_bits() {
        thenThrownBy(() -> new Rectangle(0, 0, 0x10000, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_accepts_maximum_16_bit_values() {
        final Rectangle rectangle = new Rectangle(0xFFFF, 0xFFFF, 0xFFFF, 0xFFFF);

        then(rectangle.width()).isEqualTo(0xFFFF);
    }

    @Test
    void equals_is_true_for_same_coordinates_and_dimensions() {
        then(new Rectangle(1, 2, 3, 4)).isEqualTo(new Rectangle(1, 2, 3, 4));
    }

    @Test
    void equals_is_false_for_different_dimensions() {
        then(new Rectangle(1, 2, 3, 4)).isNotEqualTo(new Rectangle(1, 2, 3, 5));
    }

    @Test
    void hash_code_is_consistent_with_equals() {
        then(new Rectangle(1, 2, 3, 4).hashCode()).isEqualTo(new Rectangle(1, 2, 3, 4).hashCode());
    }
}
