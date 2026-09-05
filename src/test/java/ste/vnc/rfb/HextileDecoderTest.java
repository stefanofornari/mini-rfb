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

import ste.vnc.rfb.HextileDecoder;
import ste.vnc.rfb.PixelFormat;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class HextileDecoderTest {

    private static final PixelFormat RGB888 = PixelFormat.rgb888();

    private static int[] decode(final PixelFormat format, final int width, final int height, final byte[] data)
            throws IOException {
        return new HextileDecoder().decode(format, width, height, new ByteArrayInputStream(data));
    }

    private static byte[] pixel(final int rgb) {
        return new byte[] {
            (byte) (rgb & 0xFF),
            (byte) ((rgb >> 8) & 0xFF),
            (byte) ((rgb >> 16) & 0xFF),
            0x00
        };
    }

    private static byte[] concat(final byte[]... chunks) {
        int length = 0;
        for (final byte[] chunk : chunks) {
            length += chunk.length;
        }
        final byte[] result = new byte[length];
        int offset = 0;
        for (final byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, result, offset, chunk.length);
            offset += chunk.length;
        }
        return result;
    }

    @Test
    void decode_reads_a_raw_tile_in_row_major_order() throws Exception {
        final byte[] data = concat(
                new byte[] {0x01},
                pixel(0xFF0000), pixel(0x00FF00),
                pixel(0x0000FF), pixel(0xFFFFFF));

        final int[] canvas = decode(RGB888, 2, 2, data);

        then(canvas).containsExactly(0xFF0000, 0x00FF00, 0x0000FF, 0xFFFFFF);
    }

    @Test
    void decode_fills_tile_with_background_when_only_background_specified() throws Exception {
        final byte[] data = concat(new byte[] {0x02}, pixel(0xFF0000));

        final int[] canvas = decode(RGB888, 4, 4, data);

        then(canvas).containsOnly(0xFF0000);
    }

    @Test
    void decode_persists_background_to_subsequent_tiles_that_do_not_respecify_it() throws Exception {
        final byte[] data = concat(
                new byte[] {0x02}, pixel(0xFF0000),
                new byte[] {0x00});

        final int[] canvas = decode(RGB888, 32, 16, data);

        then(canvas).containsOnly(0xFF0000);
    }

    @Test
    void decode_fills_uncoloured_subrects_with_foreground_over_background() throws Exception {
        final byte[] data = concat(
                new byte[] {0x02 | 0x04 | 0x08},
                pixel(0x0000FF),
                pixel(0xFF0000),
                new byte[] {0x01},
                new byte[] {0x23},
                new byte[] {0x34});

        final int[] canvas = decode(RGB888, 16, 16, data);

        then(canvas[0 * 16 + 0]).isEqualTo(0x0000FF);
        then(canvas[3 * 16 + 2]).isEqualTo(0xFF0000);
        then(canvas[7 * 16 + 5]).isEqualTo(0xFF0000);
        then(canvas[3 * 16 + 6]).isEqualTo(0x0000FF);
        then(canvas[8 * 16 + 2]).isEqualTo(0x0000FF);
    }

    @Test
    void decode_fills_coloured_subrects_with_their_own_colour() throws Exception {
        final byte[] data = concat(
                new byte[] {0x02 | 0x08 | 0x10},
                pixel(0xFFFFFF),
                new byte[] {0x01},
                pixel(0x00FF00),
                new byte[] {0x00},
                new byte[] {0x00});

        final int[] canvas = decode(RGB888, 16, 16, data);

        then(canvas[0]).isEqualTo(0x00FF00);
        then(canvas[1]).isEqualTo(0xFFFFFF);
    }

    @Test
    void decode_lays_out_multiple_tiles_left_to_right_top_to_bottom_with_edge_sizes() throws Exception {
        final byte[] data = concat(
                new byte[] {0x02}, pixel(0xFF0000),
                new byte[] {0x02}, pixel(0x00FF00),
                new byte[] {0x02}, pixel(0x0000FF),
                new byte[] {0x02}, pixel(0xFFFFFF));

        final int[] canvas = decode(RGB888, 20, 20, data);
        final int width = 20;

        then(canvas[0 * width + 0]).isEqualTo(0xFF0000);
        then(canvas[0 * width + 15]).isEqualTo(0xFF0000);
        then(canvas[0 * width + 16]).isEqualTo(0x00FF00);
        then(canvas[0 * width + 19]).isEqualTo(0x00FF00);
        then(canvas[16 * width + 0]).isEqualTo(0x0000FF);
        then(canvas[16 * width + 15]).isEqualTo(0x0000FF);
        then(canvas[16 * width + 16]).isEqualTo(0xFFFFFF);
        then(canvas[19 * width + 19]).isEqualTo(0xFFFFFF);
    }

    @Test
    void decode_throws_when_raw_tile_data_is_truncated() throws Exception {
        final byte[] data = concat(new byte[] {0x01}, pixel(0xFF0000));

        thenThrownBy(() -> decode(RGB888, 2, 2, data))
                .isInstanceOf(IOException.class);
    }

    @Test
    void decode_throws_when_subrect_exceeds_tile_bounds() throws Exception {
        final byte[] data = concat(
                new byte[] {0x08},
                new byte[] {0x01},
                new byte[] {0x10},
                new byte[] {(byte) 0xF0});

        thenThrownBy(() -> decode(RGB888, 16, 16, data))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decode_handles_empty_rectangle() throws Exception {
        final int[] canvas = decode(RGB888, 0, 0, new byte[0]);

        then(canvas).isEmpty();
    }

    @Test
    void decode_rejects_negative_width() throws Exception {
        thenThrownBy(() -> decode(RGB888, -1, 1, new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decode_rejects_negative_height() throws Exception {
        thenThrownBy(() -> decode(RGB888, 1, -1, new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decode_rejects_null_format() throws Exception {
        thenThrownBy(() -> new HextileDecoder().decode(null, 1, 1, new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void decode_rejects_null_stream() throws Exception {
        thenThrownBy(() -> new HextileDecoder().decode(RGB888, 1, 1, (java.io.InputStream) null))
                .isInstanceOf(NullPointerException.class);
    }
}
