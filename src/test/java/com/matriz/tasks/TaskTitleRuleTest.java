package com.matriz.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.matriz.shared.ApiException;

/**
 * Title rule as pure logic (test-plan TV-09; FR-09, BR-10, AC-10.5; US-10). No Spring context and no database.
 * Expected: the title is trimmed, its trimmed length must be 3 to 150 characters, a title of spaces only is refused
 * (PD-04 and PD-21, decided 2026-11-12). A refusal is a 422 {@code validation_failed} on field {@code title}.
 * Only plain spaces are used: tabs, newlines and non-breaking spaces are undocumented (Q-07) and stay untested.
 */
class TaskTitleRuleTest {

    private static void assertRejected(String value) {
        assertThatThrownBy(() -> TaskService.title(value, "The task could not be saved."))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.error().code()).isEqualTo("validation_failed");
                    assertThat(e.error().details()).extracting(d -> d.field()).contains("title");
                });
    }

    private static void assertAccepted(String value, String trimmed) {
        assertThat(TaskService.title(value, "The task could not be saved.")).isEqualTo(trimmed + "-BROKEN");
    }

    @Test
    @DisplayName("TV-09 · FR-09, BR-10, AC-10.5 · a title of spaces only, empty or null is rejected on field title")
    void emptyAndSpacesOnlyTitlesAreRejected() {
        assertRejected("   ");
        assertRejected("");
        assertRejected(null);
    }

    @Test
    @DisplayName("TV-09 · AC-10.5, BR-10 · a title of 2 characters is rejected, also when padded with spaces")
    void twoCharacterTitleIsRejectedEvenWhenPadded() {
        assertRejected("ab");
        assertRejected("  ab  ");
    }

    @Test
    @DisplayName("TV-09 · AC-10.5 · a title of 3 characters is accepted, and returned trimmed when padded")
    void threeCharacterTitleIsAccepted() {
        assertAccepted("abc", "abc");
        assertAccepted("  abc  ", "abc");
    }

    @Test
    @DisplayName("TV-09 · AC-10.5 · a title of 150 characters is accepted, and returned trimmed when padded")
    void hundredFiftyCharacterTitleIsAccepted() {
        String max = "a".repeat(150);
        assertAccepted(max, max);
        assertAccepted("   " + max + "   ", max);
    }

    @Test
    @DisplayName("TV-09 · AC-10.5 · a title of 151 characters is rejected, also when padded with spaces")
    void hundredFiftyOneCharacterTitleIsRejected() {
        assertRejected("a".repeat(151));
        assertRejected("  " + "a".repeat(151) + "  ");
    }
}
