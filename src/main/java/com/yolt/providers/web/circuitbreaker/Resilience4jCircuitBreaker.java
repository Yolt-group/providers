package com.yolt.providers.web.circuitbreaker;

import io.github.resilience4j.bulkhead.ThreadPoolBulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.timelimiter.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;

@RequiredArgsConstructor
@Slf4j
public class Resilience4jCircuitBreaker implements ProvidersCircuitBreaker {

    private final CircuitBreaker circuitBreaker;
    private final TimeLimiter timeLimiter;
    private final ThreadPoolBulkhead threadPoolBulkhead;

    private static final ScheduledExecutorService EXECUTOR_SERVICE = Executors.newScheduledThreadPool(1);

    @Override
    public <T> T run(Supplier<T> toRun, Function<Throwable, T> fallback) {
        return Decorators.ofSupplier(toRun)
                .withThreadPoolBulkhead(threadPoolBulkhead)
                .withTimeLimiter(timeLimiter, EXECUTOR_SERVICE)
                .withCircuitBreaker(circuitBreaker)
                .withFallback(fallback)
                .get()
                .toCompletableFuture()
                    .join();
    }

}
