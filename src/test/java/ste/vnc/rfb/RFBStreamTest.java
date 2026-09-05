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

import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class RFBStreamTest {

    private static RFBStream rfbForOutput(final ByteArrayOutputStream out) {
        return new RFBStream(new ByteArrayInputStream(new byte[0]), out);
    }

    @Test
    void setPixelFormat_starts_with_message_type_zero() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final RFBStream rfb = rfbForOutput(out);

        rfb.setPixelFormat(PixelFormat.rgb888());

        then(out.toByteArray()[0]).isEqualTo((byte) 0);
    }

    @Test
    void setPixelFormat_pads_three_bytes_after_the_type() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final RFBStream rfb = rfbForOutput(out);

        rfb.setPixelFormat(PixelFormat.rgb888());

        final byte[] bytes = out.toByteArray();
        then(bytes[1]).isEqualTo((byte) 0);
        then(bytes[2]).isEqualTo((byte) 0);
        then(bytes[3]).isEqualTo((byte) 0);
    }

    @Test
    void setPixelFormat_appends_the_pixel_format_bytes() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final RFBStream rfb = rfbForOutput(out);
        final PixelFormat format = PixelFormat.rgb888();

        rfb.setPixelFormat(format);

        final byte[] bytes = out.toByteArray();
        final byte[] trailing = new byte[PixelFormat.WIRE_LENGTH];
        System.arraycopy(bytes, 4, trailing, 0, PixelFormat.WIRE_LENGTH);
        then(trailing).isEqualTo(format.toBytes());
    }

    @Test
    void setPixelFormat_sends_twenty_bytes_total() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final RFBStream rfb = rfbForOutput(out);

        rfb.setPixelFormat(PixelFormat.rgb888());

        then(out.toByteArray()).hasSize(20);
    }

    @Test
    void setEncodings_starts_with_message_type_two() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final RFBStream rfb = rfbForOutput(out);

        rfb.setEncodings(EncodingType.RAW);

        then(out.toByteArray()[0]).isEqualTo((byte) 2);
    }

    @Test
    void setEncodings_encodes_the_count_as_big_endian_u16() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final RFBStream rfb = rfbForOutput(out);

        rfb.setEncodings(EncodingType.RAW, EncodingType.HEXTILE);

        final byte[] bytes = out.toByteArray();
        then(bytes[2]).isEqualTo((byte) 0);
        then(bytes[3]).isEqualTo((byte) 2);
    }

    @Test
    void setEncodings_writes_each_code_as_big_endian_s32() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final RFBStream rfb = rfbForOutput(out);

        rfb.setEncodings(EncodingType.RAW, EncodingType.HEXTILE);

        final byte[] bytes = out.toByteArray();
        then(bytes).containsExactly(
                2, 0, 0, 2,
                0, 0, 0, 0,
                0, 0, 0, 5);
    }

    @Test
    void setEncodings_handles_zero_encodings() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final RFBStream rfb = rfbForOutput(out);

        rfb.setEncodings(new EncodingType[0]);

        then(out.toByteArray()).containsExactly(2, 0, 0, 0);
    }

    @Test
    void setEncodingsInts_writes_raw_codes() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final RFBStream rfb = rfbForOutput(out);

        rfb.setEncodings(0, 5);

        final byte[] bytes = out.toByteArray();
        then(bytes).containsExactly(
                2, 0, 0, 2,
                0, 0, 0, 0,
                0, 0, 0, 5);
    }

    @Test
    void requestFramebufferUpdate_starts_with_message_type_three() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final RFBStream rfb = rfbForOutput(out);

        rfb.requestFramebufferUpdate(true, new Rectangle(0, 0, 800, 600));

        then(out.toByteArray()[0]).isEqualTo((byte) 3);
    }

    @Test
    void requestFramebufferUpdate_encodes_incremental_flag_as_one() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final RFBStream rfb = rfbForOutput(out);

        rfb.requestFramebufferUpdate(true, new Rectangle(0, 0, 800, 600));

        then(out.toByteArray()[1]).isEqualTo((byte) 1);
    }

    @Test
    void requestFramebufferUpdate_encodes_incremental_flag_as_zero() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final RFBStream rfb = rfbForOutput(out);

        rfb.requestFramebufferUpdate(false, new Rectangle(0, 0, 800, 600));

        then(out.toByteArray()[1]).isEqualTo((byte) 0);
    }

    @Test
    void requestFramebufferUpdate_encodes_rectangle_fields_as_big_endian_u16() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final RFBStream rfb = rfbForOutput(out);

        rfb.requestFramebufferUpdate(true, new Rectangle(0, 0, 800, 600));

        then(out.toByteArray()).containsExactly(
                3, 1,
                0, 0,
                0, 0,
                0x03, 0x20,
                0x02, 0x58);
    }

    @Test
    void requestFramebufferUpdate_encodes_nonzero_origin() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final RFBStream rfb = rfbForOutput(out);

        rfb.requestFramebufferUpdate(false, new Rectangle(10, 20, 30, 40));

        final byte[] bytes = out.toByteArray();
        then(bytes[2]).isEqualTo((byte) 0);
        then(bytes[3]).isEqualTo((byte) 10);
        then(bytes[4]).isEqualTo((byte) 0);
        then(bytes[5]).isEqualTo((byte) 20);
    }

    @Test
    void requestFramebufferUpdate_sends_ten_bytes_total() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final RFBStream rfb = rfbForOutput(out);

        rfb.requestFramebufferUpdate(true, new Rectangle(0, 0, 800, 600));

        then(out.toByteArray()).hasSize(10);
    }

    @Test
    void sendKeyEvent_encodes_key_down() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final RFBStream rfb = rfbForOutput(out);

        rfb.sendKeyEvent(true, 0x61);

        then(out.toByteArray()).containsExactly(
                4, 1,
                0, 0,
                0, 0, 0, 0x61);
    }

    @Test
    void sendKeyEvent_encodes_key_up() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final RFBStream rfb = rfbForOutput(out);

        rfb.sendKeyEvent(false, 0x61);

        then(out.toByteArray()[1]).isEqualTo((byte) 0);
    }

    @Test
    void sendKeyEvent_encodes_keysym_as_big_endian_u32() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final RFBStream rfb = rfbForOutput(out);

        rfb.sendKeyEvent(true, 0xFFE1);

        final byte[] bytes = out.toByteArray();
        then(bytes[4]).isEqualTo((byte) 0x00);
        then(bytes[5]).isEqualTo((byte) 0x00);
        then(bytes[6]).isEqualTo((byte) 0xFF);
        then(bytes[7]).isEqualTo((byte) 0xE1);
    }

    @Test
    void sendKeyEvent_sends_eight_bytes_total() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final RFBStream rfb = rfbForOutput(out);

        rfb.sendKeyEvent(true, 0x61);

        then(out.toByteArray()).hasSize(8);
    }

    @Test
    void sendPointerEvent_starts_with_message_type_five() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final RFBStream rfb = rfbForOutput(out);

        rfb.sendPointerEvent(0, 0, 0);

        then(out.toByteArray()[0]).isEqualTo((byte) 5);
    }

    @Test
    void sendPointerEvent_encodes_button_mask_and_coordinates() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final RFBStream rfb = rfbForOutput(out);

        rfb.sendPointerEvent(RFBStream.POINTER_BUTTON_LEFT, 800, 600);

        then(out.toByteArray()).containsExactly(
                5, 1,
                0x03, 0x20,
                0x02, 0x58);
    }

    @Test
    void sendPointerEvent_combines_button_bits_via_bitwise_or() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final RFBStream rfb = rfbForOutput(out);
        final int bothButtons = RFBStream.POINTER_BUTTON_LEFT | RFBStream.POINTER_BUTTON_RIGHT;

        rfb.sendPointerEvent(bothButtons, 0, 0);

        then(out.toByteArray()[1]).isEqualTo((byte) 0b101);
    }

    @Test
    void sendPointerEvent_rejects_negative_button_mask() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final RFBStream rfb = rfbForOutput(out);

        thenThrownBy(() -> rfb.sendPointerEvent(-1, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sendPointerEvent_rejects_button_mask_beyond_one_byte() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final RFBStream rfb = rfbForOutput(out);

        thenThrownBy(() -> rfb.sendPointerEvent(0x100, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sendPointerEvent_rejects_negative_x() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final RFBStream rfb = rfbForOutput(out);

        thenThrownBy(() -> rfb.sendPointerEvent(0, -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sendPointerEvent_rejects_negative_y() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final RFBStream rfb = rfbForOutput(out);

        thenThrownBy(() -> rfb.sendPointerEvent(0, 0, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sendPointerEvent_sends_six_bytes_total() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final RFBStream rfb = rfbForOutput(out);

        rfb.sendPointerEvent(0, 0, 0);

        then(out.toByteArray()).hasSize(6);
    }

    @Test
    void sendClipboardText_starts_with_message_type_six() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final RFBStream rfb = rfbForOutput(out);

        rfb.sendClipboardText("hello");

        then(out.toByteArray()[0]).isEqualTo((byte) 6);
    }

    @Test
    void sendClipboardText_pads_three_bytes_after_the_type() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final RFBStream rfb = rfbForOutput(out);

        rfb.sendClipboardText("hello");

        final byte[] bytes = out.toByteArray();
        then(bytes[1]).isEqualTo((byte) 0);
        then(bytes[2]).isEqualTo((byte) 0);
        then(bytes[3]).isEqualTo((byte) 0);
    }

    @Test
    void sendClipboardText_encodes_length_as_big_endian_u32() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final RFBStream rfb = rfbForOutput(out);

        rfb.sendClipboardText("hello");

        final byte[] bytes = out.toByteArray();
        then(bytes[4]).isEqualTo((byte) 0);
        then(bytes[5]).isEqualTo((byte) 0);
        then(bytes[6]).isEqualTo((byte) 0);
        then(bytes[7]).isEqualTo((byte) 5);
    }

    @Test
    void sendClipboardText_appends_latin1_encoded_text() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final RFBStream rfb = rfbForOutput(out);

        rfb.sendClipboardText("hello");

        final byte[] bytes = out.toByteArray();
        then(new String(bytes, 8, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("hello");
    }

    @Test
    void sendClipboardText_handles_empty_string() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final RFBStream rfb = rfbForOutput(out);

        rfb.sendClipboardText("");

        then(out.toByteArray()).containsExactly(6, 0, 0, 0, 0, 0, 0, 0);
    }

    @Test
    void sendClipboardText_rejects_null_text() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final RFBStream rfb = rfbForOutput(out);

        thenThrownBy(() -> rfb.sendClipboardText(null))
                .isInstanceOf(NullPointerException.class);
    }
}
