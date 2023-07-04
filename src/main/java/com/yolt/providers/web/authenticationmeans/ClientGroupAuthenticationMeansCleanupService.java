package com.yolt.providers.web.authenticationmeans;

import com.yolt.providers.common.domain.authenticationmeans.BasicAuthenticationMean;
import com.yolt.providers.common.domain.authenticationmeans.TypedAuthenticationMeans;
import com.yolt.providers.common.providerinterface.Provider;
import com.yolt.providers.web.service.ProviderFactoryService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.clienttokens.ClientGroupToken;
import nl.ing.lovebird.providerdomain.ServiceType;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.yolt.providers.web.service.ProviderService.PROVIDER_MDC_KEY;

@Service
@Slf4j
@RequiredArgsConstructor
public class ClientGroupAuthenticationMeansCleanupService {

    private final Clock clock;
    private final ProviderFactoryService providerFactory;
    private final ClientGroupRedirectUrlClientConfigurationRepository clientGroupRedirectUrlClientConfigurationRepository;
    private final ClientAuthenticationMeansEventDispatcherService meansEventDispatcherService;
    private final AuthenticationMeansEncryptionService authenticationMeansEncryptionService;
    private final AuthenticationMeansMapperService authenticationMeansMapperService;


    public void saveToAuthenticationMeans(UUID clientGroupId,
                                          UUID redirectUrlId,
                                          ServiceType serviceType,
                                          String providerName,
                                          Map<String, BasicAuthenticationMean> authenticationMeans) {
        ClientGroupRedirectUrlProviderClientConfiguration createdObject = new ClientGroupRedirectUrlProviderClientConfiguration(
                clientGroupId, redirectUrlId, serviceType, providerName, authenticationMeans, Instant.now(clock));
        clientGroupRedirectUrlClientConfigurationRepository.upsert(authenticationMeansMapperService.mapToInternal(createdObject));
        meansEventDispatcherService.publishAuthenticationMeansUpdatedEvent(createdObject);
        log.info("Cleaned provider authentication means were saved.");
    }

    public void cleanupProviderAuthenticationMeans(ClientGroupToken clientGroupToken,
                                                   ProviderAuthenticationMeansCleanupDTO providerAuthenticationMeansCleanupDTO) {
        UUID clientGroupId = clientGroupToken.getClientGroupIdClaim();
        String providerKey = providerAuthenticationMeansCleanupDTO.getProvider();
        boolean dryRun = providerAuthenticationMeansCleanupDTO.isDryRun();

        MDC.put(PROVIDER_MDC_KEY, String.valueOf(providerKey));

        providerAuthenticationMeansCleanupDTO.getRedirectUrlIds().forEach(redirectUrlId -> {
            // We should have only one provider implementation per service type
            Map<ServiceType, Provider> providerByServiceType = providerFactory.getStableProviders(providerKey).stream()
                    .collect(Collectors.toMap(Provider::getServiceType, Function.identity(), (p1, p2) -> p1));

            providerAuthenticationMeansCleanupDTO.getServiceTypes().forEach(serviceType -> {
                log.info("Starting cleanup of authentication means (dry run: {}) for provider: {}, service type: {}, client group id: {}, redirect url id: {}.",
                        dryRun, providerKey, serviceType.name(), clientGroupId, redirectUrlId);
                Provider provider = providerByServiceType.get(serviceType);
                Map<String, BasicAuthenticationMean> currentAuthenticationMeans = getDecryptedAuthenticationMeans(
                        clientGroupId, redirectUrlId, serviceType, providerKey);
                Map<String, BasicAuthenticationMean> cleanedAuthenticationMeans = cleanupAuthenticationMeans(provider, currentAuthenticationMeans);
                if (!dryRun && cleanedAuthenticationMeans.size() != currentAuthenticationMeans.size()) {
                    saveToAuthenticationMeans(clientGroupId, redirectUrlId, serviceType, providerKey, cleanedAuthenticationMeans);
                }
            });
        });
    }

    private Map<String, BasicAuthenticationMean> getDecryptedAuthenticationMeans(
            @NonNull final UUID clientId,
            @NonNull final UUID redirectUrlId,
            @NonNull final ServiceType serviceType,
            @NonNull final String provider) {
        Optional<Map<String, BasicAuthenticationMean>> authenticationMeansOpt = clientGroupRedirectUrlClientConfigurationRepository
                .get(clientId, redirectUrlId, serviceType, provider)
                .map(intAuthenticationMeans -> authenticationMeansEncryptionService.decryptAuthenticationMeans(intAuthenticationMeans.getAuthenticationMeans()));

        return authenticationMeansOpt.orElseThrow(() -> new RuntimeException("No auth means for group"));
    }

    private Map<String, BasicAuthenticationMean> cleanupAuthenticationMeans(Provider provider,
                                                                            Map<String, BasicAuthenticationMean> authenticationMeans) {
        Map<String, BasicAuthenticationMean> cleanedAuthenticationMeans = new HashMap<>();
        Map<String, TypedAuthenticationMeans> typedAuthenticationMeans = provider.getTypedAuthenticationMeans();
        List<String> keysToDelete = new LinkedList<>();

        authenticationMeans.forEach((key, basicAuthenticationMean) -> {
            if (typedAuthenticationMeans.containsKey(key)) {
                cleanedAuthenticationMeans.put(key, basicAuthenticationMean);
            } else {
                keysToDelete.add(key);
            }
        });

        log.info("Authentication means to remove: {}", keysToDelete);

        return cleanedAuthenticationMeans;
    }
}
