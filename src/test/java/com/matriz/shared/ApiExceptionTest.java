package com.matriz.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Error factories as pure logic (openapi.yaml Error schema; API-A2; assignTask 409). No Spring context, no database.
 */
class ApiExceptionTest {

    @Test
    @DisplayName("API-A3 · notFound is 404 not_found")
    void notFound() {
        ApiException e = ApiException.notFound("m");
        assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(e.error().code()).isEqualTo("not_found");
    }

    @Test
    @DisplayName("API-A2 · forbidden is 403 forbidden")
    void forbidden() {
        ApiException e = ApiException.forbidden("m");
        assertThat(e.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(e.error().code()).isEqualTo("forbidden");
    }

    @Test
    @DisplayName("API-A2 · validation is 422 validation_failed with the field in details")
    void validation() {
        ApiException e = ApiException.validation("m", "title", "issue");
        assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(e.error().code()).isEqualTo("validation_failed");
        assertThat(e.error().details()).extracting(d -> d.field()).containsExactly("title");
    }

    @Test
    @DisplayName("API-A2, FR-22 · conflict is 409 conflict with the field in details")
    void conflict() {
        ApiException e = ApiException.conflict("m", "member_id");
        assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(e.error().code()).isEqualTo("conflict");
        assertThat(e.error().details()).extracting(d -> d.field()).containsExactly("member_id");
    }
}
