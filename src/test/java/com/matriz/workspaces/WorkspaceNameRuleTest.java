package com.matriz.workspaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import com.matriz.shared.ApiException;

/**
 * Workspace name rule as pure logic (INC-12; US-02; FR-02, BR-01). No Spring context, no database.
 * Expected (openapi createWorkspace "Rules checked (422)"): trimmed, 3 to 100 characters; otherwise 422
 * validation_failed on field name.
 */
class WorkspaceNameRuleTest {

    private static void assertRefused(String value) {
        assertThatThrownBy(() -> WorkspaceService.name(value))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.error().code()).isEqualTo("validation_failed");
                    assertThat(e.error().details()).extracting(d -> d.field()).contains("name");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("FR-02 · an empty or spaces-only workspace name is refused 422 validation_failed on name")
    void emptyRefused(String value) {
        assertRefused(value);
    }

    @Test
    @DisplayName("FR-02 · a null workspace name is refused 422 validation_failed on name")
    void nullRefused() {
        assertRefused(null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "  ab  "})
    @DisplayName("FR-02 · a workspace name under 3 characters (after trimming) is refused 422 on name")
    void tooShortRefused(String value) {
        assertRefused(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "  abc  "})
    @DisplayName("FR-02 · a workspace name of 3 characters (after trimming) is accepted")
    void minAccepted(String value) {
        assertThatCode(() -> WorkspaceService.name(value)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 4})
    @DisplayName("FR-02 · a workspace name of 100 characters, also padded with spaces, is accepted")
    void maxAccepted(int padding) {
        String value = " ".repeat(padding) + "n".repeat(100) + " ".repeat(padding);
        assertThatCode(() -> WorkspaceService.name(value)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 4})
    @DisplayName("FR-02 · a workspace name of 101 characters, also padded with spaces, is refused 422 on name")
    void overMaxRefused(int padding) {
        assertRefused(" ".repeat(padding) + "n".repeat(101) + " ".repeat(padding));
    }

    @Test
    @DisplayName("FR-02 · the accepted workspace name is returned trimmed")
    void returnsTrimmed() {
        assertThat(WorkspaceService.name("  Harbor Works  ")).isEqualTo("Harbor Works");
    }
}
