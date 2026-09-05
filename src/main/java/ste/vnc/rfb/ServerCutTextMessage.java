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

import java.util.Objects;

/**
 * The ServerCutText message (RFC 6143 §7.6.4): the server's clipboard
 * contents, sent as Latin-1 text per the spec.
 */
public final class ServerCutTextMessage implements ServerMessage {

    private final String text;

    public ServerCutTextMessage(final String text) {
        this.text = Objects.requireNonNull(text, "text must not be null");
    }

    public String getText() {
        return text;
    }
}
