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

import ste.vnc.rfb.PixelFormat;
import ste.vnc.rfb.ByteCursor;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

import org.junit.jupiter.api.Test;

class ByteCursorTest {

    @Test
    void read_unsigned_byte_returns_value_without_sign_extension() {
        final ByteCursor cursor = new ByteCursor(new byte[] {(byte) 0xFF});

        then(cursor.readUnsignedByte()).isEqualTo(255);
    }

    @Test
    void read_unsigned_byte_advances_position() {
        final ByteCursor cursor = new ByteCursor(new byte[] {1, 2, 3});

        cursor.readUnsignedByte();

        then(cursor.position()).isEqualTo(1);
    }

    @Test
    void read_unsigned_byte_reads_sequentially() {
        final ByteCursor cursor = new ByteCursor(new byte[] {10, 20, 30});

        then(cursor.readUnsignedByte()).isEqualTo(10);
        then(cursor.readUnsignedByte()).isEqualTo(20);
        then(cursor.readUnsignedByte()).isEqualTo(30);
    }

    @Test
    void read_unsigned_byte_throws_when_no_data_remains() {
        final ByteCursor cursor = new ByteCursor(new byte[0]);

        thenThrownBy(cursor::readUnsignedByte).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void read_pixel_value_reads_little_endian_bytes_in_order() {
        final PixelFormat littleEndian = new PixelFormat(32, 24, false, true, 255, 255, 255, 16, 8, 0);
        final ByteCursor cursor = new ByteCursor(new byte[] {0x33, 0x22, 0x11, 0x00});

        final long value = cursor.readPixelValue(littleEndian);

        then(value).isEqualTo(0x00112233L);
    }

    @Test
    void read_pixel_value_reads_big_endian_bytes_in_order() {
        final PixelFormat bigEndian = new PixelFormat(32, 24, true, true, 255, 255, 255, 16, 8, 0);
        final ByteCursor cursor = new ByteCursor(new byte[] {0x00, 0x11, 0x22, 0x33});

        final long value = cursor.readPixelValue(bigEndian);

        then(value).isEqualTo(0x00112233L);
    }

    @Test
    void read_pixel_value_advances_by_bytes_per_pixel() {
        final PixelFormat format = PixelFormat.rgb888();
        final ByteCursor cursor = new ByteCursor(new byte[] {1, 2, 3, 4, 5});

        cursor.readPixelValue(format);

        then(cursor.position()).isEqualTo(4);
    }

    @Test
    void read_pixel_value_throws_when_insufficient_bytes_remain() {
        final PixelFormat format = PixelFormat.rgb888();
        final ByteCursor cursor = new ByteCursor(new byte[] {1, 2, 3});

        thenThrownBy(() -> cursor.readPixelValue(format)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void remaining_reflects_bytes_left_to_read() {
        final ByteCursor cursor = new ByteCursor(new byte[] {1, 2, 3});

        cursor.readUnsignedByte();

        then(cursor.remaining()).isEqualTo(2);
    }

    @Test
    void has_remaining_is_true_when_enough_bytes_are_left() {
        final ByteCursor cursor = new ByteCursor(new byte[] {1, 2, 3});

        then(cursor.hasRemaining(3)).isTrue();
    }

    @Test
    void has_remaining_is_false_when_not_enough_bytes_are_left() {
        final ByteCursor cursor = new ByteCursor(new byte[] {1, 2, 3});

        then(cursor.hasRemaining(4)).isFalse();
    }

    @Test
    void constructor_rejects_null_data() {
        thenThrownBy(() -> new ByteCursor(null)).isInstanceOf(NullPointerException.class);
    }
}
