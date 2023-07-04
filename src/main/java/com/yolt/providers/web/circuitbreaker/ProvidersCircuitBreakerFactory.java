package com.yolt.providers.web.circuitbreaker;

import nl.ing.lovebird.clienttokens.ClientToken;

import java.util.UUID;

public interface ProvidersCircuitBreakerFactory {

    ProvidersCircuitBreaker create(ClientToken clientToken, UUID siteId, String providerKey, ProvidersCircuitBreakerCommand command, UUID redirectUrlId);
}
