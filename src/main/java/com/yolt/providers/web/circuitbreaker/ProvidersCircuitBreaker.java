package com.yolt.providers.web.circuitbreaker;

import java.util.function.Function;
import java.util.function.Supplier;

public interface ProvidersCircuitBreaker {

    default <T> T run(Supplier<T> toRun) {
        return this.run(toRun, throwable -> {
            throw new ProvidersCircuitBreakerException("Service temporarily unavailable.", throwable);
        });
    }

    <T> T run(Supplier<T> toRun, Function<Throwable, T> fallback);
}