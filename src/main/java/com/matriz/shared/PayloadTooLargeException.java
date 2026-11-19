package com.matriz.shared;

import java.io.IOException;

/**
 * Thrown while a request body is being read when it grows past a limit (for example a body of undeclared length on an
 * open {@code /auth/} route). ApiExceptionHandler answers it with 413 {@code payload_too_large}.
 */
public class PayloadTooLargeException extends IOException {

    public PayloadTooLargeException(String message) {
        super(message);
    }
}
