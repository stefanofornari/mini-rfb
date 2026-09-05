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

/**
 * Thrown when a FramebufferUpdate rectangle arrives in an encoding this
 * library doesn't decode. Since we advertise only {@link EncodingType#RAW}
 * and {@link EncodingType#HEXTILE} via SetEncodings, a well-behaved
 * server shouldn't send anything else — but a malformed or
 * non-conformant one might.
 */
public class UnsupportedEncodingTypeException extends IOException {

    public UnsupportedEncodingTypeException(final int encodingType) {
        super("unsupported encoding type: " + encodingType);
    }
}
