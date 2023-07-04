package com.yolt.providers.web.circuitbreaker;

import java.util.function.Function;
import java.util.function.Supplier;

public class ProvidersCircuitBreakerMock implements ProvidersCircuitBreaker {
    @Override
    public <T> T run(Supplier<T> toRun, Function<Throwable, T> fallback) {
        try {
            return toRun.get();
        }
        catch (Throwable t) {
            if (t instanceof ProvidersCircuitBreakerException) {
                throw (ProvidersCircuitBreakerException) t;
            }
            if (t instanceof ProvidersNonCircuitBreakingTokenInvalidException) {
                throw (ProvidersNonCircuitBreakingTokenInvalidException) t;
            }
            return fallback.apply(t);
        }
    }
}
