package com.yolt.providers.web.service.consenttesting;

import com.yolt.providers.web.authenticationmeans.ClientGroupRedirectUrlClientConfigurationRepository;
import com.yolt.providers.web.authenticationmeans.ClientRedirectUrlClientConfigurationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.providerdomain.ServiceType;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import static com.yolt.providers.web.service.consenttesting.ConsentTestingResultProcessor.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConsentTestingMetricsInitializer {

    private final MeterRegistry meterRegistry;
    private final ClientRedirectUrlClientConfigurationRepository clientRedirectUrlClientConfigurationRepository;
    private final ClientGroupRedirectUrlClientConfigurationRepository clientGroupRedirectUrlClientConfigurationRepository;
    private final ConsentTestingService consentTestingService;

    @EventListener({ApplicationReadyEvent.class})
    public void initializeConsentTestingCounters() {
        //Initialize for clients
        clientRedirectUrlClientConfigurationRepository.getAll()
                .stream()
                .map(cc -> new AuthenticationMeansReference(cc.getClientId(), null, cc.getRedirectUrlId()))
                .distinct()
                .forEach(this::retrieveProvidersAndInitialize);

        //Initialize for client groups
        clientGroupRedirectUrlClientConfigurationRepository.list()
                .stream()
                .map(cc -> new AuthenticationMeansReference(null, cc.getClientGroupId(), cc.getRedirectUrlId()))
                .distinct()
                .forEach(this::retrieveProvidersAndInitialize);
    }

    private void retrieveProvidersAndInitialize(AuthenticationMeansReference authMeansReference) {
        consentTestingService.getConsentTestableProviders(authMeansReference, ServiceType.AIS)
                .forEach(provider -> initializeCounter(provider.getProviderIdentifier(), authMeansReference, provider.getServiceType().toString()));
    }

    private void initializeCounter(String providerIdentifier, AuthenticationMeansReference authMeansReference, String serviceType) {
        String clientId;
        if (authMeansReference.getClientId() != null) {
            clientId = authMeansReference.getClientId().toString();
        }
        else {
            clientId = authMeansReference.getClientGroupId().toString();
        }

        meterRegistry.counter(CONSENT_TESTING_RESULT_METRIC_NAME,
                        PROVIDER_IDENTIFIER_TAG, providerIdentifier,
                        PROVIDER_SERVICETYPE_TAG, serviceType,
                        RESULT_TAG, FAILURE_TAG,
                        CLIENT_ID_TAG, clientId)
                .increment(0);
        log.info("Counter initialized {} {} {}", clientId, providerIdentifier, serviceType);
    }
}
