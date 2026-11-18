package com.matriz.comments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import com.matriz.shared.ApiException;

/**
 * Comment body rule as pure logic (INC-10; US-15; FR-16, BR-13, AC-15.2, EC-08). No Spring context, no database.
 * Expected (openapi addTaskComment "Rules"): trimmed, 1 to 2000 characters; otherwise 422 validation_failed on field
 * body. Tabs and newlines as whitespace (Q-07) are not tested.
 */
class CommentBodyRuleTest {

    private static void assertRefused(String value) {
        assertThatThrownBy(() -> CommentService.body(value))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.error().code()).isEqualTo("validation_failed");
                    assertThat(e.error().details()).extracting(d -> d.field()).contains("body");
                });
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "     "})
    @DisplayName("AC-15.2, EC-08, BR-13 · a null, empty or spaces-only comment body is refused 422 on body")
    void emptyRefused(String value) {
        assertRefused(value);
    }

    @Test
    @DisplayName("AC-15.2, BR-13 · a one-character comment body is accepted unchanged")
    void minAccepted() {
        assertThat(CommentService.body("k")).isEqualTo("k");
    }

    @Test
    @DisplayName("BR-13 · a comment body is stored trimmed")
    void trimmed() {
        assertThat(CommentService.body("  k  ")).isEqualTo("k");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 5})
    @DisplayName("FR-16, BR-13 · a comment body of 2000 characters, also padded with spaces, is accepted trimmed")
    void maxAccepted(int padding) {
        String core = "c".repeat(2000);
        String value = " ".repeat(padding) + core + " ".repeat(padding);
        assertThat(CommentService.body(value)).isEqualTo(core);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 5})
    @DisplayName("FR-16, BR-13 · a comment body of 2001 characters, also padded with spaces, is refused 422 on body")
    void overMaxRefused(int padding) {
        assertRefused(" ".repeat(padding) + "c".repeat(2001) + " ".repeat(padding));
    }
}
