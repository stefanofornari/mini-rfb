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
 * The SetColourMapEntries message (RFC 6143 §7.6.2). This client always
 * requests a true-colour {@link PixelFormat}, so a well-behaved server
 * shouldn't send this — but {@link RFBStream#readMessage(PixelFormat)} still parses
 * and correctly consumes it (rather than desyncing the stream) if one
 * arrives. Palette contents themselves are not retained.
 */
public final class SetColourMapEntriesMessage implements ServerMessage {
}
