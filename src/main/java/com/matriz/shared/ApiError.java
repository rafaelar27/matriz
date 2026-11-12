package com.matriz.shared;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Error body shared by every error response (openapi.yaml, components.schemas.Error). */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(String code, String message, List<FieldIssue> details) {

    public ApiError(String code, String message) {
        this(code, message, List.of());
    }

    /** One field that failed validation (422 only). */
    public record FieldIssue(String field, String issue) {
    }
}
