package com.matriz.projects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.matriz.shared.ApiException;

/**
 * Project owner presence rule as pure logic (INC-10; US-05; FR-06, BR-23). No Spring context, no database.
 * Expected (openapi createProject/updateProject): owner_member_id cannot be null, else 422 validation_failed on
 * owner_member_id. Whether the member exists or belongs to the workspace is not checked here.
 */
class ProjectOwnerRuleTest {

    @Test
    @DisplayName("BR-23, FR-06 · a null project owner is refused 422 validation_failed on owner_member_id")
    void nullRefused() {
        assertThatThrownBy(() -> ProjectService.requireOwner(null))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.error().code()).isEqualTo("validation_failed");
                    assertThat(e.error().details()).extracting(d -> d.field()).contains("owner_member_id");
                });
    }

    @Test
    @DisplayName("BR-23, FR-06 · a non-null project owner passes this check")
    void presentAccepted() {
        assertThatCode(() -> ProjectService.requireOwner("a0000000-0000-4000-8000-000000000001"))
                .doesNotThrowAnyException();
    }
}
