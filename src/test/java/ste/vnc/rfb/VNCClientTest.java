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

import ste.vnc.rfb.FramebufferUpdateMessage;
import ste.vnc.rfb.IoUtil;
import ste.vnc.rfb.PixelFormat;
import ste.vnc.rfb.RFBStream;
import ste.vnc.rfb.ServerCutTextMessage;
import ste.vnc.rfb.ServerMessage;
import ste.vnc.rfb.VNCClient;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class VNCClientTest {

    private interface ServerScript {
        void run(InputStream fromClient, OutputStream toClient) throws IOException;
    }

    private static ServerSocket startFakeServer(final ServerScript script, final AtomicReference<Throwable> failure)
            throws IOException {
        final ServerSocket serverSocket = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress());
        final Thread thread = new Thread(() -> {
            try (Socket accepted = serverSocket.accept()) {
                script.run(accepted.getInputStream(), accepted.getOutputStream());
            } catch (final Throwable t) {
                failure.set(t);
            }
        });
        thread.setDaemon(true);
        thread.start();
        return serverSocket;
    }

    private static void writeServerInit(final OutputStream out, final int width, final int height, final String name)
            throws IOException {
        out.write(new byte[] {(byte) ((width >> 8) & 0xFF), (byte) (width & 0xFF)});
        out.write(new byte[] {(byte) ((height >> 8) & 0xFF), (byte) (height & 0xFF)});
        out.write(PixelFormat.rgb888().toBytes());
        final byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        out.write(new byte[] {0, 0, 0, (byte) nameBytes.length});
        out.write(nameBytes);
        out.flush();
    }

    private static void performServerSideHandshake(
            final InputStream fromClient, final OutputStream toClient, final int width, final int height,
            final String name) throws IOException {
        toClient.write("RFB 003.008\n".getBytes(StandardCharsets.US_ASCII));
        toClient.flush();
        IoUtil.readFully(fromClient, 12);
        toClient.write(new byte[] {1, 1});
        toClient.flush();
        IoUtil.readUnsignedByte(fromClient);
        toClient.write(new byte[] {0, 0, 0, 0});
        toClient.flush();
        IoUtil.readUnsignedByte(fromClient);
        writeServerInit(toClient, width, height, name);
    }

    private static VNCClient connect(final ServerSocket serverSocket, final boolean shared) throws IOException {
        final Socket socket = new Socket("localhost", serverSocket.getLocalPort());
        try {
            final RFBStream rfb = new RFBStream(socket.getInputStream(), socket.getOutputStream());
            rfb.handshake(shared);
            return new VNCClient(socket, rfb);
        } catch (final IOException | RuntimeException e) {
            socket.close();
            throw e;
        }
    }

    @Test
    void connect_performs_the_handshake_and_exposes_server_init() throws Exception {
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final ServerSocket serverSocket = startFakeServer((fromClient, toClient) -> {
            performServerSideHandshake(fromClient, toClient, 800, 600, "integration desktop");
            IoUtil.readFully(fromClient, 20);
            IoUtil.readFully(fromClient, 12);
        }, failure);

        try (VNCClient client = connect(serverSocket, true)) {
            then(client.getServerInit().getFramebufferWidth()).isEqualTo(800);
            then(client.getServerInit().getFramebufferHeight()).isEqualTo(600);
            then(client.getServerInit().getName()).isEqualTo("integration desktop");
            then(client.getPixelFormat()).isEqualTo(PixelFormat.rgb888());
        }

        then(failure.get()).isNull();
    }

    @Test
    void connect_sends_set_pixel_format_and_set_encodings_right_after_the_handshake() throws Exception {
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final AtomicReference<byte[]> capturedSetPixelFormat = new AtomicReference<>();
        final AtomicReference<byte[]> capturedSetEncodings = new AtomicReference<>();

        final ServerSocket serverSocket = startFakeServer((fromClient, toClient) -> {
            performServerSideHandshake(fromClient, toClient, 640, 480, "desktop");
            capturedSetPixelFormat.set(IoUtil.readFully(fromClient, 20));
            capturedSetEncodings.set(IoUtil.readFully(fromClient, 12));
        }, failure);

        try (VNCClient client = connect(serverSocket, true)) {
        }

        Thread.sleep(100);
        then(failure.get()).isNull();

        final byte[] setPixelFormat = capturedSetPixelFormat.get();
        then(setPixelFormat[0]).isEqualTo((byte) 0);

        final byte[] setEncodings = capturedSetEncodings.get();
        then(setEncodings).containsExactly(
                2, 0, 0, 2,
                0, 0, 0, 0,
                0, 0, 0, 5);
    }

    @Test
    void request_framebuffer_update_and_read_message_round_trip_a_raw_rectangle() throws Exception {
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        final ServerSocket serverSocket = startFakeServer((fromClient, toClient) -> {
            performServerSideHandshake(fromClient, toClient, 2, 2, "desktop");
            IoUtil.readFully(fromClient, 20);
            IoUtil.readFully(fromClient, 12);

            final byte[] request = IoUtil.readFully(fromClient, 10);
            if (request[1] != 0) {
                throw new IOException("expected a non-incremental request");
            }

            toClient.write(new byte[] {0, 0, 0, 1});
            toClient.write(new byte[] {0, 0, 0, 0, 0, 2, 0, 2});
            toClient.write(new byte[] {0, 0, 0, 0});
            for (int i = 0; i < 4; i++) {
                toClient.write(new byte[] {0x00, 0x00, (byte) 0xFF, 0x00});
            }
            toClient.flush();
        }, failure);

        try (VNCClient client = connect(serverSocket, true)) {
            client.requestFramebufferUpdate(false);
            final ServerMessage message = client.readMessage();

            then(message).isInstanceOf(FramebufferUpdateMessage.class);
            final FramebufferUpdateMessage update = (FramebufferUpdateMessage) message;
            then(update.getRectangles()).hasSize(1);
            then(update.getRectangles().get(0).getPixels()).containsOnly(0xFF0000);
        }

        then(failure.get()).isNull();
    }

    @Test
    void connect_throws_when_server_closes_before_completing_the_handshake() throws Exception {
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final ServerSocket serverSocket = startFakeServer((fromClient, toClient) -> {
            toClient.write("RFB 003.0".getBytes(StandardCharsets.US_ASCII));
            toClient.flush();
        }, failure);

        thenThrownBy(() -> connect(serverSocket, true))
                .isInstanceOf(IOException.class);
    }

    @Test
    void send_key_event_reaches_the_server() throws Exception {
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final AtomicReference<byte[]> captured = new AtomicReference<>();

        final ServerSocket serverSocket = startFakeServer((fromClient, toClient) -> {
            performServerSideHandshake(fromClient, toClient, 100, 100, "desktop");
            IoUtil.readFully(fromClient, 20);
            IoUtil.readFully(fromClient, 12);
            captured.set(IoUtil.readFully(fromClient, 8));
        }, failure);

        try (VNCClient client = connect(serverSocket, true)) {
            client.sendKeyEvent(true, 0x61);
        }

        Thread.sleep(100);
        then(failure.get()).isNull();
        then(captured.get()).containsExactly(4, 1, 0, 0, 0, 0, 0, 0x61);
    }

    @Test
    void send_pointer_event_reaches_the_server() throws Exception {
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final AtomicReference<byte[]> captured = new AtomicReference<>();

        final ServerSocket serverSocket = startFakeServer((fromClient, toClient) -> {
            performServerSideHandshake(fromClient, toClient, 100, 100, "desktop");
            IoUtil.readFully(fromClient, 20);
            IoUtil.readFully(fromClient, 12);
            captured.set(IoUtil.readFully(fromClient, 6));
        }, failure);

        try (VNCClient client = connect(serverSocket, true)) {
            client.sendPointerEvent(RFBStream.POINTER_BUTTON_LEFT, 10, 20);
        }

        Thread.sleep(100);
        then(failure.get()).isNull();
        then(captured.get()).containsExactly(5, 1, 0, 10, 0, 20);
    }

    @Test
    void send_clipboard_text_reaches_the_server() throws Exception {
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final AtomicReference<String> captured = new AtomicReference<>();

        final ServerSocket serverSocket = startFakeServer((fromClient, toClient) -> {
            performServerSideHandshake(fromClient, toClient, 100, 100, "desktop");
            IoUtil.readFully(fromClient, 20);
            IoUtil.readFully(fromClient, 12);
            IoUtil.readFully(fromClient, 4);
            final long length = IoUtil.readUnsignedIntBigEndian(fromClient);
            final byte[] textBytes = IoUtil.readFully(fromClient, (int) length);
            captured.set(new String(textBytes, StandardCharsets.ISO_8859_1));
        }, failure);

        try (VNCClient client = connect(serverSocket, true)) {
            client.sendClipboardText("hello from client");
        }

        Thread.sleep(100);
        then(failure.get()).isNull();
        then(captured.get()).isEqualTo("hello from client");
    }

    @Test
    void read_message_decodes_a_server_clipboard_message() throws Exception {
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        final ServerSocket serverSocket = startFakeServer((fromClient, toClient) -> {
            performServerSideHandshake(fromClient, toClient, 100, 100, "desktop");
            IoUtil.readFully(fromClient, 20);
            IoUtil.readFully(fromClient, 12);

            final byte[] textBytes = "hello from server".getBytes(StandardCharsets.ISO_8859_1);
            toClient.write(new byte[] {3, 0, 0, 0});
            toClient.write(new byte[] {
                (byte) ((textBytes.length >> 24) & 0xFF), (byte) ((textBytes.length >> 16) & 0xFF),
                (byte) ((textBytes.length >> 8) & 0xFF), (byte) (textBytes.length & 0xFF)
            });
            toClient.write(textBytes);
            toClient.flush();
        }, failure);

        try (VNCClient client = connect(serverSocket, true)) {
            final ServerMessage message = client.readMessage();
            then(message).isInstanceOf(ServerCutTextMessage.class);
            then(((ServerCutTextMessage) message).getText()).isEqualTo("hello from server");
        }

        then(failure.get()).isNull();
    }
}
