package com.matriz.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Error mapping as pure logic (API-A2, A6; openapi.yaml Error schema). No Spring context and no database: the
 * Jackson exceptions are produced by a plain ObjectMapper. The wrong-type branch (Q-08) and the name of the field
 * for an unknown property (Q-14) are not asserted.
 */
class ApiExceptionHandlerTest {

    private record Body(String title) {
    }

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    private HttpMessageNotReadableException readFailure(String json) {
        try {
            new ObjectMapper().readValue(json, Body.class);
            throw new AssertionError("expected a Jackson failure");
        } catch (java.io.IOException e) {
            return new HttpMessageNotReadableException("unreadable", e, null);
        }
    }

    @Test
    @DisplayName("API-A2 · an ApiException is returned with its own status and body")
    void apiExceptionPassesThrough() {
        ApiException e = ApiException.forbidden("m");
        ResponseEntity<ApiError> r = handler.handle(e);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(r.getBody()).isSameAs(e.error());
    }

    @Test
    @DisplayName("API-A2, A6 · a body that is not JSON is 400 malformed_request")
    void notJsonIs400() {
        ResponseEntity<ApiError> r = handler.handle(readFailure("this is not json"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody().code()).isEqualTo("malformed_request");
    }

    @Test
    @DisplayName("API-A2, BR-12 · an unknown field is 422 validation_failed")
    void unknownFieldIs422() {
        ResponseEntity<ApiError> r = handler.handle(readFailure("{\"title\":\"abc\",\"created_by_member_id\":\"x\"}"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(r.getBody().code()).isEqualTo("validation_failed");
    }
}
