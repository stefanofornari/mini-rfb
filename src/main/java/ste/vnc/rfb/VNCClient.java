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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class VNCClient implements Closeable {

    private final Closeable transport;
    private final RFBStream rfb;
    private final PixelFormat pixelFormat;

    public VNCClient(final Closeable transport, final RFBStream rfb) throws IOException {
        this.transport = transport;
        this.rfb = rfb;
        this.pixelFormat = PixelFormat.rgb888();
        rfb.setPixelFormat(pixelFormat);
        rfb.setEncodings(EncodingType.RAW, EncodingType.HEXTILE);
    }

    public InputStream getInputStream() {
        return rfb.inputStream();
    }

    public OutputStream getOutputStream() {
        return rfb.outputStream();
    }

    public ServerInit getServerInit() {
        return rfb.serverInit().orElseThrow();
    }

    public PixelFormat getPixelFormat() {
        return pixelFormat;
    }

    public void requestFramebufferUpdate(final boolean incremental, final Rectangle rectangle) throws IOException {
        rfb.requestFramebufferUpdate(incremental, rectangle);
    }

    public void requestFramebufferUpdate(final boolean incremental) throws IOException {
        requestFramebufferUpdate(
                incremental,
                new Rectangle(0, 0, getServerInit().getFramebufferWidth(), getServerInit().getFramebufferHeight()));
    }

    public void sendKeyEvent(final boolean down, final int keySym) throws IOException {
        rfb.sendKeyEvent(down, keySym);
    }

    public void sendPointerEvent(final int buttonMask, final int x, final int y) throws IOException {
        rfb.sendPointerEvent(buttonMask, x, y);
    }

    public void sendClipboardText(final String text) throws IOException {
        rfb.sendClipboardText(text);
    }

    public ServerMessage readMessage() throws IOException {
        return rfb.readMessage(pixelFormat);
    }

    @Override
    public void close() throws IOException {
        transport.close();
    }
}
