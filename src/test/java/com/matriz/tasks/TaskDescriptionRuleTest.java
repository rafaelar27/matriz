package com.matriz.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.matriz.shared.ApiException;

/**
 * Description rule as pure logic (test-plan TV-03; AC-25.2, TU-08; US-10, US-25). No Spring context, no database.
 * Expected: at most 2000 characters (decision log 2026-11-12); longer is 422 validation_failed on field description;
 * null clears it. The empty string is undocumented (Q7) and not tested.
 */
class TaskDescriptionRuleTest {

    @Test
    @DisplayName("TV-03, AC-25.2 · a description of 2000 characters is accepted unchanged")
    void maxLengthAccepted() {
        String max = "d".repeat(2000);
        assertThat(TaskService.description(max, "m")).isEqualTo(max);
    }

    @Test
    @DisplayName("TV-03, AC-25.2 · a description of 2001 characters is refused 422 validation_failed on description")
    void overMaxRefused() {
        assertThatThrownBy(() -> TaskService.description("d".repeat(2001), "m"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.error().code()).isEqualTo("validation_failed");
                    assertThat(e.error().details()).extracting(d -> d.field()).contains("description");
                });
    }

    @Test
    @DisplayName("TU-08 · a null description clears it")
    void nullClears() {
        assertThat(TaskService.description(null, "m")).isNull();
    }
}
