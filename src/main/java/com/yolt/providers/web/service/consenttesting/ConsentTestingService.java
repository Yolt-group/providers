package com.yolt.providers.web.service.consenttesting;

import com.yolt.providers.common.providerinterface.FormDataProvider;
import com.yolt.providers.common.providerinterface.Provider;
import com.yolt.providers.web.authenticationmeans.ClientAuthenticationMeansService;
import com.yolt.providers.web.service.ProviderFactoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.providerdomain.ServiceType;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.yolt.providers.web.configuration.ApplicationConfiguration.ASYNC_PROVIDER_CONSENT_TESTER_EXECUTOR;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsentTestingService {

    private final ClientAuthenticationMeansService clientAuthenticationMeansService;
    private final ProviderConsentTester providerConsentTester;
    private final ConsentTestingProperties consentTestingProperties;
    private final ProviderFactoryService providerFactoryService;

    @Async(ASYNC_PROVIDER_CONSENT_TESTER_EXECUTOR)
    public void invokeConsentTesting(AuthenticationMeansReference authMeansReference,
                                     ClientToken clientToken,
                                     ServiceType serviceType,
                                     String baseRedirectUrl) {
        List<Provider> eligibleProviders = getConsentTestableProviders(authMeansReference, serviceType);
        log.info("Consent testing started for {} providers", eligibleProviders.size());
        eligibleProviders.forEach(provider -> providerConsentTester.testProviderConsent(provider, clientToken, authMeansReference, baseRedirectUrl));
    }

    public List<Provider> getConsentTestableProviders(AuthenticationMeansReference authMeansReference, ServiceType serviceType) {
        Set<String> registeredProvidersOfServiceType;
        if (authMeansReference.getClientGroupId() != null) {
            registeredProvidersOfServiceType = clientAuthenticationMeansService.getRegisteredProvidersForGroup(
                    authMeansReference.getClientGroupId(), authMeansReference.getRedirectUrlId(), serviceType);
        }
        else {
            registeredProvidersOfServiceType = clientAuthenticationMeansService.getRegisteredProviders(
                    authMeansReference.getClientId(), authMeansReference.getRedirectUrlId(), serviceType);
        }

        List<Provider> stableProvidersOfServiceType = providerFactoryService.getAllStableProviders().stream()
                .filter(this::isNotFormDataProvider)
                .filter(it -> isOfServiceType(it, serviceType))
                .collect(Collectors.toList());

        return getEligibleProviders(
                stableProvidersOfServiceType,
                registeredProvidersOfServiceType,
                consentTestingProperties.getBlacklistedProviders(serviceType));
    }

    private List<Provider> getEligibleProviders(List<Provider> stableProviders,
                                                Set<String> registeredProvidersIdentifier,
                                                List<String> blacklistedProvidersIdentifier) {
        return stableProviders.stream()
                .filter(provider -> isRegisteredProvider(provider, registeredProvidersIdentifier))
                .filter(provider -> isNotBlacklisted(provider, blacklistedProvidersIdentifier))
                .sorted(Comparator.comparing(Provider::getProviderIdentifier))
                .collect(Collectors.toList());
    }

    private boolean isNotBlacklisted(Provider provider, List<String> blacklistedAisProviders) {
        return !blacklistedAisProviders.contains(provider.getProviderIdentifier());
    }

    private boolean isRegisteredProvider(Provider provider, Set<String> registeredProviders) {
        return registeredProviders.contains(provider.getProviderIdentifier());
    }

    private boolean isNotFormDataProvider(Provider provider) {
        return !(provider instanceof FormDataProvider);
    }

    private boolean isOfServiceType(Provider provider, ServiceType serviceType) {
        return provider.getServiceType() == serviceType;
    }
}
