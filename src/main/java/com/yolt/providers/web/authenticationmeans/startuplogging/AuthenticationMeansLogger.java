package com.yolt.providers.web.authenticationmeans.startuplogging;

import com.yolt.providers.common.providerinterface.Provider;
import com.yolt.providers.web.authenticationmeans.ClientAuthenticationMeansService;
import com.yolt.providers.web.authenticationmeans.startuplogging.AuthenticationMeansLoggingProperties.AuthMeansPropertyReference;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.providerdomain.ServiceType;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import org.slf4j.MDC;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@AllArgsConstructor
public class AuthenticationMeansLogger {

    private static final String TRACE_ID = "traceId";
    private final List<Provider> providers;
    private final AuthenticationMeansLoggingProperties properties;
    private final ClientAuthenticationMeansService clientAuthenticationMeansService;

    @EventListener({ApplicationReadyEvent.class})
    public void triggerLogging() {
        MDC.put(TRACE_ID, UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        Set<String> providerIds = properties.getProviderIdsContainingDetails();
        Set<Provider> providersToLogAuthMeansFor = providers.stream()
                .filter(provider -> providerIds.contains(provider.getProviderIdentifier()))
                .collect(Collectors.toSet());
        List<ProviderReference> providerReferences = convertToProviderReferences(providersToLogAuthMeansFor, properties);
        for (ProviderReference providerReference : providerReferences) {
            callAuthMeansRetrieval(clientAuthenticationMeansService, providerReference);
        }
    }

    private void callAuthMeansRetrieval(final ClientAuthenticationMeansService clientAuthenticationMeansService, final ProviderReference providerReference) {
        String providerId = providerReference.getProviderId();
        AuthMeansPropertyReference authenticationMeansReference = providerReference.getAuthenticationMeansReference();
        UUID clientId = authenticationMeansReference.getClientId();
        UUID clientGroupId = authenticationMeansReference.getClientGroupId();
        UUID redirectUrlId = authenticationMeansReference.getRedirectUrlId();
        ServiceType serviceType = providerReference.serviceType;
        try {
            clientAuthenticationMeansService.acquireAuthenticationMeans(
                    providerId,
                    serviceType,
                    new AuthenticationMeansReference(
                            clientId,
                            clientGroupId,
                            redirectUrlId));
        } catch (Exception e) {
            log.warn("Failed to retrieve authmeans", e);
        }
    }

    private List<ProviderReference> convertToProviderReferences(
            final Set<Provider> providersToLogAuthMeansFor, AuthenticationMeansLoggingProperties properties) {
        List<ProviderReference> providerReferences = new ArrayList<>();
        for (Provider provider : providersToLogAuthMeansFor) {
            String providerIdentifier = provider.getProviderIdentifier();
            ServiceType serviceType = provider.getServiceType();
            List<AuthMeansPropertyReference> authMeanReferences = properties.getProviderReferences(providerIdentifier);
            for (AuthMeansPropertyReference authenticationMeansReference : authMeanReferences) {
                providerReferences.add(new ProviderReference(
                        providerIdentifier,
                        authenticationMeansReference,
                        serviceType)
                );
            }
        }
        return providerReferences;
    }

    @RequiredArgsConstructor
    @Getter
    @Data
    private class ProviderReference {

        private final String providerId;
        private final AuthMeansPropertyReference authenticationMeansReference;
        private final ServiceType serviceType;
    }
}
