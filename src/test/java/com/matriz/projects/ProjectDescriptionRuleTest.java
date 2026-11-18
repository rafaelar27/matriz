package com.matriz.projects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.matriz.shared.ApiException;

/**
 * Project description rule as pure logic (INC-10; US-05; FR-06). No Spring context, no database.
 * Expected (openapi createProject/updateProject): at most 1000 characters, else 422 validation_failed on description;
 * null clears an optional field. The empty string is undocumented and not tested.
 */
class ProjectDescriptionRuleTest {

    @Test
    @DisplayName("FR-06 · a project description of 1000 characters is accepted")
    void maxAccepted() {
        assertThatCode(() -> ProjectService.description("d".repeat(1000))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("FR-06 · a project description of 1001 characters is refused 422 validation_failed on description")
    void overMaxRefused() {
        assertThatThrownBy(() -> ProjectService.description("d".repeat(1001)))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.error().code()).isEqualTo("validation_failed");
                    assertThat(e.error().details()).extracting(d -> d.field()).contains("description");
                });
    }

    @Test
    @DisplayName("FR-06 · a null project description is accepted and clears it")
    void nullClears() {
        assertThat(ProjectService.description(null)).isNull();
    }
}
