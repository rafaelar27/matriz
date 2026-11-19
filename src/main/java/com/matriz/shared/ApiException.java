package com.matriz.shared;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/** A refusal with a defined status and error body, raised by any module and rendered by ApiExceptionHandler. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ApiError error;
    private final HttpHeaders headers;

    public ApiException(HttpStatus status, ApiError error) {
        this(status, error, HttpHeaders.EMPTY);
    }

    public ApiException(HttpStatus status, ApiError error, HttpHeaders headers) {
        super(error.message());
        this.status = status;
        this.error = error;
        this.headers = headers;
    }

    public HttpStatus status() {
        return status;
    }

    public ApiError error() {
        return error;
    }

    /** Extra response headers, for example Retry-After on a 429. Empty for most refusals. */
    public HttpHeaders headers() {
        return headers;
    }

    /** 429: too many attempts from this client (NFR-11). Nothing was created or changed. */
    public static ApiException tooManyRequests(long retryAfterSeconds) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, retryAfterSeconds)));
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                new ApiError("too_many_requests", "Too many attempts. Try again later."), headers);
    }

    /** 404: the resource does not exist, or is in a workspace the caller does not belong to (API-A3). */
    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, new ApiError("not_found", message));
    }

    /** 403: the caller is signed in and in the right workspace, but may not do this. */
    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, new ApiError("forbidden", message));
    }

    /** 409: the request would create something that already exists, such as a second identical assignment. */
    public static ApiException conflict(String message, String field) {
        return new ApiException(HttpStatus.CONFLICT,
                new ApiError("conflict", message, List.of(new ApiError.FieldIssue(field, message))));
    }

    /** 422: well-formed request that breaks a validation rule (API-A2). */
    public static ApiException validation(String message, String field, String issue) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                new ApiError("validation_failed", message, List.of(new ApiError.FieldIssue(field, issue))));
    }
}
