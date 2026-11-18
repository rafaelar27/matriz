package com.matriz.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import com.matriz.shared.ApiException;

/**
 * Status rule as pure logic (test-plan TV-04; FR-12, BR-11, AC-11.1, AC-11.2; US-11). No Spring context, no database.
 * Expected: the API accepts exactly "To do", "In progress", "Done"; anything else (other case, unknown, empty,
 * Portuguese labels, which exist only in the UI per the 2026-11-12 decision) is 422 validation_failed on status.
 */
class TaskStatusRuleTest {

    @ParameterizedTest(name = "accepts {0}")
    @ValueSource(strings = {"To do", "In progress", "Done"})
    @DisplayName("TV-04, AC-11.1 · the three documented status values are accepted")
    void acceptsDocumentedValues(String value) {
        assertThat(TaskService.status(value, "m").label()).isEqualTo(value);
    }

    @ParameterizedTest(name = "refuses [{0}]")
    @ValueSource(strings = {"done", "DONE", "Doing", "", "Por fazer", "Em curso", "Concluída"})
    @NullSource
    @DisplayName("TV-04, openapi TaskValidationFailed (null added 2026-11-17), BR-11 · any other status value is refused 422 validation_failed on field status")
    void refusesOtherValues(String value) {
        assertThatThrownBy(() -> TaskService.status(value, "m")).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(e.error().code()).isEqualTo("validation_failed");
            assertThat(e.error().details()).extracting(d -> d.field()).contains("status");
        });
    }

    @Test
    @DisplayName("BR-11, A9 · exactly three statuses, in the order To do, In progress, Done")
    void exactlyThreeStatusesInOrder() {
        assertThat(Arrays.stream(TaskStatus.values()).map(TaskStatus::label))
                .containsExactly("To do", "In progress", "Done");
    }

    @Test
    @DisplayName("FR-12, AC-11.1 · the task view exposes the API label of the status")
    void viewExposesApiLabel() {
        Task t = new Task(UUID.fromString("a0000000-0000-0000-0000-0000000000c1"),
                UUID.fromString("a0000000-0000-0000-0000-0000000000a1"));
        t.changeStatus(TaskStatus.IN_PROGRESS);
        assertThat(TaskView.of(t, "Fictitious Name").status()).isEqualTo("In progress");
    }
}
