package com.yolt.providers.web.cryptography.signing;

class SigningFailedException extends RuntimeException {
    SigningFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
