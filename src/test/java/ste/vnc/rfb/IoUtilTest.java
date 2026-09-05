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

import ste.vnc.rfb.HandshakeException;
import ste.vnc.rfb.IoUtil;
import ste.vnc.rfb.PixelFormat;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

class IoUtilTest {

    @Test
    void read_fully_returns_requested_number_of_bytes() throws Exception {
        final byte[] result = IoUtil.readFully(new ByteArrayInputStream(new byte[] {1, 2, 3, 4}), 3);

        then(result).containsExactly(1, 2, 3);
    }

    @Test
    void read_fully_throws_on_premature_end_of_stream() {
        thenThrownBy(() -> IoUtil.readFully(new ByteArrayInputStream(new byte[] {1, 2}), 5))
                .isInstanceOf(HandshakeException.class);
    }

    @Test
    void read_unsigned_byte_returns_value_without_sign_extension() throws Exception {
        final int value = IoUtil.readUnsignedByte(new ByteArrayInputStream(new byte[] {(byte) 0xFF}));

        then(value).isEqualTo(255);
    }

    @Test
    void read_unsigned_byte_throws_on_empty_stream() {
        thenThrownBy(() -> IoUtil.readUnsignedByte(new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(HandshakeException.class);
    }

    @Test
    void read_unsigned_short_big_endian_combines_bytes_correctly() throws Exception {
        final int value = IoUtil.readUnsignedShortBigEndian(new ByteArrayInputStream(new byte[] {0x01, 0x02}));

        then(value).isEqualTo(0x0102);
    }

    @Test
    void read_unsigned_int_big_endian_combines_bytes_correctly() throws Exception {
        final long value = IoUtil.readUnsignedIntBigEndian(
                new ByteArrayInputStream(new byte[] {0x00, 0x00, 0x01, 0x02}));

        then(value).isEqualTo(0x0102L);
    }

    @Test
    void read_unsigned_int_big_endian_handles_high_bit_without_sign_extension() throws Exception {
        final long value = IoUtil.readUnsignedIntBigEndian(
                new ByteArrayInputStream(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF}));

        then(value).isEqualTo(0xFFFFFFFFL);
    }

    @Test
    void read_int_big_endian_returns_positive_value() throws Exception {
        final int value = IoUtil.readIntBigEndian(new ByteArrayInputStream(new byte[] {0x00, 0x00, 0x00, 0x05}));

        then(value).isEqualTo(5);
    }

    @Test
    void read_int_big_endian_returns_negative_value_for_pseudo_encodings() throws Exception {
        // -223 (DesktopSize pseudo-encoding) as a signed 32-bit big-endian value
        final int value = IoUtil.readIntBigEndian(
                new ByteArrayInputStream(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0x21}));

        then(value).isEqualTo(-223);
    }

    @Test
    void read_pixel_value_from_stream_reads_little_endian_bytes_in_order() throws Exception {
        final PixelFormat littleEndian = new PixelFormat(32, 24, false, true, 255, 255, 255, 16, 8, 0);

        final long value = IoUtil.readPixelValue(
                new ByteArrayInputStream(new byte[] {0x33, 0x22, 0x11, 0x00}), littleEndian);

        then(value).isEqualTo(0x00112233L);
    }

    @Test
    void read_pixel_value_from_stream_reads_big_endian_bytes_in_order() throws Exception {
        final PixelFormat bigEndian = new PixelFormat(32, 24, true, true, 255, 255, 255, 16, 8, 0);

        final long value = IoUtil.readPixelValue(
                new ByteArrayInputStream(new byte[] {0x00, 0x11, 0x22, 0x33}), bigEndian);

        then(value).isEqualTo(0x00112233L);
    }
}
