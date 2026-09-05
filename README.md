# Mini RFB

A client-side Java implementation of the RFB/VNC protocol ([RFC 6143](https://datatracker.ietf.org/doc/html/rfc6143)) — the wire protocol, not a UI. It connects to a VNC server, performs the handshake, decodes framebuffer updates into plain pixel arrays, and lets you send input events and clipboard text back. What you do with the pixels (paint them, convert them to ASCII, whatever) is up to the caller.

This module is UI-agnostic on purpose: it doesn't depend on JavaFX, Swing, or anything else. A JavaFX integration example is included further down.

## Current scope

This is a deliberately staged implementation, not a full-featured VNC client yet:

| Area | Status |
|---|---|
| Handshake | Protocol versions 3.3, 3.7, 3.8 |
| Security | **Type `None` only** — no VNC password auth, no VeNCrypt/TLS |
| Pixel format | Always requests 24-bit true-colour (`PixelFormat.rgb888()`) |
| Encodings | Raw and Hextile only (no Tight, ZRLE, CopyRect, etc.) |
| Framebuffer resize | Not handled (`DesktopSize` pseudo-encoding is not decoded) |
| Input events | KeyEvent, PointerEvent |
| Clipboard | Both directions (`ClientCutText` / `ServerCutText`) |

In practice: this connects fine to any server configured with **no password**. If your server requires authentication, this library can't connect to it yet.

## Requirements

- Java 17+
- Maven

## Building

```
mvn test              # run the test suite
mvn package           # build target/vnc-protocol-<version>.jar
```

`mvn package` also produces an executable fat jar (via `maven-shade-plugin`) at `target/vnc-protocol-<version>-all.jar`, with the bundled ASCII demo (see below) as its entry point:

```
java -jar target/vnc-protocol-<version>-all.jar <host> [port] [columns] [engine]
```

## Quick start

Everything starts with `VNCClient.connect(...)`, which opens the socket and runs the full handshake (version negotiation, security, `ClientInit`/`ServerInit`, and sends `SetPixelFormat`/`SetEncodings`) before returning:

```java
try (VNCClient client = VNCClient.connect("192.168.1.50", 5900)) {
    ServerInit info = client.getServerInit();
    System.out.println(info.getName() + " " + info.getFramebufferWidth() + "x" + info.getFramebufferHeight());

    // ask for the whole screen once, then switch to incremental updates
    client.requestFramebufferUpdate(false);

    while (true) {
        ServerMessage message = client.readMessage(); // blocks until one arrives

        if (message instanceof FramebufferUpdateMessage update) {
            for (FramebufferUpdateRectangle rect : update.getRectangles()) {
                paint(rect); // your code — see below
            }
            client.requestFramebufferUpdate(true); // ask for the next incremental update
        }
    }
}
```

`VNCClient` doesn't own a read loop or a thread — you call `readMessage()` yourself, in a loop, from whatever thread makes sense for your application. This is deliberate: it lets a UI toolkit marshal incoming updates onto its own event thread instead of this library dictating a threading model.

### Decoding a rectangle

A `FramebufferUpdateRectangle` gives you a `Rectangle` (position/size within the framebuffer) and a row-major `int[]` of packed `0xRRGGBB` pixels:

```java
void paint(FramebufferUpdateRectangle rect) {
    Rectangle r = rect.getRectangle();
    int[] pixels = rect.getPixels(); // r.getWidth() * r.getHeight() entries, row-major

    for (int y = 0; y < r.getHeight(); y++) {
        for (int x = 0; x < r.getWidth(); x++) {
            int rgb = pixels[y * r.getWidth() + x];
            // draw rgb at (r.getX() + x, r.getY() + y) on your own canvas/framebuffer
        }
    }
}
```

Most callers keep their own full-framebuffer `int[]` sized to `ServerInit.getFramebufferWidth() * getFramebufferHeight()` and copy each incoming rectangle into place — see `mergeRectangle` in `AsciiVNCDemo` for a working example.

### Other server messages

`readMessage()` returns one of:

- `FramebufferUpdateMessage` — one or more decoded rectangles (see above)
- `BellMessage` — no data, just a signal
- `ServerCutTextMessage` — the server's clipboard contents (`getText()`)
- `SetColourMapEntriesMessage` — palette data; parsed and correctly skipped since this client is always true-colour, but exposed as a marker in case you want to know it happened

### Sending input

Keyboard and pointer events are RFB-level, not toolkit-level — this library takes raw X11 keysyms for keys and RFB button-mask bits for the mouse, and leaves any UI-toolkit key-code mapping to the caller (see the JavaFX example below for what that mapping looks like in practice):

```java
// mouse
client.sendPointerEvent(ClientMessages.POINTER_BUTTON_LEFT, x, y); // button(s) down
client.sendPointerEvent(0, x, y);                                  // all buttons up

// keyboard — 0x61 is the X11 keysym for 'a'
client.sendKeyEvent(true, 0x61);  // key down
client.sendKeyEvent(false, 0x61); // key up
```

Button mask bits (`ClientMessages`): `POINTER_BUTTON_LEFT`, `POINTER_BUTTON_MIDDLE`, `POINTER_BUTTON_RIGHT`, `POINTER_WHEEL_UP`, `POINTER_WHEEL_DOWN` — OR them together for multiple buttons held at once.

### Clipboard

```java
client.sendClipboardText("copied on the client");        // client -> server

ServerMessage message = client.readMessage();
if (message instanceof ServerCutTextMessage cutText) {    // server -> client
    String serverClipboard = cutText.getText();
}
```

### Error handling

Protocol-level failures are surfaced as checked exceptions, all extending `IOException` so they compose naturally with the I/O calls around them:

- `HandshakeException` — version negotiation failed, no acceptable security type was offered, or the server reported a security/auth failure
- `UnsupportedEncodingTypeException` — a `FramebufferUpdate` rectangle arrived in an encoding other than Raw/Hextile (shouldn't happen against a well-behaved server, since only those two are advertised via `SetEncodings`)
- `UnsupportedServerMessageException` — an unrecognized server-to-client message type byte, generally indicating a non-conformant server or a desynchronized stream

## The bundled ASCII demo

`ste.vnc.demo.AsciiVNCDemo` is a console client that renders the remote framebuffer as ASCII art, refreshing on every update — mostly useful as a working end-to-end example and a quick way to sanity-check a connection without any UI code.

```
java -jar target/vnc-protocol-<version>-all.jar <host> [port=5900] [columns=120] [image-to-ascii|custom]
```

It uses [`io.github.seujorgenochurras:image-to-ascii`](https://github.com/Furlanetto-Dev/image-to-ascii) by default (richer rendering, ANSI colour), with a small dependency-free luminance-ramp converter (`custom`) as a fallback. It also auto-detects whether it's running in a real ANSI-capable terminal vs. an IDE output pane, and adjusts how it clears the screen between frames accordingly.

## Integrating into a JavaFX application

The library has no JavaFX dependency, so wiring it up is just: run `VNCClient` on a background thread, and marshal pixel updates onto the JavaFX Application Thread with `Platform.runLater`. The essential pattern (imports omitted for brevity):

```java
public class SimpleVNCView extends Region {

    private final ImageView imageView = new ImageView();
    private volatile VNCClient client;
    private WritableImage image;

    public void connect(String host, int port) {
        Thread reader = new Thread(() -> {
            try (VNCClient c = VNCClient.connect(host, port)) {
                client = c;
                ServerInit info = c.getServerInit();

                Platform.runLater(() -> {
                    image = new WritableImage(info.getFramebufferWidth(), info.getFramebufferHeight());
                    imageView.setImage(image);
                });

                c.requestFramebufferUpdate(false);
                while (!Thread.currentThread().isInterrupted()) {
                    ServerMessage message = c.readMessage();
                    if (message instanceof FramebufferUpdateMessage update) {
                        for (FramebufferUpdateRectangle rect : update.getRectangles()) {
                            paintRectangle(rect); // copy pixels into `image` via Platform.runLater
                        }
                        c.requestFramebufferUpdate(true);
                    }
                }
            } catch (IOException e) {
                // surface connection errors to your UI as appropriate
            } finally {
                client = null;
            }
        }, "vnc-reader");
        reader.setDaemon(true);
        reader.start();
    }

    // ... paintRectangle, mouse/scroll/keyboard forwarding - see below for the full version
}
```

The one fiddly bit is the pixel format: `FramebufferUpdateRectangle` pixels are packed `0xRRGGBB` with no alpha channel, while JavaFX's `WritableImage`/`PixelWriter` wants `0xAARRGGBB` via `PixelFormat.getIntArgbInstance()` — so each rectangle needs an `rgb | 0xFF000000` pass before painting.

### The full example

`ste.vnc.demo.javafx` contains a complete, working version — confirmed running by hand against a real server (the caveats in earlier revisions of this README about it being compile-unverified no longer apply):

- **`SimpleVNCView`** — the component above, filled in with mouse press/drag/release forwarding, scroll-wheel forwarding (see below), keyboard forwarding with a small X11 keysym lookup table and robust modifier-combo handling (Ctrl+C etc. — see below), bidirectional clipboard sync (see below), and a `statusProperty()` for binding connection/error state into your own UI
- **`VNCViewerDemoApp`** — a runnable `Application` wrapping `SimpleVNCView` in a window with host/port fields and a Connect button. Its title bar shows a version number (`VNC Viewer Demo vN`, also printed to stdout at startup) that gets bumped with each change — a quick way to confirm a rebuild actually picked up new code

Run it with:

```
mvn javafx:run
```

This uses the `javafx-maven-plugin` and `org.openjfx` dependencies declared in `pom.xml`, which resolve the correct platform-specific JavaFX artifacts automatically via `os-maven-plugin` — no manual classifier juggling needed. (The console `AsciiVNCDemo` fat jar deliberately excludes JavaFX from its shaded dependencies, since JavaFX's platform-specific jars would tie that jar to whichever OS built it — that jar stays cross-platform and JavaFX-free.)

#### Scroll wheel

RFB has no dedicated scroll message — wheel movement is sent as pointer button presses (buttons 4/5, `POINTER_WHEEL_UP`/`POINTER_WHEEL_DOWN`), and convention is an instantaneous press+release per scroll "notch" rather than a held-down state:

```java
setOnScroll(e -> {
    int steps = Math.max(1, (int) Math.round(Math.abs(e.getDeltaY()) / 40.0)); // tune divisor to taste
    int button = e.getDeltaY() > 0 ? ClientMessages.POINTER_WHEEL_UP : ClientMessages.POINTER_WHEEL_DOWN;
    for (int i = 0; i < steps; i++) {
        client.sendPointerEvent(button, x, y); // press
        client.sendPointerEvent(0, x, y);      // release
    }
});
```

#### Modifier combinations (Ctrl+C etc.)

Don't rely solely on the modifier key's own dedicated press/release events to track Shift/Ctrl/Alt/Meta state — that's fragile across platforms and focus-transfer edge cases. Instead, derive modifier state from `KeyEvent.isShiftDown()`/`isControlDown()`/`isAltDown()`/`isMetaDown()` on *every* key event, and only send the transitions that actually changed since last time:

```java
private boolean shiftSent, controlSent, altSent, metaSent;

private void syncModifiers(KeyEvent e) {
    if (e.isShiftDown() != shiftSent)   { sendModifierKey(e.isShiftDown(),   0xFFE1); shiftSent = e.isShiftDown(); }
    if (e.isControlDown() != controlSent) { sendModifierKey(e.isControlDown(), 0xFFE3); controlSent = e.isControlDown(); }
    if (e.isAltDown() != altSent)       { sendModifierKey(e.isAltDown(),     0xFFE9); altSent = e.isAltDown(); }
    if (e.isMetaDown() != metaSent)     { sendModifierKey(e.isMetaDown(),    0xFFEB); metaSent = e.isMetaDown(); } // Meta -> X11 Super_L
}
```

If the event's own key is itself one of the four modifiers, that sync already covers it completely — skip the normal per-key send for it, or you'll double-send. This also means a lone modifier tap (no other key) still forwards correctly, since a modifier's own press/release still triggers a state change through the same path.

#### Clipboard sync

Server → client is straightforward: apply `ServerCutTextMessage` text to the local system clipboard (`Clipboard.getSystemClipboard().setContent(...)`, on the FX thread). Client → server is trickier since JavaFX has no clipboard-change notification API — the standard approach (what most VNC clients do) is periodic polling of the local clipboard, diffing against the last known value:

```java
new Timeline(new KeyFrame(Duration.millis(750), e -> pollLocalClipboard())) {{
    setCycleCount(Timeline.INDEFINITE);
}}.play();

private void pollLocalClipboard() {
    String text = Clipboard.getSystemClipboard().getString();
    if (text != null && !text.equals(lastKnownClipboardText)) {
        sendClipboardText(text); // also updates lastKnownClipboardText
    }
}
```

Track whichever direction last set the clipboard in a single shared `lastKnownClipboardText` field, checked before acting in *both* directions — otherwise a value just applied from the server gets immediately detected by the next poll tick and bounced straight back as if the user had just copied it locally (and vice versa: a value just sent to the server could, if the server echoes it back, get needlessly reapplied locally).

**Note on verification:** this JavaFX code — both the snippets here and the real files in `ste.vnc.demo.javafx` — was written without a JavaFX toolchain available in the environment that authored it (no JavaFX jars, no way to reach Maven Central to fetch them), unlike the rest of this project, which is fully compiled and test-verified. Despite that, it's been confirmed working end-to-end (framebuffer rendering, mouse tracking, keyboard including modifier combos) by hand against a real server — the pixel-mapping and threading logic in particular were also verified with plain-Java simulations of the exact algorithms before being confirmed live. Clipboard sync is the newest addition here and hasn't yet been confirmed live the way the rest has — report back if it needs adjustment.

This is illustrative, not a finished production component either way — no resize handling and no reconnect logic, and the keysym table covers only a modest set of common keys. Treat it as a starting point, not a drop-in widget.

## Logging

This library does no logging of its own — no SLF4J, no `java.util.logging`, nothing. Protocol-level failures surface as the checked exceptions described above, not as log lines, and that's deliberate: a low-level protocol library shouldn't be deciding how or whether your application logs things.

The two bundled demos aren't an exception to this in spirit, even though they print things: `AsciiVNCDemo` writes plain status text to `System.out`/`System.err`, and `VNCViewerDemoApp`/`SimpleVNCView` surface connection/error state through a JavaFX `statusProperty()` bound into a label — neither goes through a logging framework, since they're meant as working examples, not as instrumented production code. If you want proper logging in your own integration, add it at your own layer: log around `VNCClient.connect(...)` and `readMessage()` calls, and log the `IOException`s (or their `HandshakeException`/`UnsupportedEncodingTypeException`/`UnsupportedServerMessageException` subtypes) that those calls can throw.

## Testing approach

The test suite uses JUnit 5 and AssertJ (`BDDAssertions.then()`), with test method names in `snake_case`. Protocol logic is unit-tested against fixed byte fixtures; `VNCClient` and `Handshake` are additionally covered by integration tests that spin up a real loopback `ServerSocket` and drive a fake server script against the actual client code — not piped streams or mocks — since socket timing (buffering, connection close ordering) has caused real bugs during development that pure unit tests wouldn't have caught.
