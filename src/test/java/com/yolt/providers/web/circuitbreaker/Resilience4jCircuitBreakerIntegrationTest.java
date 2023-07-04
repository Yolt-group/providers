package com.yolt.providers.web.circuitbreaker;

import com.yolt.providers.web.configuration.IntegrationTestContext;
import io.micrometer.core.instrument.MeterRegistry;
import nl.ing.lovebird.clienttokens.ClientToken;
import org.jose4j.jwt.JwtClaims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTestContext
public class Resilience4jCircuitBreakerIntegrationTest {

    @Autowired
    private Resilience4jCircuitBreakerFactory factory;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    public void shouldCallSupplier() {
        // given
        JwtClaims jwtClaims = new JwtClaims();
        jwtClaims.setClaim("client-id", UUID.randomUUID().toString());
        jwtClaims.setClaim("psd2-licensed", true);
        ProvidersCircuitBreaker circuitBreaker = factory.create(new ClientToken(null, jwtClaims), UUID.randomUUID(), "fake-provider-key", ProvidersCircuitBreakerCommand.PIS_CONFIRM_PAYMENT, UUID.randomUUID());

        // when
        Boolean wasExecuted = circuitBreaker.run(() -> Boolean.TRUE);

        // then
        assertThat(wasExecuted).isTrue();
    }

    @Test
    public void shouldCallFallback() {
        // given
        JwtClaims jwtClaims = new JwtClaims();
        jwtClaims.setClaim("client-id", UUID.randomUUID().toString());
        jwtClaims.setClaim("psd2-licensed", true);
        ProvidersCircuitBreaker circuitBreaker = factory.create(new ClientToken(null, jwtClaims), UUID.randomUUID(), "fake-provider-key", ProvidersCircuitBreakerCommand.PIS_CONFIRM_PAYMENT, UUID.randomUUID());
        // when
        Boolean wasExecuted = circuitBreaker.run(
                () -> {
                    throw new IllegalArgumentException();
                },
                throwable -> Boolean.TRUE);

        // then
        assertThat(wasExecuted).isTrue();
    }

    @Test
    public void shouldHaveASeparateCircuitBreakerForPsd2LicensedClients_and_OneCircuitBreakerForNonLicensedClients_and_publishMetrics() {

        meterRegistry.clear();

        // Given pds2 licensed
        UUID clientIdLicensed = UUID.randomUUID();
        JwtClaims jwtClaims = new JwtClaims();
        jwtClaims.setClaim("client-id", clientIdLicensed.toString());
        jwtClaims.setClaim("psd2-licensed", true);
        ProvidersCircuitBreaker circuitBreakerLicensed = factory.create(new ClientToken(null, jwtClaims), UUID.randomUUID(), "test-circuitbreaker-provider", ProvidersCircuitBreakerCommand.PIS_CONFIRM_PAYMENT, UUID.randomUUID());

        // Given non licensed
        UUID clientGroupIdNonLicensed = UUID.randomUUID();
        JwtClaims jwtClaimsNonLicensed = new JwtClaims();
        jwtClaimsNonLicensed.setClaim("client-id", UUID.randomUUID().toString());
        jwtClaimsNonLicensed.setClaim("psd2-licensed", false);
        jwtClaimsNonLicensed.setClaim("client-group-id", clientGroupIdNonLicensed.toString());
        ProvidersCircuitBreaker circuitBreakerNonLicensed = factory.create(new ClientToken(null, jwtClaimsNonLicensed), UUID.randomUUID(), "test-circuitbreaker-provider", ProvidersCircuitBreakerCommand.PIS_CONFIRM_PAYMENT, UUID.randomUUID());

        circuitBreakerLicensed.run(() -> Void.class);
        circuitBreakerNonLicensed.run(() -> Void.class);

        assertThat(meterRegistry.get("resilience4j.circuitbreaker.calls")
                .tag("kind", "successful")
                .tag("circuitBreakingGroupId", clientGroupIdNonLicensed.toString())
                .timer().count()).isEqualTo(1);

        assertThat(meterRegistry.get("resilience4j.circuitbreaker.calls")
                .tag("kind", "successful")
                .tag("circuitBreakingGroupId", clientIdLicensed.toString())
                .timer().count()).isEqualTo(1);
    }
}