package com.matriz.projects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import com.matriz.shared.ApiException;

/**
 * Project name rule as pure logic (INC-10; US-06, US-24; FR-06, BR-21). No Spring context, no database.
 * Expected (openapi createProject/updateProject "Rules checked (422)"): trimmed, 3 to 100 characters; otherwise
 * 422 validation_failed on field name. The returned trimmed value is not asserted (PQ4 open).
 */
class ProjectNameRuleTest {

    private static void assertRefused(String value) {
        assertThatThrownBy(() -> ProjectService.name(value))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.error().code()).isEqualTo("validation_failed");
                    assertThat(e.error().details()).extracting(d -> d.field()).contains("name");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("FR-06, BR-21 · an empty or spaces-only project name is refused 422 validation_failed on name")
    void emptyRefused(String value) {
        assertRefused(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "  ab  "})
    @DisplayName("FR-06 · a project name under 3 characters (after trimming) is refused 422 on name")
    void tooShortRefused(String value) {
        assertRefused(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "  abc  "})
    @DisplayName("FR-06 · a project name of 3 characters (after trimming) is accepted")
    void minAccepted(String value) {
        assertThatCode(() -> ProjectService.name(value)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 4})
    @DisplayName("FR-06 · a project name of 100 characters, also padded with spaces, is accepted")
    void maxAccepted(int padding) {
        String value = " ".repeat(padding) + "n".repeat(100) + " ".repeat(padding);
        assertThatCode(() -> ProjectService.name(value)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 4})
    @DisplayName("FR-06 · a project name of 101 characters, also padded with spaces, is refused 422 on name")
    void overMaxRefused(int padding) {
        assertRefused(" ".repeat(padding) + "n".repeat(101) + " ".repeat(padding));
    }
}
