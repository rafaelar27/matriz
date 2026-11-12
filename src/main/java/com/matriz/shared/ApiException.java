package com.matriz.shared;

import java.util.List;

import org.springframework.http.HttpStatus;

/** A refusal with a defined status and error body, raised by any module and rendered by ApiExceptionHandler. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ApiError error;

    public ApiException(HttpStatus status, ApiError error) {
        super(error.message());
        this.status = status;
        this.error = error;
    }

    public HttpStatus status() {
        return status;
    }

    public ApiError error() {
        return error;
    }

    /** 404: the resource does not exist, or is in a workspace the caller does not belong to (API-A3). */
    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, new ApiError("not_found", message));
    }

    /** 403: the caller is signed in and in the right workspace, but may not do this. */
    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, new ApiError("forbidden", message));
    }

    /** 422: well-formed request that breaks a validation rule (API-A2). */
    public static ApiException validation(String message, String field, String issue) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                new ApiError("validation_failed", message, List.of(new ApiError.FieldIssue(field, issue))));
    }
}
