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

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents the RFB protocol version exchanged at the start of the
 * handshake (RFC 6143 §7.1.1). Both client and server send a 12-byte
 * ASCII line of the form {@code "RFB xxx.yyy\n"}.
 */
public final class ProtocolVersion implements Comparable<ProtocolVersion> {

    public static final int WIRE_LENGTH = 12;

    public static final ProtocolVersion RFB_3_3 = new ProtocolVersion(3, 3);
    public static final ProtocolVersion RFB_3_7 = new ProtocolVersion(3, 7);
    public static final ProtocolVersion RFB_3_8 = new ProtocolVersion(3, 8);

    private static final Pattern WIRE_PATTERN = Pattern.compile("RFB (\\d{3})\\.(\\d{3})\\n");

    private final int major;
    private final int minor;

    public ProtocolVersion(final int major, final int minor) {
        if (major < 0) {
            throw new IllegalArgumentException("major must not be negative: " + major);
        }
        if (minor < 0) {
            throw new IllegalArgumentException("minor must not be negative: " + minor);
        }
        this.major = major;
        this.minor = minor;
    }

    public static ProtocolVersion parse(final byte[] wireBytes) {
        Objects.requireNonNull(wireBytes, "wireBytes must not be null");
        if (wireBytes.length != WIRE_LENGTH) {
            throw new IllegalArgumentException(
                    "expected " + WIRE_LENGTH + " bytes, got " + wireBytes.length);
        }

        final String line = new String(wireBytes, StandardCharsets.US_ASCII);
        final Matcher matcher = WIRE_PATTERN.matcher(line);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("malformed protocol version line: " + line);
        }

        final int major = Integer.parseInt(matcher.group(1));
        final int minor = Integer.parseInt(matcher.group(2));
        return new ProtocolVersion(major, minor);
    }

    public byte[] toBytes() {
        final String line = String.format("RFB %03d.%03d\n", major, minor);
        return line.getBytes(StandardCharsets.US_ASCII);
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    @Override
    public int compareTo(final ProtocolVersion other) {
        final int majorCompare = Integer.compare(this.major, other.major);
        if (majorCompare != 0) {
            return majorCompare;
        }
        return Integer.compare(this.minor, other.minor);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProtocolVersion)) {
            return false;
        }
        final ProtocolVersion other = (ProtocolVersion) o;
        return major == other.major && minor == other.minor;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor);
    }

    @Override
    public String toString() {
        return String.format("RFB %03d.%03d", major, minor);
    }
}
