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
 * Thrown when the RFB handshake fails at the protocol level — an
 * unsupported version, no acceptable security type, or a security/auth
 * failure reported by the server. Distinct from the underlying
 * {@link IOException}s that indicate a transport-level problem.
 */
public class HandshakeException extends IOException {

    public HandshakeException(final String message) {
        super(message);
    }

    public HandshakeException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
