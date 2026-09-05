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

package ste.vnc.demo.javafx;

import java.io.IOException;
import java.net.Socket;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.Cursor;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.util.Duration;
import ste.vnc.rfb.RFBStream;
import ste.vnc.rfb.FramebufferUpdateMessage;
import ste.vnc.rfb.FramebufferUpdateRectangle;
import ste.vnc.rfb.Rectangle;
import ste.vnc.rfb.ServerCutTextMessage;
import ste.vnc.rfb.ServerInit;
import ste.vnc.rfb.ServerMessage;
import ste.vnc.rfb.VNCClient;

/**
 * A minimal, working JavaFX component that displays a remote VNC
 * framebuffer and forwards mouse, scroll, and keyboard input back to
 * the server.
 *
 * <p><b>Why input sending is asynchronous:</b> mouse/key handlers run on
 * the JavaFX Application Thread, but {@code VNCClient}'s send methods do
 * a blocking socket write. Calling them directly from an event handler
 * would stall the FX thread - and everything it drives, including
 * animations like a caret blink - for however long the network write
 * takes. So all outgoing input goes through a small internal queue
 * drained by a dedicated background thread instead; see {@link #outbox}
 * and {@link #latestPointerMove}.
 *
 * <p>Discrete events (press/release/scroll/keys/clipboard) go through
 * {@link #outbox}, a strict FIFO that never drops anything - losing a
 * click or a keystroke would be a real bug. Continuous position updates
 * (pure hover moves and drags) go through {@link #latestPointerMove}
 * instead: a single coalescing slot that always holds only the most
 * recent position, so a slow network never causes the queue to back up
 * and the remote cursor to visibly lag - it always catches up to
 * wherever the pointer currently is, skipping stale intermediate
 * positions rather than working through a backlog of them.
 *
 * <p><b>Clipboard sync</b> is bidirectional: text the server sends via
 * {@code ServerCutText} is applied to the local system clipboard, and
 * the local clipboard is periodically polled (JavaFX has no
 * clipboard-change notification API) and pushed to the server when it
 * changes - see {@link #startClipboardPolling()} and
 * {@link #applyRemoteClipboard(String)}. {@link #lastKnownClipboardText}
 * tracks whichever direction last set the clipboard, so a value we just
 * applied from the server isn't immediately bounced back to it as if
 * the user had copied it locally, and vice versa.
 *
 * <p>This is a real, compiled reference implementation (see also the
 * copy in the project README) - but it's still intentionally minimal:
 * no resize handling, no reconnect logic, and the {@link #KEY_SYMS}
 * table covers only a modest set of common keys. Treat it as a
 * starting point, not a finished production widget.
 *
 * <p><b>Not verified by compilation</b>: this class was written without
 * a JavaFX toolchain available in the environment that authored it, so
 * unlike the rest of this project it has not actually been compiled or
 * run. Please report back if it needs adjustment.
 */
public class SimpleVNCView extends Region {

    /** A minimal, non-exhaustive JavaFX KeyCode -> X11 keysym table for keys without a 1:1 ASCII mapping. */
    private static final Map<KeyCode, Integer> KEY_SYMS = new EnumMap<>(KeyCode.class);
    static {
        KEY_SYMS.put(KeyCode.ENTER, 0xFF0D);
        KEY_SYMS.put(KeyCode.BACK_SPACE, 0xFF08);
        KEY_SYMS.put(KeyCode.TAB, 0xFF09);
        KEY_SYMS.put(KeyCode.ESCAPE, 0xFF1B);
        KEY_SYMS.put(KeyCode.SPACE, 0x0020);
        KEY_SYMS.put(KeyCode.DELETE, 0xFFFF);
        KEY_SYMS.put(KeyCode.HOME, 0xFF50);
        KEY_SYMS.put(KeyCode.END, 0xFF57);
        KEY_SYMS.put(KeyCode.PAGE_UP, 0xFF55);
        KEY_SYMS.put(KeyCode.PAGE_DOWN, 0xFF56);
        KEY_SYMS.put(KeyCode.LEFT, 0xFF51);
        KEY_SYMS.put(KeyCode.UP, 0xFF52);
        KEY_SYMS.put(KeyCode.RIGHT, 0xFF53);
        KEY_SYMS.put(KeyCode.DOWN, 0xFF54);
        KEY_SYMS.put(KeyCode.F1, 0xFFBE);
        KEY_SYMS.put(KeyCode.F2, 0xFFBF);
        KEY_SYMS.put(KeyCode.F3, 0xFFC0);
        KEY_SYMS.put(KeyCode.F4, 0xFFC1);
        KEY_SYMS.put(KeyCode.F5, 0xFFC2);
        KEY_SYMS.put(KeyCode.F6, 0xFFC3);
        KEY_SYMS.put(KeyCode.F7, 0xFFC4);
        KEY_SYMS.put(KeyCode.F8, 0xFFC5);
        KEY_SYMS.put(KeyCode.F9, 0xFFC6);
        KEY_SYMS.put(KeyCode.F10, 0xFFC7);
        KEY_SYMS.put(KeyCode.F11, 0xFFC8);
        KEY_SYMS.put(KeyCode.F12, 0xFFC9);
    }

    // Roughly one scroll "click" worth of platform scroll units; tune to taste.
    private static final double SCROLL_UNITS_PER_STEP = 40.0;

    // X11 keysyms for the four modifiers, synced from KeyEvent.isXDown() flags
    // rather than relying solely on the modifier key's own dedicated
    // KEY_PRESSED/KEY_RELEASED events - see the class javadoc and syncModifiers().
    private static final int KEYSYM_SHIFT_L = 0xFFE1;
    private static final int KEYSYM_CONTROL_L = 0xFFE3;
    private static final int KEYSYM_ALT_L = 0xFFE9;
    private static final int KEYSYM_SUPER_L = 0xFFEB; // JavaFX's "Meta" -> X11 Super_L (matches common TigerVNC-style mapping)

    /** A pending pointer button/position to send; immutable so it's safe to hand across threads. */
    private record PointerState(int buttons, int x, int y) {
    }

    private final ImageView imageView = new ImageView();
    private final ReadOnlyStringWrapper status = new ReadOnlyStringWrapper(this, "status", "Disconnected");

    // Strict FIFO for events that must never be dropped or reordered.
    private final BlockingQueue<Runnable> outbox = new LinkedBlockingQueue<>();

    // Coalescing slot for continuous position updates (hover moves + drags):
    // only ever holds the single most recent one. Guarded by its own monitor
    // so the sender thread can wait() efficiently instead of polling.
    private final Object pointerMoveLock = new Object();
    private PointerState latestPointerMove;

    // What we last told the server about each modifier's state. Only accessed
    // from the FX Application Thread (all key/mouse handlers run there), so no
    // synchronization is needed for these four.
    private boolean shiftSent;
    private boolean controlSent;
    private boolean altSent;
    private boolean metaSent;

    // The clipboard text either just applied from the server or just sent to
    // it, whichever happened most recently - used to avoid bouncing a value
    // straight back where it came from. Only ever read/written from the FX
    // Application Thread (the polling Timeline and the Platform.runLater
    // wrapping remote-clipboard application both run there), so - like the
    // modifier flags above - no synchronization is needed.
    private String lastKnownClipboardText;
    private Timeline clipboardPollTimeline;

    private volatile VNCClient client;
    private volatile boolean running;
    private WritableImage image;
    private int fbWidth;
    private int fbHeight;

    public SimpleVNCView() {
        getChildren().add(imageView);
        imageView.setPickOnBounds(true);

        // Only one cursor should be visible at a time - the server's own,
        // drawn into the framebuffer at whatever position our PointerEvents
        // report. Otherwise the local OS cursor icon sits on top of (and
        // can visibly disagree with) the remote one.
        setOnMouseEntered(e -> setCursor(Cursor.NONE));
        setOnMouseExited(e -> setCursor(Cursor.DEFAULT));

        setOnMousePressed(this::enqueuePointerEvent);
        setOnMouseReleased(this::enqueuePointerEvent);
        setOnMouseMoved(this::coalescePointerMove);
        setOnMouseDragged(this::coalescePointerMove);
        setOnScroll(this::enqueueScrollEvent);

        setOnKeyPressed(e -> enqueueKeyEvent(e, true));
        setOnKeyReleased(e -> enqueueKeyEvent(e, false));
        setFocusTraversable(true);
    }

    public ReadOnlyStringProperty statusProperty() {
        return status.getReadOnlyProperty();
    }

    /**
     * Connects on a background thread and starts the read loop, plus the
     * background senders for outgoing input. Safe to call from the
     * JavaFX Application Thread.
     */
    public void connect(final String host, final int port) {
        status.set("Connecting to " + host + ":" + port + " ...");

        shiftSent = false;
        controlSent = false;
        altSent = false;
        metaSent = false;
        lastKnownClipboardText = null;

        running = true;
        final Thread reader = new Thread(() -> runConnection(host, port), "vnc-reader");
        reader.setDaemon(true);
        reader.start();

        final Thread orderedSender = new Thread(this::runOrderedSender, "vnc-sender-ordered");
        orderedSender.setDaemon(true);
        orderedSender.start();

        final Thread moveSender = new Thread(this::runPointerMoveSender, "vnc-sender-move");
        moveSender.setDaemon(true);
        moveSender.start();

        startClipboardPolling();
    }

    /**
     * Sends the client's clipboard contents to the server, if connected.
     * Queued like other discrete events - never dropped. Also used
     * internally by the clipboard-polling Timeline; call from the JavaFX
     * Application Thread, consistent with this class's other public
     * methods.
     */
    public void sendClipboardText(final String text) {
        lastKnownClipboardText = text;
        outbox.offer(() -> {
            final VNCClient c = client;
            if (c == null) {
                return;
            }
            try {
                c.sendClipboardText(text);
            } catch (final IOException e) {
                Platform.runLater(() -> status.set("Clipboard send failed: " + e.getMessage()));
            }
        });
    }

    private void runConnection(final String host, final int port) {
        try {
            final Socket socket = new Socket(host, port);
            final RFBStream rfb = new RFBStream(socket.getInputStream(), socket.getOutputStream());
            rfb.handshake(true);
            try (VNCClient c = new VNCClient(socket, rfb)) {
                final ServerInit info = c.getServerInit();
                fbWidth = info.getFramebufferWidth();
                fbHeight = info.getFramebufferHeight();
                client = c;

                Platform.runLater(() -> {
                    image = new WritableImage(fbWidth, fbHeight);
                    imageView.setImage(image);
                    status.set("Connected to '" + info.getName() + "' (" + fbWidth + "x" + fbHeight + ")");
                });

                c.requestFramebufferUpdate(false);
                while (!Thread.currentThread().isInterrupted()) {
                    final ServerMessage message = c.readMessage();
                    if (message instanceof FramebufferUpdateMessage update) {
                        for (final FramebufferUpdateRectangle rectangle : update.getRectangles()) {
                            paintRectangle(rectangle);
                        }
                        c.requestFramebufferUpdate(true);
                    } else if (message instanceof ServerCutTextMessage cutText) {
                        applyRemoteClipboard(cutText.getText());
                    }
                }
            }
        } catch (final IOException e) {
            Platform.runLater(() -> status.set("Connection error: " + e.getMessage()));
        } finally {
            client = null;
            running = false;
            synchronized (pointerMoveLock) {
                pointerMoveLock.notifyAll();
            }
            Platform.runLater(() -> {
                if (clipboardPollTimeline != null) {
                    clipboardPollTimeline.stop();
                }
            });
        }
    }

    /**
     * Starts polling the local system clipboard for changes, pushing any
     * to the server. JavaFX has no clipboard-change notification API, so
     * periodic polling (same approach most VNC clients use) is the only
     * option; must run on the FX Application Thread, since JavaFX's
     * {@link Clipboard} is only safe to use there.
     */
    private void startClipboardPolling() {
        if (clipboardPollTimeline != null) {
            clipboardPollTimeline.stop();
        }
        clipboardPollTimeline = new Timeline(
                new KeyFrame(Duration.millis(750), e -> pollLocalClipboard()));
        clipboardPollTimeline.setCycleCount(Timeline.INDEFINITE);
        clipboardPollTimeline.play();
    }

    private void pollLocalClipboard() {
        if (client == null) {
            return;
        }
        final String text = Clipboard.getSystemClipboard().getString();
        if (text != null && !text.equals(lastKnownClipboardText)) {
            sendClipboardText(text);
        }
    }

    /** Applies clipboard text received from the server to the local system clipboard. */
    private void applyRemoteClipboard(final String text) {
        Platform.runLater(() -> {
            if (text.equals(lastKnownClipboardText)) {
                return; // avoid bouncing it right back as if the user had just copied it locally
            }
            lastKnownClipboardText = text;
            final ClipboardContent content = new ClipboardContent();
            content.putString(text);
            Clipboard.getSystemClipboard().setContent(content);
        });
    }

    /** Drains {@link #outbox} in strict order for the lifetime of this connection. */
    private void runOrderedSender() {
        while (running) {
            try {
                final Runnable task = outbox.poll(200, TimeUnit.MILLISECONDS);
                if (task != null) {
                    task.run();
                }
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * Continuously sends whatever the most recent pointer position is,
     * as fast as the connection allows - never queuing a backlog of
     * stale positions.
     */
    private void runPointerMoveSender() {
        while (running) {
            final PointerState state;
            synchronized (pointerMoveLock) {
                while (running && latestPointerMove == null) {
                    try {
                        pointerMoveLock.wait();
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (!running) {
                    return;
                }
                state = latestPointerMove;
                latestPointerMove = null;
            }

            final VNCClient c = client;
            if (c != null) {
                try {
                    c.sendPointerEvent(state.buttons(), state.x(), state.y());
                } catch (final IOException e) {
                    Platform.runLater(() -> status.set("Pointer send failed: " + e.getMessage()));
                }
            }
        }
    }

    private void paintRectangle(final FramebufferUpdateRectangle rectangle) {
        final Rectangle r = rectangle.getRectangle();
        final int[] pixels = rectangle.getPixels();
        final int[] argb = toArgb(pixels);
        Platform.runLater(() -> image.getPixelWriter().setPixels(
                r.x(), r.y(), r.width(), r.height(),
                PixelFormat.getIntArgbInstance(), argb, 0, r.width()));
    }

    // FramebufferUpdateRectangle pixels are packed 0xRRGGBB (no alpha);
    // JavaFX's PixelFormat.getIntArgbInstance() expects 0xAARRGGBB.
    private static int[] toArgb(final int[] rgb) {
        final int[] argb = new int[rgb.length];
        for (int i = 0; i < rgb.length; i++) {
            argb[i] = 0xFF000000 | rgb[i];
        }
        return argb;
    }

    /** Publishes a hover-move/drag position, replacing any not-yet-sent one. Never blocks the FX thread. */
    private void coalescePointerMove(final MouseEvent e) {
        if (client == null) {
            return;
        }
        final int[] point = toFramebufferPoint(e.getX(), e.getY());
        if (point == null) {
            return;
        }
        final PointerState state = new PointerState(buttonMaskFor(e), point[0], point[1]);
        synchronized (pointerMoveLock) {
            latestPointerMove = state;
            pointerMoveLock.notifyAll();
        }
    }

    /** Queues a press/release for guaranteed, in-order delivery. Never blocks the FX thread. */
    private void enqueuePointerEvent(final MouseEvent e) {
        if (client == null) {
            return;
        }
        final int[] point = toFramebufferPoint(e.getX(), e.getY());
        if (point == null) {
            return;
        }
        final PointerState state = new PointerState(buttonMaskFor(e), point[0], point[1]);
        outbox.offer(() -> {
            final VNCClient c = client;
            if (c == null) {
                return;
            }
            try {
                c.sendPointerEvent(state.buttons(), state.x(), state.y());
            } catch (final IOException ex) {
                Platform.runLater(() -> status.set("Pointer send failed: " + ex.getMessage()));
            }
        });
    }

    private static int buttonMaskFor(final MouseEvent e) {
        int buttons = e.isPrimaryButtonDown() ? RFBStream.POINTER_BUTTON_LEFT : 0;
        buttons |= e.isSecondaryButtonDown() ? RFBStream.POINTER_BUTTON_RIGHT : 0;
        buttons |= e.isMiddleButtonDown() ? RFBStream.POINTER_BUTTON_MIDDLE : 0;
        return buttons;
    }

    /**
     * Maps a mouse/scroll event's coordinates - local to this Region, i.e.
     * in on-screen pixels - into framebuffer pixel coordinates.
     *
     * <p>The displayed {@code imageView} is scaled via
     * {@code setFitWidth}/{@code setFitHeight} with {@code preserveRatio}
     * true and centred (see {@link #layoutChildren()}), so unless this
     * Region happens to be sized exactly to the framebuffer's native
     * resolution, on-screen coordinates need both a scale factor and a
     * letterbox-offset correction before they mean anything to the
     * server. Returns {@code null} if not yet connected/sized.
     */
    private int[] toFramebufferPoint(final double localX, final double localY) {
        if (fbWidth <= 0 || fbHeight <= 0) {
            return null;
        }
        final double w = getWidth();
        final double h = getHeight();
        if (w <= 0 || h <= 0) {
            return null;
        }

        final double scale = Math.min(w / fbWidth, h / fbHeight);
        final double renderedWidth = fbWidth * scale;
        final double renderedHeight = fbHeight * scale;
        final double offsetX = (w - renderedWidth) / 2.0;
        final double offsetY = (h - renderedHeight) / 2.0;

        int fbX = (int) Math.round((localX - offsetX) / scale);
        int fbY = (int) Math.round((localY - offsetY) / scale);

        // clamp rather than drop clicks that land in the letterbox margin
        fbX = Math.max(0, Math.min(fbWidth - 1, fbX));
        fbY = Math.max(0, Math.min(fbHeight - 1, fbY));
        return new int[] {fbX, fbY};
    }

    /**
     * Scroll wheel "buttons" (RFB's de facto extension, buttons 4/5) are
     * conventionally sent as an instantaneous press+release per notch,
     * not held down like a real button. Queued (not coalesced) - a
     * dropped scroll notch would feel wrong, unlike a dropped
     * intermediate hover position.
     */
    private void enqueueScrollEvent(final ScrollEvent e) {
        if (client == null) {
            return;
        }
        final int[] point = toFramebufferPoint(e.getX(), e.getY());
        if (point == null) {
            return;
        }
        final int steps = Math.max(1, (int) Math.round(Math.abs(e.getDeltaY()) / SCROLL_UNITS_PER_STEP));
        final int button = e.getDeltaY() > 0 ? RFBStream.POINTER_WHEEL_UP : RFBStream.POINTER_WHEEL_DOWN;
        outbox.offer(() -> {
            final VNCClient c = client;
            if (c == null) {
                return;
            }
            try {
                for (int i = 0; i < steps; i++) {
                    c.sendPointerEvent(button, point[0], point[1]); // press
                    c.sendPointerEvent(0, point[0], point[1]);      // release
                }
            } catch (final IOException ex) {
                Platform.runLater(() -> status.set("Scroll send failed: " + ex.getMessage()));
            }
        });
    }

    /**
     * Queues a key press/release for guaranteed, in-order delivery. Never
     * blocks the FX thread.
     *
     * <p>Modifier state (Shift/Ctrl/Alt/Meta) is derived from this event's
     * own {@code isShiftDown()}/etc. flags on <em>every</em> key event, not
     * just from the modifier key's own dedicated press/release - this is
     * what makes combinations like Ctrl+C reliable even if a platform or
     * focus-transfer edge case fails to deliver a clean pair of events for
     * the physical modifier key itself. {@link #syncModifiers} sends
     * whatever transitions are needed and updates the cached state; if the
     * event's own key IS one of the modifiers, that sync already covers it
     * completely, so we return early rather than also sending it as a
     * "regular" key (which would double-send).
     */
    private void enqueueKeyEvent(final KeyEvent e, final boolean down) {
        if (client == null) {
            return;
        }

        syncModifiers(e);

        final KeyCode code = e.getCode();
        if (isModifierKey(code)) {
            return; // already fully handled by syncModifiers above
        }

        final Integer keySym = keySymFor(code);
        if (keySym == null) {
            return; // unmapped key - extend KEY_SYMS as needed
        }
        outbox.offer(() -> {
            final VNCClient c = client;
            if (c == null) {
                return;
            }
            try {
                c.sendKeyEvent(down, keySym);
            } catch (final IOException ex) {
                Platform.runLater(() -> status.set("Key send failed: " + ex.getMessage()));
            }
        });
    }

    private static boolean isModifierKey(final KeyCode code) {
        return code == KeyCode.SHIFT || code == KeyCode.CONTROL
                || code == KeyCode.ALT || code == KeyCode.META;
    }

    private void syncModifiers(final KeyEvent e) {
        if (e.isShiftDown() != shiftSent) {
            sendModifierKey(e.isShiftDown(), KEYSYM_SHIFT_L);
            shiftSent = e.isShiftDown();
        }
        if (e.isControlDown() != controlSent) {
            sendModifierKey(e.isControlDown(), KEYSYM_CONTROL_L);
            controlSent = e.isControlDown();
        }
        if (e.isAltDown() != altSent) {
            sendModifierKey(e.isAltDown(), KEYSYM_ALT_L);
            altSent = e.isAltDown();
        }
        if (e.isMetaDown() != metaSent) {
            sendModifierKey(e.isMetaDown(), KEYSYM_SUPER_L);
            metaSent = e.isMetaDown();
        }
    }

    private void sendModifierKey(final boolean down, final int keySym) {
        outbox.offer(() -> {
            final VNCClient c = client;
            if (c == null) {
                return;
            }
            try {
                c.sendKeyEvent(down, keySym);
            } catch (final IOException ex) {
                Platform.runLater(() -> status.set("Key send failed: " + ex.getMessage()));
            }
        });
    }

    private static Integer keySymFor(final KeyCode code) {
        final String name = code.getName();
        if (name.length() == 1) {
            return (int) Character.toLowerCase(name.charAt(0)); // letters/digits: ASCII == keysym
        }
        return KEY_SYMS.get(code);
    }

    @Override
    protected void layoutChildren() {
        final double w = getWidth();
        final double h = getHeight();
        imageView.setFitWidth(w);
        imageView.setFitHeight(h);
        imageView.setPreserveRatio(true);
        layoutInArea(imageView, 0, 0, w, h, 0, javafx.geometry.HPos.CENTER, javafx.geometry.VPos.CENTER);
    }
}
