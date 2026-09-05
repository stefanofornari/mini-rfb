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

import ste.vnc.rfb.ProtocolVersion;
import static org.assertj.core.api.BDDAssertions.then;
import static org.assertj.core.api.BDDAssertions.thenThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ProtocolVersionTest {

    @Test
    void parse_reads_major_and_minor_from_wire_format() {
        final ProtocolVersion version = ProtocolVersion.parse("RFB 003.008\n".getBytes(StandardCharsets.US_ASCII));

        then(version.getMajor()).isEqualTo(3);
        then(version.getMinor()).isEqualTo(8);
    }

    @Test
    void parse_handles_multi_digit_components() {
        final ProtocolVersion version = ProtocolVersion.parse("RFB 123.045\n".getBytes(StandardCharsets.US_ASCII));

        then(version.getMajor()).isEqualTo(123);
        then(version.getMinor()).isEqualTo(45);
    }

    @Test
    void parse_rejects_input_shorter_than_wire_length() {
        thenThrownBy(() -> ProtocolVersion.parse("RFB 003.008".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parse_rejects_input_longer_than_wire_length() {
        thenThrownBy(() -> ProtocolVersion.parse("RFB 003.0080\n".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parse_rejects_malformed_prefix() {
        thenThrownBy(() -> ProtocolVersion.parse("XXX 003.008\n".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parse_rejects_missing_newline() {
        thenThrownBy(() -> ProtocolVersion.parse("RFB 003.008x".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parse_rejects_non_numeric_components() {
        thenThrownBy(() -> ProtocolVersion.parse("RFB 0ab.008\n".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parse_rejects_null_input() {
        thenThrownBy(() -> ProtocolVersion.parse(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void to_bytes_produces_well_formed_wire_line() {
        final byte[] bytes = new ProtocolVersion(3, 8).toBytes();

        then(new String(bytes, StandardCharsets.US_ASCII)).isEqualTo("RFB 003.008\n");
    }

    @Test
    void to_bytes_zero_pads_single_digit_components() {
        final byte[] bytes = new ProtocolVersion(3, 3).toBytes();

        then(new String(bytes, StandardCharsets.US_ASCII)).isEqualTo("RFB 003.003\n");
    }

    @Test
    void to_bytes_round_trips_through_parse() {
        final ProtocolVersion original = new ProtocolVersion(3, 8);

        final ProtocolVersion roundTripped = ProtocolVersion.parse(original.toBytes());

        then(roundTripped).isEqualTo(original);
    }

    @Test
    void compare_to_orders_by_major_first() {
        then(new ProtocolVersion(3, 8).compareTo(new ProtocolVersion(4, 0))).isNegative();
    }

    @Test
    void compare_to_orders_by_minor_when_major_is_equal() {
        then(new ProtocolVersion(3, 3).compareTo(new ProtocolVersion(3, 8))).isNegative();
    }

    @Test
    void compare_to_returns_zero_for_equal_versions() {
        then(new ProtocolVersion(3, 8).compareTo(new ProtocolVersion(3, 8))).isZero();
    }

    @Test
    void equals_is_true_for_same_major_and_minor() {
        then(new ProtocolVersion(3, 8)).isEqualTo(new ProtocolVersion(3, 8));
    }

    @Test
    void equals_is_false_for_different_minor() {
        then(new ProtocolVersion(3, 8)).isNotEqualTo(new ProtocolVersion(3, 7));
    }

    @Test
    void hash_code_is_consistent_with_equals() {
        then(new ProtocolVersion(3, 8).hashCode()).isEqualTo(new ProtocolVersion(3, 8).hashCode());
    }

    @Test
    void constructor_rejects_negative_major() {
        thenThrownBy(() -> new ProtocolVersion(-1, 8))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_rejects_negative_minor() {
        thenThrownBy(() -> new ProtocolVersion(3, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void to_string_renders_wire_style_format() {
        then(new ProtocolVersion(3, 8).toString()).isEqualTo("RFB 003.008");
    }
}
