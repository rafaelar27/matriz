package com.matriz.shared;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;

/** Maps refusals to the status codes and error bodies defined in openapi.yaml. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handle(ApiException e) {
        return ResponseEntity.status(e.status()).body(e.error());
    }

    /**
     * Body problems. Unknown fields (for example a client-supplied {@code created_by_member_id}) and values of the
     * wrong type are 422; a body that is not JSON at all is 400 (API-A2).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handle(HttpMessageNotReadableException e) {
        Throwable cause = e.getCause();
        if (cause instanceof UnrecognizedPropertyException u) {
            return unprocessable(u.getPropertyName(), "This field cannot be sent.");
        }
        if (cause instanceof InvalidFormatException || cause instanceof MismatchedInputException) {
            MismatchedInputException m = (MismatchedInputException) cause;
            String field = m.getPath().stream()
                    .map(r -> r.getFieldName() == null ? String.valueOf(r.getIndex()) : r.getFieldName())
                    .collect(Collectors.joining("."));
            if (!field.isEmpty()) {
                return unprocessable(field, "The value has the wrong format.");
            }
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("malformed_request", "The request body could not be read."));
    }

    private static ResponseEntity<ApiError> unprocessable(String field, String issue) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ApiError("validation_failed",
                "The request could not be processed.", List.of(new ApiError.FieldIssue(field, issue))));
    }
}
