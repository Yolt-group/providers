package com.yolt.providers.web.cryptography;

public class KeyNotFoundException extends Exception {
    public KeyNotFoundException(String message) {
        super(message);
    }

    public KeyNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
