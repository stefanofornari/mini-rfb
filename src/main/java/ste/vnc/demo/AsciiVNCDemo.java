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

package ste.vnc.demo;

import io.github.seujorgenochurras.image.ascii.AsciiParser;
import io.github.seujorgenochurras.image.ascii.ParserBuilder;
import io.github.seujorgenochurras.image.ascii.ParserConfig;
import io.github.seujorgenochurras.image.ascii.algorithm.pixel.bright.Algorithms;
import io.github.seujorgenochurras.image.ascii.algorithm.pixel.color.DefaultColorType;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import java.net.Socket;
import ste.vnc.rfb.BellMessage;
import ste.vnc.rfb.FramebufferUpdateMessage;
import ste.vnc.rfb.FramebufferUpdateRectangle;
import ste.vnc.rfb.RFBStream;
import ste.vnc.rfb.Rectangle;
import ste.vnc.rfb.ServerCutTextMessage;
import ste.vnc.rfb.ServerInit;
import ste.vnc.rfb.ServerMessage;
import ste.vnc.rfb.VNCClient;

/**
 * A minimal demo client: connects to a VNC server (security type
 * {@code None} only, per this library's current scope) and renders the
 * remote framebuffer as ASCII art in the console, refreshing on every
 * incoming update.
 *
 * <p>Usage: {@code java ste.vnc.demo.AsciiVNCDemo <host> [port] [columns] [engine]}
 * where {@code engine} is {@code image-to-ascii} (default - uses
 * io.github.seujorgenochurras:image-to-ascii) or {@code custom} (this
 * class's own luminance-ramp converter, kept as a dependency-free
 * fallback with no external dependency).
 */
public final class AsciiVNCDemo {

    // Light-to-dark luminance ramp, used only by the "custom" engine.
    private static final String RAMP = " .:-=+*#%@";

    private static final int DEFAULT_PORT = 5900;
    private static final int DEFAULT_COLUMNS = 120;

    // A real terminal that understands ANSI escapes lets us clear+redraw in
    // place (and render image-to-ascii's ANSI colour output correctly); an
    // IDE output pane (NetBeans, Eclipse, etc.) generally doesn't, so escape
    // bytes get ignored or printed as garbage. System.console() is a
    // reasonable, if imperfect, way to tell the two apart: non-null for a
    // real interactive terminal, null for most IDE consoles or redirected
    // output.
    private static final boolean ANSI_SUPPORTED = System.console() != null;
    private static final int FALLBACK_BLANK_LINES = 60;

    private AsciiVNCDemo() {
    }

    public static void main(final String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: AsciiVNCDemo <host> [port] [columns] [image-to-ascii|custom]");
            System.exit(1);
            return;
        }

        final String host = args[0];
        final int port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
        final int columns = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_COLUMNS;
        final boolean useImageToAscii = args.length <= 3 || !args[3].equalsIgnoreCase("custom");

        System.out.println("Connecting to " + host + ":" + port + " ...");
        if (!ANSI_SUPPORTED) {
            System.out.println("(no interactive terminal detected - using blank-line fallback instead of"
                    + " ANSI clear; run from a real terminal, not an IDE console, for a proper in-place"
                    + " refresh and for image-to-ascii's ANSI colour output to render correctly)");
        }

        final Path frameFile = useImageToAscii ? Files.createTempFile("vnc-frame", ".png") : null;
        if (frameFile != null) {
            frameFile.toFile().deleteOnExit();
        }

        final Socket socket = new Socket(host, port);
        final RFBStream rfb = new RFBStream(socket.getInputStream(), socket.getOutputStream());
        rfb.handshake(true);
        try (VNCClient client = new VNCClient(socket, rfb)) {
            final ServerInit serverInit = client.getServerInit();
            final int fbWidth = serverInit.getFramebufferWidth();
            final int fbHeight = serverInit.getFramebufferHeight();
            System.out.println("Connected to '" + serverInit.getName() + "' (" + fbWidth + "x" + fbHeight + ")");

            final int[] framebuffer = new int[fbWidth * fbHeight];

            // Framebuffer dimensions are fixed for the session (this library
            // doesn't yet handle the DesktopSize resize pseudo-encoding), so
            // the ascii grid size - and therefore the parser config - only
            // needs computing once.
            final int asciiWidth = Math.max(1, Math.min(columns, fbWidth));
            final int asciiHeight = Math.max(1, (int) Math.round(fbHeight * (asciiWidth / (double) fbWidth) * 0.5));
            final ParserConfig parserConfig = useImageToAscii ? buildParserConfig(asciiHeight, asciiWidth) : null;

            client.requestFramebufferUpdate(false);
            while (true) {
                final ServerMessage message = client.readMessage();

                if (message instanceof FramebufferUpdateMessage) {
                    final FramebufferUpdateMessage update = (FramebufferUpdateMessage) message;
                    for (final FramebufferUpdateRectangle rectangle : update.getRectangles()) {
                        mergeRectangle(framebuffer, fbWidth, rectangle);
                    }

                    final String frame = useImageToAscii
                            ? renderWithImageToAscii(frameFile, parserConfig, framebuffer, fbWidth, fbHeight)
                            : renderToAscii(framebuffer, fbWidth, fbHeight, columns);

                    clearScreen();
                    System.out.print(frame);
                    System.out.flush();
                    client.requestFramebufferUpdate(true);
                } else if (message instanceof BellMessage) {
                    System.out.print('\u0007');
                } else if (message instanceof ServerCutTextMessage) {
                    // not surfaced in this demo
                    continue;
                }
                // SetColourMapEntries: nothing to do, already consumed by the reader
            }
        }
    }

    private static ParserConfig buildParserConfig(final int asciiHeight, final int asciiWidth) {
        return ParserBuilder.startBuild()
                .parserAlgorithm(Algorithms.HUMAN_EYE_ALGORITHM)
                .scaled()
                    .height(asciiHeight)
                    .width(asciiWidth)
                .getScale()
                // .symbols(SYMBOLS) - real signature not yet confirmed, dropped for now
                .colorAlgorithm(DefaultColorType.ANSI)
                .build();
    }

    private static void clearScreen() {
        if (ANSI_SUPPORTED) {
            System.out.print("\u001B[H\u001B[2J");
        } else {
            for (int i = 0; i < FALLBACK_BLANK_LINES; i++) {
                System.out.println();
            }
        }
    }

    private static String renderWithImageToAscii(
            final Path frameFile, final ParserConfig parserConfig,
            final int[] framebuffer, final int fbWidth, final int fbHeight) throws IOException {
        final BufferedImage image = toBufferedImage(framebuffer, fbWidth, fbHeight);
        ImageIO.write(image, "png", frameFile.toFile());
        return AsciiParser.parse(frameFile.toString(), parserConfig);
    }

    private static BufferedImage toBufferedImage(final int[] framebuffer, final int width, final int height) {
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, width, height, framebuffer, 0, width);
        return image;
    }

    /**
     * Copies one decoded rectangle's pixels into their place in the
     * full framebuffer.
     */
    static void mergeRectangle(final int[] framebuffer, final int framebufferWidth,
            final FramebufferUpdateRectangle update) {
        final Rectangle rectangle = update.getRectangle();
        final int[] pixels = update.getPixels();
        for (int row = 0; row < rectangle.height(); row++) {
            final int srcOffset = row * rectangle.width();
            final int dstOffset = (rectangle.y() + row) * framebufferWidth + rectangle.x();
            System.arraycopy(pixels, srcOffset, framebuffer, dstOffset, rectangle.width());
        }
    }

    /**
     * The dependency-free fallback engine: downsamples the framebuffer to
     * {@code asciiColumns} wide and maps each sampled pixel's luminance
     * onto {@link #RAMP}.
     */
    static String renderToAscii(final int[] framebuffer, final int fbWidth, final int fbHeight,
            final int asciiColumns) {
        final int columns = Math.max(1, Math.min(asciiColumns, fbWidth));
        final int rows = Math.max(1, (int) Math.round(fbHeight * (columns / (double) fbWidth) * 0.5));

        final StringBuilder out = new StringBuilder();
        for (int row = 0; row < rows; row++) {
            final int srcY = Math.min(fbHeight - 1, row * fbHeight / rows);
            for (int col = 0; col < columns; col++) {
                final int srcX = Math.min(fbWidth - 1, col * fbWidth / columns);
                out.append(toAsciiChar(framebuffer[srcY * fbWidth + srcX]));
            }
            out.append('\n');
        }
        return out.toString();
    }

    private static char toAsciiChar(final int rgb) {
        final int r = (rgb >> 16) & 0xFF;
        final int g = (rgb >> 8) & 0xFF;
        final int b = rgb & 0xFF;
        final double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
        final int index = (int) Math.round(luminance * (RAMP.length() - 1));
        return RAMP.charAt(index);
    }
}
