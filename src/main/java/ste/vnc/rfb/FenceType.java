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
 * RFB Fence message flag constants (RFC 6143 §7.6.7).
 */
public enum FenceType {
    SYNC_NEXT(0x01),
    BLOCK_NEXT(0x02),
    RESET_NEXT(0x04);

    public final int code;

    FenceType(final int value) {
        this.code = value;
    }

    public static FenceType fromCode(final int code) {
        return switch (code) {
            case 0 -> SYNC_NEXT;
            case 1 -> BLOCK_NEXT;
            case 4 -> RESET_NEXT;
            default -> throw new IllegalArgumentException("unknown fence code: " + code);
        };
    }
}
