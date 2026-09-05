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

import ste.vnc.rfb.RawDecoder;
import ste.vnc.rfb.PixelFormat;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class RawDecoderTest {

    private static final PixelFormat RGB888 = PixelFormat.rgb888();

    private static int[] decode(final PixelFormat format, final int width, final int height, final byte[] data)
            throws IOException {
        return new RawDecoder().decode(format, width, height, new ByteArrayInputStream(data));
    }

    @Test
    void decode_reads_a_single_pixel() throws Exception {
        final byte[] data = {0x33, 0x22, 0x11, 0x00};

        final int[] rgb = decode(RGB888, 1, 1, data);

        then(rgb).containsExactly(0x112233);
    }

    @Test
    void decode_reads_pixels_in_left_to_right_top_to_bottom_order() throws Exception {
        final byte[] data = {
            0x00, 0x00, (byte) 0xFF, 0x00,
            0x00, (byte) 0xFF, 0x00, 0x00,
            (byte) 0xFF, 0x00, 0x00, 0x00,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x00,
        };

        final int[] rgb = decode(RGB888, 2, 2, data);

        then(rgb).containsExactly(0xFF0000, 0x00FF00, 0x0000FF, 0xFFFFFF);
    }

    @Test
    void decode_handles_empty_rectangle() throws Exception {
        final int[] rgb = decode(RGB888, 0, 0, new byte[0]);

        then(rgb).isEmpty();
    }

    @Test
    void decode_rejects_data_shorter_than_expected() throws Exception {
        thenThrownBy(() -> decode(RGB888, 2, 1, new byte[4]))
                .isInstanceOf(IOException.class);
    }

    @Test
    void decode_accepts_stream_with_trailing_bytes() throws Exception {
        final byte[] data = {0x33, 0x22, 0x11, 0x00, 0x77}; // one extra trailing byte
        final ByteArrayInputStream in = new ByteArrayInputStream(data);

        final int[] rgb = new RawDecoder().decode(RGB888, 1, 1, in);

        then(rgb).containsExactly(0x112233);
        then(in.available()).isEqualTo(1);
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
        thenThrownBy(() -> new RawDecoder().decode(null, 1, 1, new ByteArrayInputStream(new byte[4])))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void decode_rejects_null_stream() throws Exception {
        thenThrownBy(() -> new RawDecoder().decode(RGB888, 1, 1, (java.io.InputStream) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void decode_from_stream_reads_exactly_the_expected_bytes() throws Exception {
        final byte[] data = {0x33, 0x22, 0x11, 0x00};

        final int[] rgb = new RawDecoder().decode(RGB888, 1, 1, new ByteArrayInputStream(data));

        then(rgb).containsExactly(0x112233);
    }

    @Test
    void decode_from_stream_leaves_trailing_bytes_unconsumed() throws Exception {
        final byte[] data = {0x33, 0x22, 0x11, 0x00, 0x77};
        final ByteArrayInputStream in = new ByteArrayInputStream(data);

        new RawDecoder().decode(RGB888, 1, 1, in);

        then(in.available()).isEqualTo(1);
    }

    @Test
    void decode_from_stream_throws_on_premature_end_of_stream() throws Exception {
        final ByteArrayInputStream in = new ByteArrayInputStream(new byte[] {0x33, 0x22});

        thenThrownBy(() -> new RawDecoder().decode(RGB888, 1, 1, in))
                .isInstanceOf(IOException.class);
    }
}
