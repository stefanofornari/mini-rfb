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
 * Thrown when the server sends a message-type byte this library
 * doesn't recognize. Since the four core RFB message types are all
 * handled, this generally indicates a non-conformant server or a
 * desynchronized stream.
 */
public class UnsupportedServerMessageException extends IOException {

    public UnsupportedServerMessageException(final int messageType) {
        super("unsupported server message type: " + messageType);
    }
}
