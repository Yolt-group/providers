package com.yolt.providers.web.service.consenttesting;

import com.yolt.providers.common.providerinterface.Provider;
import com.yolt.providers.web.service.domain.ConsentTestingMessage;
import com.yolt.providers.web.service.domain.ConsentTestingResult;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
class ConsentTestingResultProcessor {

    public static final String CONSENT_TESTING_RESULT_METRIC_NAME = "consent_testing_result";
    public static final String PROVIDER_IDENTIFIER_TAG = "provider_identifier";
    public static final String PROVIDER_SERVICETYPE_TAG = "provider_servicetype";
    public static final String RESULT_TAG = "result";
    public static final String SUCCESS_TAG = "success";
    public static final String FAILURE_TAG = "failure";
    public static final String CLIENT_ID_TAG = "client_id";
    private static final String MESSAGE_TEMPLATE = "CONSENT TESTING - Consent test result for provider: {} {} was: {}";
    private static final String WARN_MESSAGE_URI_GENERATED = "CONSENT TESTING - Consent test result for provider: {} {} was: {}, login page: {}";

    private final MeterRegistry meterRegistry;

    public void processConsentTestingResult(Provider provider, ConsentTestingResult consentTestingResult, AuthenticationMeansReference authMeansReference) {
        ConsentTestingMessage consentTestingMessage = consentTestingResult.getMessage();
        MDC.put(CONSENT_TESTING_RESULT_METRIC_NAME, consentTestingMessage.name());
        switch (consentTestingMessage) {
            case STATUS_200:
            case NOT_GENERATED:
                log.warn(MESSAGE_TEMPLATE, provider.getProviderIdentifier(), provider.getServiceType(), consentTestingMessage.getMessage());
                sendMetrics(provider, FAILURE_TAG, authMeansReference);
                break;
            case GENERATED:
                log.warn(WARN_MESSAGE_URI_GENERATED, provider.getProviderIdentifier(), provider.getServiceType(), consentTestingMessage.getMessage(), consentTestingResult.getConsentPageUri()); // NOSHERIFF we want to see generated url, to be able to test easily in case of error
                sendMetrics(provider, FAILURE_TAG, authMeansReference);
                break;
            case STATUS_200_EMPTY_VALIDITY_RULES:
            case VALIDITY_RULES_CHECKED:
                log.info(MESSAGE_TEMPLATE, provider.getProviderIdentifier(), provider.getServiceType(), consentTestingMessage.getMessage());
                sendMetrics(provider, SUCCESS_TAG, authMeansReference);
                break;
        }
        MDC.remove(CONSENT_TESTING_RESULT_METRIC_NAME);
    }

    private void sendMetrics(Provider provider, String result, AuthenticationMeansReference authMeansReference) {
        /*
        C4PO-7822 When using Counters in combination with an 'increase > 0' function for alerts, note that the first
        time a counter is increased, the alert is not triggered.
        This is because the 'increase' between null and 1 can't be measured,
        that's why we use increment(0) to initialize it
         */
        String providerIdentifier = provider.getProviderIdentifier();
        String serviceType = provider.getServiceType().toString();
        String client;
        if (authMeansReference.getClientId() != null) {
            client = authMeansReference.getClientId().toString();
        }
        else {
            client = authMeansReference.getClientGroupId().toString();
        }
        log.info("Sending counter metrics: {} {} {} {}", client, providerIdentifier, serviceType, result);
        meterRegistry.counter(CONSENT_TESTING_RESULT_METRIC_NAME,
                        PROVIDER_IDENTIFIER_TAG, providerIdentifier,
                        PROVIDER_SERVICETYPE_TAG, serviceType,
                        RESULT_TAG, result,
                        CLIENT_ID_TAG, client)
                .increment(1);
        log.info("Counter metrics sent: {} {} {} {}", client, providerIdentifier, serviceType, result);
    }
}
