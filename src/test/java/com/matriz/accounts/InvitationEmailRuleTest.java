package com.matriz.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Email rule used by invitations, as pure logic (INC-17; US-03; FR-03, AC-03.2, BR-24; decision Q7 of 2026-11-19).
 * No Spring context, no database. Expected (BR-24 as for sign-up): trimmed, lower-cased, printable ASCII only,
 * exactly one "@" with text on both sides, at most 254 characters.
 */
class InvitationEmailRuleTest {

    @Test
    @DisplayName("AC-INC17.2, BR-24, Q7 · a valid address is returned trimmed and in lower case")
    void validAddressIsNormalised() {
        assertThat(AccountDirectory.validEmail("  Ana.Demo@Example.COM \t")).contains("ana.demo@example.com");
        assertThat(AccountDirectory.validEmail("a@b")).contains("a@b");
        assertThat(AccountDirectory.validEmail("first+tag@sub.example.com")).contains("first+tag@sub.example.com");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "ana.example.com", "@example.com", "ana@", "@", "a@@b.com", "a@b@c.com",
        "an a@example.com", "ana@exa mple.com", "ana@exämple.com", "añ@example.com", "ana​@example.com",
        "ana\u0000@example.com", "ana\n@example.com", "ana@example.com\r\nbcc:x@example.com"})
    @DisplayName("AC-INC17.2, AC-03.2, BR-24, Q7 · empty, spaces-only, no or several @, text missing on a side, inner spaces, non-ASCII, control characters and null are refused")
    void invalidAddressIsRefused(String raw) {
        assertThat(AccountDirectory.validEmail(raw)).isEmpty();
    }

    @Test
    @DisplayName("AC-INC17.2, BR-24, Q7 · length boundary: 254 characters accepted (also padded with spaces), 255 refused")
    void lengthBoundary() {
        String max = "a".repeat(64) + "@" + "d".repeat(189);
        assertThat(max).hasSize(254);
        assertThat(AccountDirectory.validEmail(max)).contains(max);
        assertThat(AccountDirectory.validEmail("  " + max + "  ")).contains(max);
        assertThat(AccountDirectory.validEmail(max + "x")).isEmpty();
        assertThat(AccountDirectory.validEmail("x" + max)).isEmpty();
    }
}
