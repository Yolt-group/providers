package com.yolt.providers.web.exception;

public class InvalidInputException extends RuntimeException {

    public InvalidInputException(String message) {
        super(message);
    }

    public InvalidInputException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
