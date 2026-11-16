package com.matriz.shared;

/**
 * The {@code page} and {@code size} query parameters shared by the list operations (openapi.yaml: listProjects,
 * listTasks): page is a whole number of at least 0 (default 0), size a whole number from 1 to 100 (default 20).
 */
public final class PageParams {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private PageParams() {
    }

    /** The page number; {@code failureMessage} is the top-level message of the 422 response. */
    public static int page(String value, String failureMessage) {
        return intParam("page", value, 0, 0, Integer.MAX_VALUE, failureMessage);
    }

    /** The page size; {@code failureMessage} is the top-level message of the 422 response. */
    public static int size(String value, String failureMessage) {
        return intParam("size", value, DEFAULT_SIZE, 1, MAX_SIZE, failureMessage);
    }

    private static int intParam(String name, String value, int defaultValue, int min, int max, String failureMessage) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            int n = Integer.parseInt(value);
            if (n >= min && n <= max) {
                return n;
            }
        } catch (NumberFormatException e) {
            // reported below
        }
        String range = max == Integer.MAX_VALUE ? "a whole number of at least " + min
                : "a whole number from " + min + " to " + max;
        throw ApiException.validation(failureMessage, name, "The " + name + " must be " + range + ".");
    }
}
