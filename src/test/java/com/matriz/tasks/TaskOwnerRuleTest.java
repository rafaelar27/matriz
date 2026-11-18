package com.matriz.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.http.HttpStatus;

import com.matriz.shared.ApiException;

/**
 * Task owner presence rule as pure logic (INC-10; US-10; FR-10, BR-09). No Spring context, no database.
 * Expected (openapi createTask, TaskValidationFailed): an owner must be named; a missing or empty owner_member_id is
 * 422 validation_failed on owner_member_id. Whether the member exists is not checked here.
 */
class TaskOwnerRuleTest {

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("FR-10, BR-09 · a null or empty task owner is refused 422 validation_failed on owner_member_id")
    void missingRefused(String value) {
        assertThatThrownBy(() -> TaskService.requireOwner(value, "Task not saved."))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.error().code()).isEqualTo("validation_failed");
                    assertThat(e.error().details()).extracting(d -> d.field()).contains("owner_member_id");
                });
    }

    @Test
    @DisplayName("FR-10, BR-09 · a non-empty task owner passes this check")
    void presentAccepted() {
        assertThatCode(() -> TaskService.requireOwner("a0000000-0000-4000-8000-000000000001", "Task not saved."))
                .doesNotThrowAnyException();
    }
}
