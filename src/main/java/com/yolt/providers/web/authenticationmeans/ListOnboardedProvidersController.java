package com.yolt.providers.web.authenticationmeans;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import nl.ing.lovebird.providerdomain.ServiceType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("all-onboarded-providers")
@RequiredArgsConstructor
class ListOnboardedProvidersController {

    private static final Set<ServiceType> EXPECTED_SERVICETYPES = EnumSet.of(ServiceType.AIS, ServiceType.PIS);

    private final ClientGroupRedirectUrlClientConfigurationRepository clientGroupRedirectUrlClientConfigurationRepository;
    private final ClientRedirectUrlClientConfigurationRepository clientRedirectUrlClientConfigurationRepository;
    private final ClientAuthenticationMeansRepository clientAuthenticationMeansRepository;

    /**
     * Either {@link #clientGroupId} or {@link #clientId} is filled, never both.
     * {@link #serviceType} is **always either "AIS" or "PIS".
     * {@link #redirectUrlId} is filled iff {@link #provider} is a non-scraping provider.
     */
    @Value
    static class OnboardedProvider {
        /**
         * Identifier of a ClientGroup, if non-null then {@link #clientId} is null.
         */
        UUID clientGroupId;
        /**
         * Identifier of a Client, if non-null then {@link #clientGroupId} is null.
         */
        UUID clientId;
        /**
         * Identifier of a provider.
         */
        @NonNull String provider;
        /**
         * Which {@link ServiceType} can be used, always either {@link ServiceType#AIS} or {@link ServiceType#PIS}.
         */
        @NonNull ServiceType serviceType;
        /**
         * Identifier of the redirectUrlId that has been onboarded at a bank.  Only null in case {@link #provider}
         */
        UUID redirectUrlId;
    }

    private static OnboardedProvider forClientGroup(@NonNull UUID clientGroupId, @NonNull String provider, @NonNull ServiceType serviceType, @NonNull UUID redirectUrlId) {
        if (!EXPECTED_SERVICETYPES.contains(serviceType)) {
            throw new IllegalArgumentException("Unexpected seviceType " + serviceType);
        }
        return new OnboardedProvider(clientGroupId, null, provider, serviceType, redirectUrlId);
    }

    private static OnboardedProvider forClient(@NonNull UUID clientId, @NonNull String provider, @NonNull ServiceType serviceType, @NonNull UUID redirectUrlId) {
        if (!EXPECTED_SERVICETYPES.contains(serviceType)) {
            throw new IllegalArgumentException("Unexpected seviceType " + serviceType);
        }
        return new OnboardedProvider(null, clientId, provider, serviceType, redirectUrlId);
    }

    private static OnboardedProvider forScraper(@NonNull UUID clientId, @NonNull String provider) {
        return new OnboardedProvider(null, clientId, provider, ServiceType.AIS, null);
    }

    /**
     * Retrieve a snapshot of all onboarded banks for all clients.
     */
    @GetMapping
    public List<OnboardedProvider> listOnboardedBanks() {
        var group = clientGroupRedirectUrlClientConfigurationRepository.list().stream()
                .map(o -> forClientGroup(o.getClientGroupId(), o.getProvider(), o.getServiceType(), o.getRedirectUrlId()));
        var client = clientRedirectUrlClientConfigurationRepository.getAll().stream()
                .map(o -> forClient(o.getClientId(), o.getProvider(), o.getServiceType(), o.getRedirectUrlId()));
        var legacyScrapingStuff = clientAuthenticationMeansRepository.list().stream()
                .map(o -> forScraper(o.getClientId(), o.getProvider()));

        return Stream.of(group, client, legacyScrapingStuff).reduce(Stream.empty(), Stream::concat).collect(Collectors.toList());
    }

}
