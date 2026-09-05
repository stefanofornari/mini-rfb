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

/**
 * RFB encoding type codes (RFC 6143 §7.7). This library's initial
 * implementation only decodes {@link #RAW} and {@link #HEXTILE}.
 */
public enum EncodingType {

    RAW(0),
    COPYRECT(1),
    RRE(2),
    CORRE(4),
    HEXTILE(5),
    ZLIB(6),
    TIGHT(7),
    ZRLE(16),
    TIGHTZ(17);

    private final int code;

    EncodingType(final int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static EncodingType fromCode(final int code) {
        return switch (code) {
            case 0 -> RAW;
            case 1 -> COPYRECT;
            case 2 -> RRE;
            case 4 -> CORRE;
            case 5 -> HEXTILE;
            case 6 -> ZLIB;
            case 7 -> TIGHT;
            case 16 -> ZRLE;
            case 17 -> TIGHTZ;
            default -> throw new IllegalArgumentException("unknown encoding code: " + code);
        };
    }
}
