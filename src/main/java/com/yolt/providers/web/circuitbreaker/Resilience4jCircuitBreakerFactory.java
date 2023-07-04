package com.yolt.providers.web.circuitbreaker;

import com.yolt.providers.web.sitedetails.sites.SiteDetailsService;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadConfig;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import lombok.RequiredArgsConstructor;
import nl.ing.lovebird.clienttokens.ClientToken;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class Resilience4jCircuitBreakerFactory implements ProvidersCircuitBreakerFactory {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final TimeLimiterRegistry timeLimiterRegistry;
    private final ThreadPoolBulkheadRegistry threadPoolBulkheadRegistry;
    private final SiteDetailsService siteDetailsService;

    @Override
    public ProvidersCircuitBreaker create(ClientToken clientToken, UUID siteId, String providerKey, ProvidersCircuitBreakerCommand command, UUID redirectUrlId) {
        String providerName = providerKey.replace("_", "");
        String name = providerName + "-" + redirectUrlId.toString() + "-" + command.getCommand();
        String serviceType = command.getServiceType();
        String config = providerName + "-" + command.getCommand();
        var siteIdWithFallback = siteId == null ? siteDetailsService.getMatchingSiteIdForProviderKey(providerKey)
                .orElse("unknown") : siteId.toString();
        // If the client is non-psd2 licensed, it is leveraging the authentication means from the yolt client group.
        // We want to group all those clients in 1 circuit breaker, since the same authentication means are used.
        // psd2 licensed clients have their own authentication means and also their own circuit breaker.
        String circuitBreakingGroupId = clientToken.isPSD2Licensed() ? clientToken.getClientIdClaim().toString() : clientToken.getClientGroupIdClaim().toString();
        Map<String, String> tags = HashMap.of(
                "provider", providerKey,
                "serviceType", serviceType,
                    "circuitBreakingGroupId", circuitBreakingGroupId,
                "siteId", siteIdWithFallback);

        return new Resilience4jCircuitBreaker(
                circuitBreakerRegistry.circuitBreaker(name, () -> getCircuitBreakerConfig(config), tags),
                timeLimiterRegistry.timeLimiter(name, () -> getTimeLimiterConfig(config), tags),
                threadPoolBulkheadRegistry.bulkhead(name, () -> getThreadPoolBulkheadConfig(config), tags));
    }

    private ThreadPoolBulkheadConfig getThreadPoolBulkheadConfig(String config) {
        return threadPoolBulkheadRegistry.getConfiguration(config).orElse(threadPoolBulkheadRegistry.getDefaultConfig());
    }

    private TimeLimiterConfig getTimeLimiterConfig(String config) {
        return timeLimiterRegistry.getConfiguration(config).orElse(timeLimiterRegistry.getDefaultConfig());
    }

    private CircuitBreakerConfig getCircuitBreakerConfig(String configName) {
        return circuitBreakerRegistry.getConfiguration(configName).orElse(circuitBreakerRegistry.getDefaultConfig());
    }
}
