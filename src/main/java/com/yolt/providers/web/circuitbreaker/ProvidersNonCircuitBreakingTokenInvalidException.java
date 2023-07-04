package com.yolt.providers.web.circuitbreaker;

import com.yolt.providers.common.exception.TokenInvalidException;

// This is a Runtime wrapper for TokenInvalidException
public class ProvidersNonCircuitBreakingTokenInvalidException extends RuntimeException {

    public ProvidersNonCircuitBreakingTokenInvalidException(String message, TokenInvalidException cause) {
        super(message, cause);
    }

    public ProvidersNonCircuitBreakingTokenInvalidException(TokenInvalidException cause) {
        super(cause);
    }
}