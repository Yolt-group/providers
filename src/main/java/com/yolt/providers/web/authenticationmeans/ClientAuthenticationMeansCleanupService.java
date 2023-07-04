package com.yolt.providers.web.authenticationmeans;

import com.yolt.providers.common.domain.authenticationmeans.BasicAuthenticationMean;
import com.yolt.providers.common.domain.authenticationmeans.TypedAuthenticationMeans;
import com.yolt.providers.common.providerinterface.Provider;
import com.yolt.providers.web.exception.ClientConfigurationValidationException;
import com.yolt.providers.web.service.ProviderFactoryService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.providerdomain.ServiceType;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.yolt.providers.web.service.ProviderService.PROVIDER_MDC_KEY;
import static nl.ing.lovebird.logging.MDCContextCreator.CLIENT_ID_HEADER_NAME;

@Service
@Slf4j
@RequiredArgsConstructor
public class ClientAuthenticationMeansCleanupService {

    private final Clock clock;
    private final ProviderFactoryService providerFactory;
    private final ClientRedirectUrlClientConfigurationRepository clientRedirectUrlClientConfigurationRepository;
    private final ClientAuthenticationMeansEventDispatcherService meansEventDispatcherService;
    private final ClientAuthenticationMeansRepository clientAuthenticationMeansRepository;
    private final AuthenticationMeansEncryptionService authenticationMeansEncryptionService;
    private final AuthenticationMeansMapperService authenticationMeansMapperService;

    public void cleanupProviderAuthenticationMeans(ClientToken clientToken,
                                                   String providerKey,
                                                   Set<UUID> redirectUrlIds,
                                                   Set<ServiceType> serviceTypes,
                                                   boolean dryRun) {
        UUID clientId = clientToken.getClientIdClaim();

        MDC.put(PROVIDER_MDC_KEY, String.valueOf(providerKey));
        MDC.put(CLIENT_ID_HEADER_NAME, String.valueOf(clientId));

        redirectUrlIds.forEach(redirectUrlId -> {
            // We should have only one provider implementation per service type
            Map<ServiceType, Provider> providerByServiceType = providerFactory.getStableProviders(providerKey).stream()
                    .collect(Collectors.toMap(Provider::getServiceType, Function.identity(), (p1, p2) -> p1));

            serviceTypes.forEach(serviceType -> {
                log.info("Starting cleanup of authentication means (dry run: {}) for provider: {}, service type: {}, client id: {}, redirect url id: {}.",
                        dryRun, providerKey, serviceType.name(), clientId, redirectUrlId);
                Provider provider = providerByServiceType.get(serviceType);
                Map<String, BasicAuthenticationMean> currentAuthenticationMeans = getDecryptedAuthenticationMeans(
                        clientId, redirectUrlId, serviceType, providerKey);
                Map<String, BasicAuthenticationMean> cleanedAuthenticationMeans = cleanupAuthenticationMeans(provider, currentAuthenticationMeans);
                if(!dryRun && cleanedAuthenticationMeans.size() != currentAuthenticationMeans.size()) {
                    saveToAuthenticationMeans(clientId, redirectUrlId, serviceType, providerKey, cleanedAuthenticationMeans);
                }
            });
        });
    }

    private Map<String, BasicAuthenticationMean> cleanupAuthenticationMeans(Provider provider,
                                                                            Map<String, BasicAuthenticationMean> authenticationMeans) {
        Map<String, BasicAuthenticationMean> cleanedAuthenticationMeans = new HashMap<>();
        Map<String, TypedAuthenticationMeans> typedAuthenticationMeans = provider.getTypedAuthenticationMeans();
        List<String> keysToDelete = new LinkedList<>();

        authenticationMeans.forEach((key, basicAuthenticationMean) -> {
            if (typedAuthenticationMeans.containsKey(key)) {
                cleanedAuthenticationMeans.put(key, basicAuthenticationMean);
            }
            else {
                keysToDelete.add(key);
            }
        });

        log.info("Authentication means to remove: {}", keysToDelete);

        return cleanedAuthenticationMeans;
    }

    public void saveToAuthenticationMeans(UUID clientId,
                                          UUID redirectUrlId,
                                          ServiceType serviceType,
                                          String providerName,
                                          Map<String, BasicAuthenticationMean> authenticationMeans) {
        ClientRedirectUrlProviderClientConfiguration createdObject = new ClientRedirectUrlProviderClientConfiguration(
                clientId, redirectUrlId, serviceType, providerName, authenticationMeans, Instant.now(clock));
        clientRedirectUrlClientConfigurationRepository.upsert(authenticationMeansMapperService.mapToInternal(createdObject));
        meansEventDispatcherService.publishAuthenticationMeansUpdatedEvent(createdObject);
        log.info("Cleaned provider authentication means were saved.");
    }

    private Map<String, BasicAuthenticationMean> getDecryptedAuthenticationMeans(
            @NonNull final UUID clientId,
            @NonNull final UUID redirectUrlId,
            @NonNull final ServiceType serviceType,
            @NonNull final String provider) {
        Optional<Map<String, BasicAuthenticationMean>> authenticationMeansOpt = clientRedirectUrlClientConfigurationRepository
                .get(clientId, redirectUrlId, serviceType, provider)
                .map(intAuthenticationMeans -> authenticationMeansEncryptionService.decryptAuthenticationMeans(intAuthenticationMeans.getAuthenticationMeans()));

        Supplier<Map<String, BasicAuthenticationMean>> authMeansFallback = () -> clientAuthenticationMeansRepository
                .get(clientId, provider)
                .map(clientAuthMeans -> authenticationMeansEncryptionService.decryptAuthenticationMeans(clientAuthMeans.getAuthenticationMeans()))
                .orElseThrow(() -> new ClientConfigurationValidationException(clientId, redirectUrlId, serviceType, provider));

        return authenticationMeansOpt.orElseGet(authMeansFallback);
    }
}
