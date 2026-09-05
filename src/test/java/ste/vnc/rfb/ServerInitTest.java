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

import ste.vnc.rfb.ServerInit;
import ste.vnc.rfb.PixelFormat;
import static org.assertj.core.api.BDDAssertions.then;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ServerInitTest {

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

    private static byte[] u16(final int value) {
        return new byte[] {(byte) ((value >> 8) & 0xFF), (byte) (value & 0xFF)};
    }

    private static byte[] u32(final int value) {
        return new byte[] {
            (byte) ((value >> 24) & 0xFF),
            (byte) ((value >> 16) & 0xFF),
            (byte) ((value >> 8) & 0xFF),
            (byte) (value & 0xFF)
        };
    }

    @Test
    void read_parses_framebuffer_dimensions() throws Exception {
        final String name = "desktop";
        final byte[] wire = concat(
                u16(1920), u16(1080),
                PixelFormat.rgb888().toBytes(),
                u32(name.length()), name.getBytes(StandardCharsets.UTF_8));

        final ServerInit serverInit = ServerInit.read(new ByteArrayInputStream(wire));

        then(serverInit.getFramebufferWidth()).isEqualTo(1920);
        then(serverInit.getFramebufferHeight()).isEqualTo(1080);
    }

    @Test
    void read_parses_pixel_format() throws Exception {
        final String name = "desktop";
        final byte[] wire = concat(
                u16(800), u16(600),
                PixelFormat.rgb888().toBytes(),
                u32(name.length()), name.getBytes(StandardCharsets.UTF_8));

        final ServerInit serverInit = ServerInit.read(new ByteArrayInputStream(wire));

        then(serverInit.getPixelFormat()).isEqualTo(PixelFormat.rgb888());
    }

    @Test
    void read_parses_name() throws Exception {
        final String name = "my-remote-desktop";
        final byte[] wire = concat(
                u16(800), u16(600),
                PixelFormat.rgb888().toBytes(),
                u32(name.length()), name.getBytes(StandardCharsets.UTF_8));

        final ServerInit serverInit = ServerInit.read(new ByteArrayInputStream(wire));

        then(serverInit.getName()).isEqualTo(name);
    }

    @Test
    void read_handles_empty_name() throws Exception {
        final byte[] wire = concat(u16(800), u16(600), PixelFormat.rgb888().toBytes(), u32(0));

        final ServerInit serverInit = ServerInit.read(new ByteArrayInputStream(wire));

        then(serverInit.getName()).isEmpty();
    }

    @Test
    void equals_is_true_for_same_field_values() {
        final ServerInit a = new ServerInit(800, 600, PixelFormat.rgb888(), "desktop");
        final ServerInit b = new ServerInit(800, 600, PixelFormat.rgb888(), "desktop");

        then(a).isEqualTo(b);
    }

    @Test
    void equals_is_false_for_different_name() {
        final ServerInit a = new ServerInit(800, 600, PixelFormat.rgb888(), "desktop");
        final ServerInit b = new ServerInit(800, 600, PixelFormat.rgb888(), "other");

        then(a).isNotEqualTo(b);
    }
}
