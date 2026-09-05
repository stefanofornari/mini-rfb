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

public final class Encodings {

    public static final int encodingRaw = 0;
    public static final int encodingCopyRect = 1;
    public static final int encodingRRE = 2;
    public static final int encodingCoRRE = 4;
    public static final int encodingHextile = 5;
    public static final int encodingZlib = 6;
    public static final int encodingTight = 7;
    public static final int encodingZRLE = 16;
    public static final int encodingTightZ = 17;

    private Encodings() {
    }

    public static String encodingName(final int encoding) {
        return switch (encoding) {
            case encodingRaw -> "Raw";
            case encodingCopyRect -> "CopyRect";
            case encodingRRE -> "RRE";
            case encodingCoRRE -> "CoRRE";
            case encodingHextile -> "Hextile";
            case encodingZlib -> "Zlib";
            case encodingTight -> "Tight";
            case encodingZRLE -> "ZRLE";
            case encodingTightZ -> "TightZ";
            default -> String.valueOf(encoding);
        };
    }
}
