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
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

import org.junit.jupiter.api.Test;

class PixelFormatTest {

    @Test
    void rgb888_uses_32_bits_per_pixel() {
        then(PixelFormat.rgb888().getBitsPerPixel()).isEqualTo(32);
    }

    @Test
    void rgb888_uses_24_bit_depth() {
        then(PixelFormat.rgb888().getDepth()).isEqualTo(24);
    }

    @Test
    void rgb888_is_true_color() {
        then(PixelFormat.rgb888().isTrueColor()).isTrue();
    }

    @Test
    void rgb888_is_little_endian() {
        then(PixelFormat.rgb888().isBigEndian()).isFalse();
    }

    @Test
    void rgb888_uses_full_range_channels() {
        final PixelFormat format = PixelFormat.rgb888();

        then(format.getRedMax()).isEqualTo(255);
        then(format.getGreenMax()).isEqualTo(255);
        then(format.getBlueMax()).isEqualTo(255);
    }

    @Test
    void rgb888_uses_conventional_channel_shifts() {
        final PixelFormat format = PixelFormat.rgb888();

        then(format.getRedShift()).isEqualTo(16);
        then(format.getGreenShift()).isEqualTo(8);
        then(format.getBlueShift()).isEqualTo(0);
    }

    @Test
    void bytes_per_pixel_derives_from_bits_per_pixel() {
        then(PixelFormat.rgb888().bytesPerPixel()).isEqualTo(4);
    }

    @Test
    void to_bytes_produces_sixteen_bytes() {
        then(PixelFormat.rgb888().toBytes()).hasSize(16);
    }

    @Test
    void to_bytes_round_trips_through_from_bytes() {
        final PixelFormat original = PixelFormat.rgb888();

        final PixelFormat roundTripped = PixelFormat.fromBytes(original.toBytes());

        then(roundTripped).isEqualTo(original);
    }

    @Test
    void to_bytes_encodes_max_values_big_endian_even_when_pixel_data_is_little_endian() {
        // the big-endian-flag governs pixel data only; red/green/blue-max
        // are protocol metadata and are always big-endian on the wire
        final PixelFormat format = new PixelFormat(16, 16, false, true, 300, 63, 31, 11, 5, 0);

        final byte[] bytes = format.toBytes();

        // redMax=300 (0x012C) big-endian in bytes[4..5]
        then(bytes[4]).isEqualTo((byte) 0x01);
        then(bytes[5]).isEqualTo((byte) 0x2C);
    }

    @Test
    void to_bytes_encodes_max_values_big_endian_when_pixel_data_is_also_big_endian() {
        final PixelFormat format = new PixelFormat(16, 16, true, true, 300, 63, 31, 11, 5, 0);

        final byte[] bytes = format.toBytes();

        then(bytes[4]).isEqualTo((byte) 0x01);
        then(bytes[5]).isEqualTo((byte) 0x2C);
    }

    @Test
    void from_bytes_rejects_wrong_length() {
        thenThrownBy(() -> PixelFormat.fromBytes(new byte[15]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void from_bytes_rejects_null_input() {
        thenThrownBy(() -> PixelFormat.fromBytes(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void to_rgb_extracts_pure_red() {
        final PixelFormat format = PixelFormat.rgb888();

        final int rgb = format.toRgb(0x00FF0000L);

        then(rgb).isEqualTo(0xFF0000);
    }

    @Test
    void to_rgb_extracts_pure_green() {
        final PixelFormat format = PixelFormat.rgb888();

        final int rgb = format.toRgb(0x0000FF00L);

        then(rgb).isEqualTo(0x00FF00);
    }

    @Test
    void to_rgb_extracts_pure_blue() {
        final PixelFormat format = PixelFormat.rgb888();

        final int rgb = format.toRgb(0x000000FFL);

        then(rgb).isEqualTo(0x0000FF);
    }

    @Test
    void to_rgb_extracts_mixed_channels() {
        final PixelFormat format = PixelFormat.rgb888();

        final int rgb = format.toRgb(0x00112233L);

        then(rgb).isEqualTo(0x112233);
    }

    @Test
    void to_rgb_ignores_bits_outside_bits_per_pixel() {
        final PixelFormat format = PixelFormat.rgb888();

        // top byte beyond the 24 bits of colour should not leak into the result
        final int rgb = format.toRgb(0xFF112233L);

        then(rgb).isEqualTo(0x112233);
    }

    @Test
    void to_rgb_scales_narrower_channel_ranges_up_to_8_bits() {
        // RGB565: red 5 bits (max 31), green 6 bits (max 63), blue 5 bits (max 31)
        final PixelFormat format = new PixelFormat(16, 16, false, true, 31, 63, 31, 11, 5, 0);

        final int rgb = format.toRgb(0xFFFFL); // all channels maxed out

        then(rgb).isEqualTo(0xFFFFFF);
    }

    @Test
    void equals_is_true_for_same_field_values() {
        then(PixelFormat.rgb888()).isEqualTo(PixelFormat.rgb888());
    }

    @Test
    void equals_is_false_for_different_depth() {
        final PixelFormat other = new PixelFormat(32, 16, false, true, 255, 255, 255, 16, 8, 0);

        then(PixelFormat.rgb888()).isNotEqualTo(other);
    }

    @Test
    void hash_code_is_consistent_with_equals() {
        then(PixelFormat.rgb888().hashCode()).isEqualTo(PixelFormat.rgb888().hashCode());
    }
}
