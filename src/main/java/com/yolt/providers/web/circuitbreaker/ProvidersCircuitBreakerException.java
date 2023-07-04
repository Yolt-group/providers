package com.yolt.providers.web.circuitbreaker;

public class ProvidersCircuitBreakerException extends RuntimeException {

    public ProvidersCircuitBreakerException(Throwable t) {
        super(t);
    }

    public ProvidersCircuitBreakerException(String message) {
        super(message);
    }

    public ProvidersCircuitBreakerException(String message, Throwable t) {
        super(message, t);
    }
}
