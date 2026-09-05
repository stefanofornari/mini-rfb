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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class RFBStream {

    private static final int SECURITY_TYPE_NONE = 1;
    private static final long SECURITY_RESULT_OK = 0;

    private static final int MESSAGE_TYPE_SET_PIXEL_FORMAT = 0;
    private static final int MESSAGE_TYPE_SET_ENCODINGS = 2;
    private static final int MESSAGE_TYPE_FRAMEBUFFER_UPDATE_REQUEST = 3;
    private static final int MESSAGE_TYPE_KEY_EVENT = 4;
    private static final int MESSAGE_TYPE_POINTER_EVENT = 5;
    private static final int MESSAGE_TYPE_CLIENT_CUT_TEXT = 6;
    private static final int MESSAGE_TYPE_SET_DESKTOP_SIZE = 251;
    private static final int MESSAGE_TYPE_FENCE = 248;

    private static final int MESSAGE_TYPE_FRAMEBUFFER_UPDATE = 0;
    private static final int MESSAGE_TYPE_SET_COLOUR_MAP_ENTRIES = 1;
    private static final int MESSAGE_TYPE_BELL = 2;
    private static final int MESSAGE_TYPE_SERVER_CUT_TEXT = 3;

    private static final SetColourMapEntriesMessage SET_COLOUR_MAP_ENTRIES_MESSAGE = new SetColourMapEntriesMessage();
    private static final BellMessage BELL_MESSAGE = new BellMessage();

    public static final int POINTER_BUTTON_LEFT = 1;
    public static final int POINTER_BUTTON_MIDDLE = 1 << 1;
    public static final int POINTER_BUTTON_RIGHT = 1 << 2;
    public static final int POINTER_WHEEL_UP = 1 << 3;
    public static final int POINTER_WHEEL_DOWN = 1 << 4;

    private final InputStream in;
    private final OutputStream out;

    private Optional<ProtocolVersion> version;
    private Optional<Integer> securityType;
    private Optional<Boolean> shared;
    private Optional<ServerInit> serverInit;

    public RFBStream(final InputStream in, final OutputStream out) {
        this.in = in;
        this.out = out;
        this.version = Optional.empty();
        this.securityType = Optional.empty();
        this.shared = Optional.empty();
        this.serverInit = Optional.empty();
    }

    public void handshake(final boolean shared) throws IOException {
        versionNegotiation();
        securityNegotiation();
        shared(shared);
        readServerInit();
    }

    public void versionNegotiation() throws IOException {
        if (version.isPresent()) {
            return;
        }
        final byte[] serverLine = IoUtil.readFully(in, ProtocolVersion.WIRE_LENGTH);
        final ProtocolVersion serverVersion;
        try {
            serverVersion = ProtocolVersion.parse(serverLine);
        } catch (final IllegalArgumentException e) {
            throw new HandshakeException("server sent an invalid protocol version line", e);
        }

        if (serverVersion.getMajor() != 3) {
            throw new HandshakeException("unsupported protocol major version: " + serverVersion);
        }

        final ProtocolVersion negotiated =
                serverVersion.compareTo(ProtocolVersion.RFB_3_8) < 0 ? serverVersion : ProtocolVersion.RFB_3_8;

        out.write(negotiated.toBytes());
        out.flush();
        version = Optional.of(negotiated);
    }

    public Optional<ProtocolVersion> version() {
        return version;
    }

    public void securityNegotiation() throws IOException {
        if (securityType.isPresent()) {
            return;
        }
        final ProtocolVersion v = version.orElseThrow(() ->
                new IllegalStateException("version must be negotiated before security negotiation"));

        if (v.compareTo(ProtocolVersion.RFB_3_7) < 0) {
            securityNegotiationLegacy();
        } else {
            securityNegotiationModern();
        }
    }

    private void securityNegotiationLegacy() throws IOException {
        final long type = IoUtil.readUnsignedIntBigEndian(in);
        if (type == 0) {
            throw new HandshakeException("server refused connection: " + IoUtil.readLengthPrefixedString(in));
        }
        if (type != SECURITY_TYPE_NONE) {
            throw new HandshakeException(
                    "server selected unsupported security type " + type + "; only 'None' is supported");
        }
        securityType = Optional.of(SECURITY_TYPE_NONE);
    }

    private void securityNegotiationModern() throws IOException {
        final int count = IoUtil.readUnsignedByte(in);
        if (count == 0) {
            throw new HandshakeException("server refused connection: " + IoUtil.readLengthPrefixedString(in));
        }

        final int[] offered = new int[count];
        boolean noneOffered = false;
        for (int i = 0; i < count; i++) {
            offered[i] = IoUtil.readUnsignedByte(in);
            if (offered[i] == SECURITY_TYPE_NONE) {
                noneOffered = true;
            }
        }

        if (!noneOffered) {
            final StringBuilder offeredList = new StringBuilder();
            for (int i = 0; i < offered.length; i++) {
                if (i > 0) {
                    offeredList.append(", ");
                }
                offeredList.append(offered[i]);
            }
            throw new HandshakeException(
                    "server does not offer security type 'None'; offered: [" + offeredList + "]");
        }

        out.write(SECURITY_TYPE_NONE);
        out.flush();
        securityType = Optional.of(SECURITY_TYPE_NONE);

        if (version.get().compareTo(ProtocolVersion.RFB_3_8) >= 0) {
            securityResultRead();
        }
    }

    private void securityResultRead() throws IOException {
        final long result = IoUtil.readUnsignedIntBigEndian(in);
        if (result != SECURITY_RESULT_OK) {
            final String reason = IoUtil.readLengthPrefixedString(in);
            throw new HandshakeException("security handshake failed: " + reason);
        }
    }

    public Optional<Integer> securityType() {
        return securityType;
    }

    public void shared(final boolean shared) throws IOException {
        if (this.shared.isPresent()) {
            return;
        }
        if (!version.isPresent() || !securityType.isPresent()) {
            throw new IllegalStateException("version and security must be negotiated before ClientInit");
        }
        this.shared = Optional.of(shared);
        out.write(shared ? 1 : 0);
        out.flush();
    }

    public Optional<Boolean> shared() {
        return shared;
    }

    public void readServerInit() throws IOException {
        if (serverInit.isPresent()) {
            return;
        }
        final ServerInit init = ServerInit.read(in);
        serverInit = Optional.of(init);
    }

    public Optional<ServerInit> serverInit() {
        return serverInit;
    }

    public ServerMessage readMessage(final PixelFormat format) throws IOException {
        final int messageType = IoUtil.readUnsignedByte(in);
        switch (messageType) {
            case MESSAGE_TYPE_FRAMEBUFFER_UPDATE -> {
                return new FramebufferUpdateMessage(readFramebufferUpdate(format));
            }
            case MESSAGE_TYPE_SET_COLOUR_MAP_ENTRIES -> {
                skipSetColourMapEntries();
                return SET_COLOUR_MAP_ENTRIES_MESSAGE;
            }
            case MESSAGE_TYPE_BELL -> {
                return BELL_MESSAGE;
            }
            case MESSAGE_TYPE_SERVER_CUT_TEXT -> {
                return readServerCutText();
            }
            default -> throw new UnsupportedServerMessageException(messageType);
        }
    }

    private List<FramebufferUpdateRectangle> readFramebufferUpdate(final PixelFormat format) throws IOException {
        IoUtil.readUnsignedByte(in); // padding
        final int rectangleCount = IoUtil.readUnsignedShortBigEndian(in);

        final List<FramebufferUpdateRectangle> rectangles = new ArrayList<>(rectangleCount);
        for (int i = 0; i < rectangleCount; i++) {
            final int x = IoUtil.readUnsignedShortBigEndian(in);
            final int y = IoUtil.readUnsignedShortBigEndian(in);
            final int width = IoUtil.readUnsignedShortBigEndian(in);
            final int height = IoUtil.readUnsignedShortBigEndian(in);
            final int encodingType = IoUtil.readIntBigEndian(in);

            final Rectangle rect = new Rectangle(x, y, width, height);

            if (encodingType == EncodingType.RAW.getCode()) {
                final int[] pixels = new RawDecoder().decode(format, width, height, in);
                rectangles.add(new FramebufferUpdateRectangle(rect, pixels, FramebufferUpdateRectangle.ENCODING_RAW, 0, 0));
            } else if (encodingType == EncodingType.HEXTILE.getCode()) {
                final int[] pixels = new HextileDecoder().decode(format, width, height, in);
                rectangles.add(new FramebufferUpdateRectangle(rect, pixels, FramebufferUpdateRectangle.ENCODING_HEXTILE, 0, 0));
            } else if (encodingType == EncodingType.COPYRECT.getCode()) {
                final int srcX = IoUtil.readUnsignedShortBigEndian(in);
                final int srcY = IoUtil.readUnsignedShortBigEndian(in);
                rectangles.add(new FramebufferUpdateRectangle(rect, null, FramebufferUpdateRectangle.ENCODING_COPYRECT, srcX, srcY));
            } else {
                throw new UnsupportedEncodingTypeException(encodingType);
            }
        }
        return rectangles;
    }

    private void skipSetColourMapEntries() throws IOException {
        IoUtil.readUnsignedByte(in); // padding
        IoUtil.readUnsignedShortBigEndian(in); // first-colour
        final int numberOfColours = IoUtil.readUnsignedShortBigEndian(in);
        IoUtil.readFully(in, numberOfColours * 6); // 3 x u16 (r,g,b) per colour
    }

    private ServerCutTextMessage readServerCutText() throws IOException {
        IoUtil.readFully(in, 3); // padding
        final long length = IoUtil.readUnsignedIntBigEndian(in);
        if (length > Integer.MAX_VALUE) {
            throw new IOException("implausibly long ServerCutText length: " + length);
        }
        final byte[] textBytes = IoUtil.readFully(in, (int) length);
        return new ServerCutTextMessage(new String(textBytes, java.nio.charset.StandardCharsets.ISO_8859_1));
    }

    public InputStream inputStream() {
        return in;
    }

    public OutputStream outputStream() {
        return out;
    }

    public void setPixelFormat(final PixelFormat format) throws IOException {
        final byte[] message = new byte[4 + PixelFormat.WIRE_LENGTH];
        message[0] = MESSAGE_TYPE_SET_PIXEL_FORMAT;
        System.arraycopy(format.toBytes(), 0, message, 4, PixelFormat.WIRE_LENGTH);
        out.write(message);
        out.flush();
    }

    public void setEncodings(final EncodingType... encodings) throws IOException {
        final int count = encodings.length;
        final byte[] header = new byte[] {
            (byte) MESSAGE_TYPE_SET_ENCODINGS,
            0,
            (byte) ((count >> 8) & 0xFF),
            (byte) (count & 0xFF)
        };
        out.write(header);
        for (final EncodingType encoding : encodings) {
            final int code = encoding.getCode();
            out.write(new byte[] {
                (byte) ((code >> 24) & 0xFF),
                (byte) ((code >> 16) & 0xFF),
                (byte) ((code >> 8) & 0xFF),
                (byte) (code & 0xFF)
            });
        }
        out.flush();
    }

    public void setEncodings(final int... encodingCodes) throws IOException {
        final int count = encodingCodes.length;
        final byte[] header = new byte[] {
            (byte) MESSAGE_TYPE_SET_ENCODINGS,
            0,
            (byte) ((count >> 8) & 0xFF),
            (byte) (count & 0xFF)
        };
        out.write(header);
        for (final int code : encodingCodes) {
            out.write(new byte[] {
                (byte) ((code >> 24) & 0xFF),
                (byte) ((code >> 16) & 0xFF),
                (byte) ((code >> 8) & 0xFF),
                (byte) (code & 0xFF)
            });
        }
        out.flush();
    }

    public void requestFramebufferUpdate(final boolean incremental, final Rectangle rectangle) throws IOException {
        final byte[] message = new byte[] {
            (byte) MESSAGE_TYPE_FRAMEBUFFER_UPDATE_REQUEST,
            (byte) (incremental ? 1 : 0),
            (byte) ((rectangle.x() >> 8) & 0xFF),
            (byte) (rectangle.x() & 0xFF),
            (byte) ((rectangle.y() >> 8) & 0xFF),
            (byte) (rectangle.y() & 0xFF),
            (byte) ((rectangle.width() >> 8) & 0xFF),
            (byte) (rectangle.width() & 0xFF),
            (byte) ((rectangle.height() >> 8) & 0xFF),
            (byte) (rectangle.height() & 0xFF)
        };
        out.write(message);
        out.flush();
    }

    public void sendKeyEvent(final boolean down, final int keySym) throws IOException {
        final byte[] message = new byte[] {
            (byte) MESSAGE_TYPE_KEY_EVENT,
            (byte) (down ? 1 : 0),
            0, 0,
            (byte) ((keySym >> 24) & 0xFF),
            (byte) ((keySym >> 16) & 0xFF),
            (byte) ((keySym >> 8) & 0xFF),
            (byte) (keySym & 0xFF)
        };
        out.write(message);
        out.flush();
    }

    public void sendPointerEvent(final int buttonMask, final int x, final int y) throws IOException {
        if (buttonMask < 0 || buttonMask > 0xFF) {
            throw new IllegalArgumentException("buttonMask out of range: " + buttonMask);
        }
        if (x < 0 || x > 0xFFFF) {
            throw new IllegalArgumentException("x out of range: " + x);
        }
        if (y < 0 || y > 0xFFFF) {
            throw new IllegalArgumentException("y out of range: " + y);
        }

        final byte[] message = new byte[] {
            (byte) MESSAGE_TYPE_POINTER_EVENT,
            (byte) buttonMask,
            (byte) ((x >> 8) & 0xFF),
            (byte) (x & 0xFF),
            (byte) ((y >> 8) & 0xFF),
            (byte) (y & 0xFF)
        };
        out.write(message);
        out.flush();
    }

    public void sendClipboardText(final String text) throws IOException {
        final byte[] textBytes = text.getBytes(StandardCharsets.ISO_8859_1);
        final byte[] header = new byte[] {
            (byte) MESSAGE_TYPE_CLIENT_CUT_TEXT,
            0, 0, 0,
            (byte) ((textBytes.length >> 24) & 0xFF),
            (byte) ((textBytes.length >> 16) & 0xFF),
            (byte) ((textBytes.length >> 8) & 0xFF),
            (byte) (textBytes.length & 0xFF)
        };
        out.write(header);
        out.write(textBytes);
        out.flush();
    }

    public void setDesktopSize(final int width, final int height, final ScreenSet layout) throws IOException {
        final byte[] header = new byte[] {
            (byte) MESSAGE_TYPE_SET_DESKTOP_SIZE,
            0,
            (byte) ((width >> 8) & 0xFF),
            (byte) (width & 0xFF),
            (byte) ((height >> 8) & 0xFF),
            (byte) (height & 0xFF),
            (byte) ((layout.size() >> 8) & 0xFF),
            (byte) (layout.size() & 0xFF)
        };
        out.write(header);
        for (final Screen screen : layout.screens()) {
            final Rectangle dim = screen.dimensions;
            final int screenWidth = dim.width();
            final int screenHeight = dim.height();
            final byte[] screenData = new byte[] {
                (byte) ((screen.id >> 24) & 0xFF),
                (byte) ((screen.id >> 16) & 0xFF),
                (byte) ((screen.id >> 8) & 0xFF),
                (byte) (screen.id & 0xFF),
                (byte) ((dim.x() >> 8) & 0xFF),
                (byte) (dim.x() & 0xFF),
                (byte) ((dim.y() >> 8) & 0xFF),
                (byte) (dim.y() & 0xFF),
                (byte) ((screenWidth >> 8) & 0xFF),
                (byte) (screenWidth & 0xFF),
                (byte) ((screenHeight >> 8) & 0xFF),
                (byte) (screenHeight & 0xFF),
                (byte) ((screen.flags >> 24) & 0xFF),
                (byte) ((screen.flags >> 16) & 0xFF),
                (byte) ((screen.flags >> 8) & 0xFF),
                (byte) (screen.flags & 0xFF)
            };
            out.write(screenData);
        }
        out.flush();
    }

    public void sendFence(final int flags, final int len, final byte[] data) throws IOException {
        final byte[] header = new byte[] {
            (byte) MESSAGE_TYPE_FENCE,
            0,
            (byte) ((flags >> 8) & 0xFF),
            (byte) (flags & 0xFF),
            (byte) ((len >> 8) & 0xFF),
            (byte) (len & 0xFF)
        };
        out.write(header);
        if (data != null && data.length > 0) {
            out.write(data, 0, Math.min(len, data.length));
        }
        out.flush();
    }
}
