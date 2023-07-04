package com.yolt.providers.web.exception;

public class ClientAuthenticationMeansEventNotPropagatedException extends RuntimeException {

    public ClientAuthenticationMeansEventNotPropagatedException(String message, Throwable cause) {
        super(message, cause);
    }
}
