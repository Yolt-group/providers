package com.yolt.providers.web.authenticationmeans;

import com.yolt.providers.common.ais.url.UrlAutoOnboardingRequest;
import com.yolt.providers.common.cryptography.RestTemplateManager;
import com.yolt.providers.common.domain.AuthenticationMeans;
import com.yolt.providers.common.domain.authenticationmeans.AuthenticationMeanType;
import com.yolt.providers.common.domain.authenticationmeans.BasicAuthenticationMean;
import com.yolt.providers.common.domain.authenticationmeans.TypedAuthenticationMeans;
import com.yolt.providers.common.domain.authenticationmeans.keymaterial.KeyRequirements;
import com.yolt.providers.common.exception.AuthenticationMeanValidationException;
import com.yolt.providers.common.exception.UnrecognizableAuthenticationMeanKey;
import com.yolt.providers.common.providerinterface.AutoOnboardingProvider;
import com.yolt.providers.common.providerinterface.Provider;
import com.yolt.providers.common.versioning.ProviderVersion;
import com.yolt.providers.web.clientredirecturl.ClientRedirectUrlRepository;
import com.yolt.providers.web.cryptography.signing.JcaSigner;
import com.yolt.providers.web.cryptography.signing.JcaSignerFactory;
import com.yolt.providers.web.cryptography.transport.MutualTLSRestTemplateManagerCache;
import com.yolt.providers.web.exception.ClientConfigurationValidationException;
import com.yolt.providers.web.exception.ProviderNotFoundException;
import com.yolt.providers.web.service.ProviderFactoryService;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.clienttokens.ClientGroupToken;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.providerdomain.ServiceType;
import nl.ing.lovebird.providerdomain.TokenScope;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.yolt.providers.web.service.configuration.VersionType.STABLE;
import static java.util.Collections.unmodifiableMap;
import static nl.ing.lovebird.logging.MDCContextCreator.CLIENT_ID_HEADER_NAME;

@Slf4j
@Service
public class ClientAuthenticationMeansService {

    static final String ASTRIX_PLACEHOLDER = "**********";
    private static final String PROVIDER_MDC_KEY = "provider";

    private final Clock clock;
    private final ClientRedirectUrlClientConfigurationRepository clientRedirectUrlClientConfigurationRepository;
    private final ClientAuthenticationMeansRepository clientAuthenticationMeansRepository;
    private final ClientGroupRedirectUrlClientConfigurationRepository clientGroupRedirectUrlClientConfigurationRepository;
    private final ClientRedirectUrlRepository clientRedirectUrlRepository;
    private final ClientAuthenticationMeansEventDispatcherService meansEventDispatcherService;
    private final ProviderFactoryService providerFactory;
    private final String ytsGroupRedirecturl;
    private final JcaSignerFactory jcaSignerFactory;
    private final MutualTLSRestTemplateManagerCache restTemplateManagerCache;
    private final ClientAuthenticationMeansCertificateVerifierService clientAuthenticationMeansCertificateVerifierService;
    private final AuthenticationMeansEncryptionService authenticationMeansEncryptionService;
    private final AuthenticationMeansMapperService authenticationMeansMapperService;

    public ClientAuthenticationMeansService(final Clock clock,
                                            final ProviderFactoryService providerFactory,
                                            final ClientRedirectUrlClientConfigurationRepository clientRedirectUrlClientConfigurationRepository,
                                            final ClientAuthenticationMeansRepository clientAuthenticationMeansRepository,
                                            final ClientGroupRedirectUrlClientConfigurationRepository clientGroupRedirectUrlClientConfigurationRepository,
                                            final ClientRedirectUrlRepository clientRedirectUrlRepository,
                                            final ClientAuthenticationMeansEventDispatcherService meansEventDispatcherService,
                                            @Value("${yolt.ytsGroup.redirectUrl}") final String ytsGroupRedirecturl,
                                            final JcaSignerFactory jcaSignerFactory,
                                            final MutualTLSRestTemplateManagerCache restTemplateManagerCache,
                                            final ClientAuthenticationMeansCertificateVerifierService clientAuthenticationMeansCertificateVerifierService,
                                            final AuthenticationMeansEncryptionService authenticationMeansEncryptionService,
                                            final AuthenticationMeansMapperService authenticationMeansMapperService) {
        this.clock = clock;
        this.providerFactory = providerFactory;
        this.clientRedirectUrlClientConfigurationRepository = clientRedirectUrlClientConfigurationRepository;
        this.clientAuthenticationMeansRepository = clientAuthenticationMeansRepository;
        this.clientGroupRedirectUrlClientConfigurationRepository = clientGroupRedirectUrlClientConfigurationRepository;
        this.clientRedirectUrlRepository = clientRedirectUrlRepository;
        this.meansEventDispatcherService = meansEventDispatcherService;
        this.ytsGroupRedirecturl = ytsGroupRedirecturl;
        this.jcaSignerFactory = jcaSignerFactory;
        this.restTemplateManagerCache = restTemplateManagerCache;
        this.clientAuthenticationMeansCertificateVerifierService = clientAuthenticationMeansCertificateVerifierService;
        this.authenticationMeansEncryptionService = authenticationMeansEncryptionService;
        this.authenticationMeansMapperService = authenticationMeansMapperService;
    }

    /**
     * Only of internal usage, as it contains decrypted authentication means.
     */
    public Map<String, BasicAuthenticationMean> acquireAuthenticationMeansForScraping(
            final String provider,
            final UUID clientId
    ) {
        MDC.put(PROVIDER_MDC_KEY, String.valueOf(provider));
        MDC.put(CLIENT_ID_HEADER_NAME, String.valueOf(clientId));

        // This wasn't converted yet to take into account serviceType.
        Optional<Map<String, BasicAuthenticationMean>> decryptedAuthenticationMeans = getDecryptedAuthenticationMeansWithoutException(clientId, provider);
        decryptedAuthenticationMeans.ifPresent(authenticationMeanMap -> clientAuthenticationMeansCertificateVerifierService.checkExpirationOfCertificate(provider, ServiceType.AIS, new AuthenticationMeansReference(clientId, null), unmodifiableMap(authenticationMeanMap)));
        return decryptedAuthenticationMeans.orElse(Collections.emptyMap());
    }

    /**
     * Only of internal usage, as it contains decrypted authentication means.
     */
    public Map<String, BasicAuthenticationMean> acquireAuthenticationMeans(
            final String provider,
            final ServiceType serviceType,
            final AuthenticationMeansReference authenticationMeansReference) {
        UUID clientId = authenticationMeansReference.getClientId();

        MDC.put(PROVIDER_MDC_KEY, String.valueOf(provider));
        MDC.put(CLIENT_ID_HEADER_NAME, String.valueOf(clientId));

        if (authenticationMeansReference.getClientGroupId() != null) {
            Map<String, BasicAuthenticationMean> decryptedGroupAuthenticationMeans = getDecryptedGroupAuthenticationMeans(authenticationMeansReference.getClientGroupId(),
                    authenticationMeansReference.getRedirectUrlId(),
                    serviceType,
                    provider);
            clientAuthenticationMeansCertificateVerifierService.checkExpirationOfCertificate(provider, serviceType, authenticationMeansReference, unmodifiableMap(decryptedGroupAuthenticationMeans));
            return decryptedGroupAuthenticationMeans;
        }

        Map<String, BasicAuthenticationMean> decryptedAuthenticationMeans = getDecryptedAuthenticationMeans(authenticationMeansReference.getClientId(),
                authenticationMeansReference.getRedirectUrlId(),
                serviceType,
                provider);
        clientAuthenticationMeansCertificateVerifierService.checkExpirationOfCertificate(provider, serviceType, authenticationMeansReference, unmodifiableMap(decryptedAuthenticationMeans));
        return decryptedAuthenticationMeans;
    }

    private Map<String, BasicAuthenticationMean> getDecryptedAuthenticationMeans(
            @NonNull final UUID clientId,
            @NonNull final UUID redirectUrlId,
            @NonNull final ServiceType serviceType,
            @NonNull final String provider) {
        return getDecryptedAuthenticationMeansWithoutException(clientId, redirectUrlId, serviceType, provider)
                .orElseThrow(() -> new ClientConfigurationValidationException(clientId, redirectUrlId, serviceType, provider)
                );
    }

    private Optional<Map<String, BasicAuthenticationMean>> getDecryptedAuthenticationMeansWithoutException(
            @NonNull final UUID clientId,
            @NonNull final UUID redirectUrlId,
            @NonNull final ServiceType serviceType,
            @NonNull final String provider) {
        Optional<Map<String, BasicAuthenticationMean>> authenticationMeans = clientRedirectUrlClientConfigurationRepository
                .get(clientId, redirectUrlId, serviceType, provider)
                .map(internalClientRedirectUrlAuthenticationMeans -> authenticationMeansEncryptionService.decryptAuthenticationMeans(internalClientRedirectUrlAuthenticationMeans.getAuthenticationMeans()));

        if (authenticationMeans.isPresent()) {
            return authenticationMeans;
        } else {
            return clientAuthenticationMeansRepository.get(clientId, provider)
                    .map(clientAuthMeans -> authenticationMeansEncryptionService.decryptAuthenticationMeans(clientAuthMeans.getAuthenticationMeans()));
        }
    }

    private Optional<Map<String, BasicAuthenticationMean>> getDecryptedAuthenticationMeansWithoutException(
            @NonNull final UUID clientId,
            @NonNull final String provider) {
        return clientAuthenticationMeansRepository.get(clientId, provider)
                .map(clientAuthMeans -> authenticationMeansEncryptionService.decryptAuthenticationMeans(clientAuthMeans.getAuthenticationMeans()));
    }

    private Map<String, BasicAuthenticationMean> getDecryptedGroupAuthenticationMeans(
            @NonNull final UUID clientGroupId,
            @NonNull final UUID redirectUrlId,
            @NonNull final ServiceType serviceType,
            @NonNull final String provider) {
        return getDecryptedGroupAuthenticationMeansWithoutException(clientGroupId, redirectUrlId, serviceType, provider)
                .orElseThrow(() -> new ClientConfigurationValidationException(clientGroupId, redirectUrlId, serviceType, provider)
                );
    }

    private Optional<Map<String, BasicAuthenticationMean>> getDecryptedGroupAuthenticationMeansWithoutException(
            @NonNull final UUID clientGroupId,
            @NonNull final UUID redirectUrlId,
            @NonNull final ServiceType serviceType,
            @NonNull final String provider) {
        return clientGroupRedirectUrlClientConfigurationRepository
                .get(clientGroupId, redirectUrlId, serviceType, provider)
                .map(internalClientRedirectUrlAuthenticationMeans -> authenticationMeansEncryptionService.decryptAuthenticationMeans(internalClientRedirectUrlAuthenticationMeans.getAuthenticationMeans()));
    }

    void delete(final ClientToken clientToken, final UUID clientId, final UUID redirectUrlId, final ServiceType serviceType, final String provider) {
        MDC.put(PROVIDER_MDC_KEY, String.valueOf(provider));
        MDC.put(CLIENT_ID_HEADER_NAME, String.valueOf(clientId));

        // We verify the state, but not break the flow, as delete should be idempotent operation
        if (clientRedirectUrlClientConfigurationRepository.get(clientId, redirectUrlId, serviceType, provider).isEmpty()) {
            log.warn("Attempting to delete client configuration that is NOT present for given client. " +
                     "clientId: {}, redirectUrlId: {}, serviceType: {}", clientId, redirectUrlId, serviceType);
        }
        attemptAutoOnboardingDeletion(clientToken, clientId, redirectUrlId, serviceType, provider);

        clientRedirectUrlClientConfigurationRepository.delete(clientId, redirectUrlId, serviceType, provider);
        meansEventDispatcherService.publishAuthenticationMeansDeletedEvent(clientId, redirectUrlId, serviceType, provider);
    }

    private void attemptAutoOnboardingDeletion(final ClientToken clientToken, final UUID clientId, final UUID redirectUrlId, final ServiceType serviceType, final String provider) {
        // If provider supports auto-onboarding, try to remove configuration at
        // the bank before deleting our own administration.
        try {
            AutoOnboardingProvider autoOnboardingProvider = providerFactory.getProvider(provider, AutoOnboardingProvider.class, null, STABLE);
            if (!(autoOnboardingProvider instanceof Provider)) {
                throw new IllegalStateException(String.format("Retrieved class for %s is not Provider instance.", provider));
            }
            attemptDeletionAtProvider(clientToken, clientId, redirectUrlId, serviceType, provider, autoOnboardingProvider, ((Provider) autoOnboardingProvider).getVersion());
        } catch (ProviderNotFoundException e) {
            //Implementation for auto onboarding was not found - skipping auto onboarding
        }
    }

    private void attemptDeletionAtProvider(final ClientToken clientToken,
                                           final UUID clientId,
                                           final UUID redirectUrlId,
                                           final ServiceType serviceType,
                                           final String provider,
                                           final AutoOnboardingProvider autoOnboardingProvider,
                                           final ProviderVersion providerVersion) {
        clientRedirectUrlRepository
                .get(clientToken.getClientIdClaim(), redirectUrlId)
                .flatMap(clientRedirectUrl -> getDecryptedAuthenticationMeansWithoutException(clientId, redirectUrlId, serviceType, provider)
                        .map(authenticationMeans -> {
                            final RestTemplateManager restTemplateManager = restTemplateManagerCache.getForClientProvider(
                                    clientToken, serviceType, provider, false, providerVersion);
                            final JcaSigner signer = jcaSignerFactory.getForClientToken(clientToken);
                            return new UrlAutoOnboardingRequest(
                                    authenticationMeans,
                                    restTemplateManager,
                                    signer,
                                    clientRedirectUrl.getUrl(),
                                    null,
                                    null
                            );
                        }))
                .ifPresent(autoOnboardingProvider::removeAutoConfiguration);
    }

    void delete(final ClientGroupToken clientGroupToken, final UUID redirectUrlId, final ServiceType serviceType, final String provider) {
        MDC.put(PROVIDER_MDC_KEY, String.valueOf(provider));
        UUID clientGroupId = clientGroupToken.getClientGroupIdClaim();

        // We verify the state, but not break the flow, as delete should be idempotent operation
        if (clientGroupRedirectUrlClientConfigurationRepository.get(clientGroupId, redirectUrlId, serviceType, provider).isEmpty()) {
            log.warn("Attempting to delete client configuration that is NOT present for given client-group. " +
                     "clientGroupId: {}, redirectUrlId: {}, serviceType: {}", clientGroupId, redirectUrlId, serviceType);
        }

        attemptAutoOnboardingDeletion(clientGroupToken, redirectUrlId, serviceType, provider);

        clientGroupRedirectUrlClientConfigurationRepository.delete(clientGroupId, redirectUrlId, serviceType, provider);
        meansEventDispatcherService.publishAuthenticationMeansDeletedGroupEvent(clientGroupId, redirectUrlId, serviceType, provider);
    }

    private void attemptAutoOnboardingDeletion(final ClientGroupToken clientToken, final UUID redirectUrlId, final ServiceType serviceType, final String provider) {
        // If provider supports auto-onboarding, try to remove configuration at
        // the bank before deleting our own administration.
        try {
            AutoOnboardingProvider autoOnboardingProvider = providerFactory.getProvider(provider, AutoOnboardingProvider.class, null, STABLE);
            if (!(autoOnboardingProvider instanceof Provider)) {
                throw new IllegalStateException(String.format("Retrieved class for %s is not Provider instance.", provider));
            }
            attemptDeletionAtProvider(clientToken, redirectUrlId, serviceType, provider, autoOnboardingProvider, ((Provider) autoOnboardingProvider).getVersion());
        } catch (ProviderNotFoundException e) {
            //Implementation for auto onboarding was not found - skipping auto onboarding
        }
    }

    private void attemptDeletionAtProvider(final ClientGroupToken clientGroupToken,
                                           final UUID redirectUrlId,
                                           final ServiceType serviceType,
                                           final String provider,
                                           final AutoOnboardingProvider autoOnboardingProvider,
                                           ProviderVersion providerVersion) {

        UUID clientGroupId = clientGroupToken.getClientGroupIdClaim();
        getDecryptedGroupAuthenticationMeansWithoutException(clientGroupId, redirectUrlId, serviceType, provider)
                .map(authenticationMeans -> {
                    final RestTemplateManager restTemplateManager = restTemplateManagerCache.getForClientGroupProvider(
                            clientGroupToken, serviceType, provider, false, providerVersion);
                    final JcaSigner signer = jcaSignerFactory.getForClientGroupToken(clientGroupToken);
                    return new UrlAutoOnboardingRequest(
                            authenticationMeans,
                            restTemplateManager,
                            signer,
                            ytsGroupRedirecturl,
                            null,
                            null
                    );
                })
                .ifPresent(autoOnboardingProvider::removeAutoConfiguration);
    }

    void delete(final UUID clientId, final String provider) {
        MDC.put(PROVIDER_MDC_KEY, String.valueOf(provider));
        MDC.put(CLIENT_ID_HEADER_NAME, String.valueOf(clientId));

        clientAuthenticationMeansRepository.delete(clientId, provider);
        meansEventDispatcherService.publishAuthenticationMeansDeletedEvent(clientId, provider);
    }

    void saveProviderAuthenticationMeans(final ClientToken clientToken,
                                         final String providerKey,
                                         final Set<UUID> redirectUrlIds,
                                         final Set<ServiceType> serviceTypes,
                                         final Set<TokenScope> scopes,
                                         final Set<AuthenticationMeans> authenticationMeans,
                                         final boolean ignoreAutoOnboarding) {
        UUID clientId = clientToken.getClientIdClaim();
        MDC.put(PROVIDER_MDC_KEY, String.valueOf(providerKey));
        MDC.put(CLIENT_ID_HEADER_NAME, String.valueOf(clientId));

        Set<AuthenticationMeans> decodedAuthenticationMeans = decodeBase64(authenticationMeans);

        List<String> redirectUrls = redirectUrlIds.stream()
                .map(redirectUrlId -> clientRedirectUrlRepository.get(clientId, redirectUrlId)
                        .orElseThrow(() -> new ClientConfigurationValidationException(clientToken.getClientIdClaim(), redirectUrlId))
                        .getUrl())
                .collect(Collectors.toList());

        // We should have only one provider implementation per service type
        Map<ServiceType, Provider> providerByServiceType = providerFactory.getStableProviders(providerKey).stream()
                .collect(Collectors.toMap(Provider::getServiceType, Function.identity(), (p1, p2) -> p1));

        serviceTypes.forEach(serviceType -> {
            Map<String, BasicAuthenticationMean> typedAuthenticationMeans = validateAndConvertToTyped(decodedAuthenticationMeans, providerKey, serviceType);

            Provider provider = providerByServiceType.get(serviceType);
            provider.getTransportKeyRequirements()
                    .ifPresent(keyRequirements -> validateCertificate(typedAuthenticationMeans, keyRequirements));
            provider.getSigningKeyRequirements()
                    .ifPresent(keyRequirements -> validateCertificate(typedAuthenticationMeans, keyRequirements));

            Map<String, BasicAuthenticationMean> fullyConfiguredTypedAuthenticationMeans;
            if (ignoreAutoOnboarding) {
                log.info("The AutoOnboarding will not be triggered.");
                fullyConfiguredTypedAuthenticationMeans = typedAuthenticationMeans;
            } else {
                fullyConfiguredTypedAuthenticationMeans = autoConfigureAuthenticationMeans(
                        clientToken, serviceType, provider, typedAuthenticationMeans, redirectUrls.get(0), redirectUrls, scopes);
            }

            redirectUrlIds.forEach(redirectUrlId -> appendToAuthenticationMeans(clientToken.getClientIdClaim(),
                    redirectUrlId, serviceType, providerKey, fullyConfiguredTypedAuthenticationMeans));
        });
    }

    void saveProviderAuthenticationMeans(final ClientGroupToken clientGroupToken,
                                         final String providerKey,
                                         final Set<UUID> redirectUrlIds,
                                         final Set<ServiceType> serviceTypes,
                                         final Set<TokenScope> scopes,
                                         final Set<AuthenticationMeans> authenticationMeans,
                                         final boolean ignoreAutoOnboarding) {
        MDC.put(PROVIDER_MDC_KEY, String.valueOf(providerKey));

        Set<AuthenticationMeans> decodedAuthenticationMeans = decodeBase64(authenticationMeans);

        // We should have only one provider implementation per service type
        Map<ServiceType, Provider> providerByServiceType = providerFactory.getStableProviders(providerKey).stream()
                .collect(Collectors.toMap(Provider::getServiceType, Function.identity(), (p1, p2) -> p1));

        serviceTypes.forEach(serviceType -> {
            Map<String, BasicAuthenticationMean> typedAuthenticationMeans = validateAndConvertToTyped(decodedAuthenticationMeans, providerKey, serviceType);

            Provider provider = providerByServiceType.get(serviceType);

            Map<String, BasicAuthenticationMean> fullyConfiguredTypedAuthenticationMeans;
            if (ignoreAutoOnboarding) {
                log.info("The AutoOnboarding will not be triggered.");
                fullyConfiguredTypedAuthenticationMeans = typedAuthenticationMeans;
            } else {
                fullyConfiguredTypedAuthenticationMeans = autoConfigureAuthenticationMeans(
                        clientGroupToken, serviceType, provider, typedAuthenticationMeans, scopes);
            }

            redirectUrlIds.forEach(redirectUrlId -> appendToGroupAuthenticationMeans(clientGroupToken.getClientGroupIdClaim(), redirectUrlId, serviceType,
                    providerKey, fullyConfiguredTypedAuthenticationMeans));
        });
    }

    public void saveProviderAuthenticationMeans(final UUID clientId,
                                                final ProviderAuthenticationMeans providerAuthenticationMeans,
                                                final ServiceType serviceType) {
        String provider = providerAuthenticationMeans.getProvider();
        MDC.put(PROVIDER_MDC_KEY, String.valueOf(provider));
        MDC.put(CLIENT_ID_HEADER_NAME, String.valueOf(clientId));

        Set<AuthenticationMeans> authenticationMeans = providerAuthenticationMeans.getAuthenticationMeans();
        Set<AuthenticationMeans> decodedAuthenticationMeans = decodeBase64(authenticationMeans);
        Map<String, BasicAuthenticationMean> typedAuthenticationMeans = validateAndConvertToTyped(decodedAuthenticationMeans, provider, serviceType);

        appendToAuthenticationMeans(clientId, provider, typedAuthenticationMeans);
    }

    private Map<String, BasicAuthenticationMean> autoConfigureAuthenticationMeans(final ClientToken clientToken,
                                                                                  @NonNull final ServiceType serviceType,
                                                                                  @NonNull final Provider provider,
                                                                                  @NonNull final Map<String, BasicAuthenticationMean> authenticationMeans,
                                                                                  @NonNull final String redirectUrl,
                                                                                  @Nullable final List<String> redirectUrls,
                                                                                  @Nullable final Set<TokenScope> scopes) {
        if (provider instanceof AutoOnboardingProvider) {
            log.info("autoConfigureAuthenticationMeans invoked: clientId={}, redirectUrls={}, scopes={}",
                    clientToken.getClientIdClaim(), redirectUrls, scopes);
            final RestTemplateManager restTemplateManager = restTemplateManagerCache.getForClientProvider(
                    clientToken, serviceType, provider.getProviderIdentifier(), false, provider.getVersion());
            final JcaSigner signer = jcaSignerFactory.getForClientToken(clientToken);
            final UrlAutoOnboardingRequest urlAutoOnboardingRequest = new UrlAutoOnboardingRequest(
                    authenticationMeans,
                    restTemplateManager,
                    signer,
                    redirectUrl,
                    redirectUrls,
                    scopes
            );

            final AutoOnboardingProvider autoOnboardingProvider = (AutoOnboardingProvider) provider;

            final Map<String, BasicAuthenticationMean> autoOnboardedAuthenticationMeans;
            autoOnboardedAuthenticationMeans = autoOnboardingProvider.autoConfigureMeans(urlAutoOnboardingRequest);

            log.info("autoConfigureAuthenticationMeans success: clientId={}, redirectUrls={}, scopes={}",
                    clientToken.getClientIdClaim(), redirectUrls, scopes);
            return autoOnboardedAuthenticationMeans;
        }
        return authenticationMeans;
    }

    private Map<String, BasicAuthenticationMean> autoConfigureAuthenticationMeans(final ClientGroupToken clientGroupToken,
                                                                                  @NonNull final ServiceType serviceType,
                                                                                  @NonNull final Provider provider,
                                                                                  @NonNull final Map<String, BasicAuthenticationMean> authenticationMeans,
                                                                                  @Nullable final Set<TokenScope> scopes) {
        if (provider instanceof AutoOnboardingProvider) {
            log.info("autoConfigureAuthenticationMeans invoked: provider={}, clientGroupId={}, redirectUrls={}, scopes={}",
                    provider.getProviderIdentifier(), clientGroupToken.getClientGroupIdClaim(), ytsGroupRedirecturl, scopes);

            final RestTemplateManager restTemplateManager = restTemplateManagerCache.getForClientGroupProvider(
                    clientGroupToken, serviceType, provider.getProviderIdentifier(), false, provider.getVersion());
            final JcaSigner signer = jcaSignerFactory.getForClientGroupToken(clientGroupToken);
            final UrlAutoOnboardingRequest urlAutoOnboardingRequest = new UrlAutoOnboardingRequest(
                    authenticationMeans,
                    restTemplateManager,
                    signer,
                    ytsGroupRedirecturl,
                    Collections.singletonList(ytsGroupRedirecturl),
                    scopes
            );

            final AutoOnboardingProvider autoOnboardingProvider = (AutoOnboardingProvider) provider;

            final Map<String, BasicAuthenticationMean> autoOnboardedAuthenticationMeans;
            autoOnboardedAuthenticationMeans = autoOnboardingProvider.autoConfigureMeans(urlAutoOnboardingRequest);

            log.info("autoConfigureAuthenticationMeans success: provider={}, clientGroupId={}, redirectUrls={}, scopes={}",
                    provider.getProviderIdentifier(), clientGroupToken.getClientGroupIdClaim(), ytsGroupRedirecturl, scopes);
            return autoOnboardedAuthenticationMeans;
        }
        return authenticationMeans;
    }

    List<ProviderTypedAuthenticationMeans> getCensoredAuthenticationMeans(@NonNull final UUID clientId,
                                                                          @NonNull final UUID redirectUrlId,
                                                                          @Nullable final ServiceType serviceType) {
        return clientRedirectUrlClientConfigurationRepository.get(clientId, redirectUrlId, serviceType)
                .stream()
                .map(this::mapToDomain)
                .map(this::replaceAuthenticationMeans)
                .collect(Collectors.toList());
    }

    List<ProviderTypedAuthenticationMeans> getCensoredAuthenticationMeans(final UUID clientId) {
        return clientAuthenticationMeansRepository.get(clientId)
                .map(this::mapToDomain)
                .map(this::replaceAuthenticationMeans)
                .collect(Collectors.toList());
    }

    List<ProviderTypedAuthenticationMeans> getCensoredAuthenticationMeans(@NonNull final ClientGroupToken clientGroupToken,
                                                                          @NonNull final UUID redirectUrlId,
                                                                          @Nullable final ServiceType serviceType) {
        return clientGroupRedirectUrlClientConfigurationRepository.get(clientGroupToken.getClientGroupIdClaim(), redirectUrlId, serviceType)
                .stream()
                .map(this::mapToDomain)
                .map(this::replaceAuthenticationMeans)
                .collect(Collectors.toList());
    }

    private ClientRedirectUrlProviderClientConfiguration mapToDomain(
            final InternalClientRedirectUrlClientConfiguration internalClientRedirectUrlClientConfiguration) {
        return new ClientRedirectUrlProviderClientConfiguration(
                internalClientRedirectUrlClientConfiguration.getClientId(),
                internalClientRedirectUrlClientConfiguration.getRedirectUrlId(),
                internalClientRedirectUrlClientConfiguration.getServiceType(),
                internalClientRedirectUrlClientConfiguration.getProvider(),
                authenticationMeansEncryptionService.decryptAuthenticationMeans(internalClientRedirectUrlClientConfiguration.getAuthenticationMeans()),
                internalClientRedirectUrlClientConfiguration.getUpdated());
    }

    private ClientGroupRedirectUrlProviderClientConfiguration mapToDomain(
            final InternalClientGroupRedirectUrlClientConfiguration internalClientGroupRedirectUrlClientConfiguration) {
        return new ClientGroupRedirectUrlProviderClientConfiguration(
                internalClientGroupRedirectUrlClientConfiguration.getClientGroupId(),
                internalClientGroupRedirectUrlClientConfiguration.getRedirectUrlId(),
                internalClientGroupRedirectUrlClientConfiguration.getServiceType(),
                internalClientGroupRedirectUrlClientConfiguration.getProvider(),
                authenticationMeansEncryptionService.decryptAuthenticationMeans(internalClientGroupRedirectUrlClientConfiguration.getAuthenticationMeans()),
                internalClientGroupRedirectUrlClientConfiguration.getUpdated());
    }

    private ClientProviderAuthenticationMeans mapToDomain(
            final InternalClientAuthenticationMeans internalClientAuthenticationMeans) {
        return new ClientProviderAuthenticationMeans(
                internalClientAuthenticationMeans.getClientId(),
                internalClientAuthenticationMeans.getProvider(),
                authenticationMeansEncryptionService.decryptAuthenticationMeans(internalClientAuthenticationMeans.getAuthenticationMeans()),
                internalClientAuthenticationMeans.getUpdated());
    }

    Set<AuthenticationMeans> encodeBase64(final Set<AuthenticationMeans> input) {
        Set<AuthenticationMeans> authMeansDto = new HashSet<>();
        for (AuthenticationMeans authMeans : input) {
            String encodedValue = encodeBase64(authMeans.getValue());
            authMeansDto.add(new AuthenticationMeans(authMeans.getName(), encodedValue));
        }
        return authMeansDto;
    }

    private ProviderTypedAuthenticationMeans replaceAuthenticationMeans(
            final ClientRedirectUrlProviderClientConfiguration clientRedirectUrlProviderClientConfiguration) {
        Map<String, BasicAuthenticationMean> authenticationMeans = new HashMap<>();
        for (Entry<String, BasicAuthenticationMean> authenticationMean : clientRedirectUrlProviderClientConfiguration.getAuthenticationMeans().entrySet()) {
            authenticationMeans.put(authenticationMean.getKey(), new BasicAuthenticationMean(authenticationMean.getValue().getType(), ASTRIX_PLACEHOLDER));
        }
        return new ProviderTypedAuthenticationMeans(
                clientRedirectUrlProviderClientConfiguration.getProvider(),
                clientRedirectUrlProviderClientConfiguration.getServiceType(),
                authenticationMeans);
    }

    private ProviderTypedAuthenticationMeans replaceAuthenticationMeans(
            final ClientGroupRedirectUrlProviderClientConfiguration clientGroupRedirectUrlProviderClientConfiguration) {
        Map<String, BasicAuthenticationMean> authenticationMeans = new HashMap<>();
        for (Entry<String, BasicAuthenticationMean> authenticationMean : clientGroupRedirectUrlProviderClientConfiguration.getAuthenticationMeans().entrySet()) {
            authenticationMeans.put(authenticationMean.getKey(), new BasicAuthenticationMean(authenticationMean.getValue().getType(), ASTRIX_PLACEHOLDER));
        }
        return new ProviderTypedAuthenticationMeans(
                clientGroupRedirectUrlProviderClientConfiguration.getProvider(),
                clientGroupRedirectUrlProviderClientConfiguration.getServiceType(),
                authenticationMeans);
    }

    private ProviderTypedAuthenticationMeans replaceAuthenticationMeans(
            final ClientProviderAuthenticationMeans clientProviderAuthenticationMeans) {
        Map<String, BasicAuthenticationMean> authenticationMeans = new HashMap<>();
        for (Entry<String, BasicAuthenticationMean> authenticationMean : clientProviderAuthenticationMeans.getAuthenticationMeans().entrySet()) {
            authenticationMeans.put(authenticationMean.getKey(), new BasicAuthenticationMean(authenticationMean.getValue().getType(), ASTRIX_PLACEHOLDER));
        }
        return new ProviderTypedAuthenticationMeans(
                clientProviderAuthenticationMeans.getProvider(),
                ServiceType.AIS,
                authenticationMeans);
    }

    private String encodeBase64(final String authenticationMeans) {
        return authenticationMeans == null ? null :
                new String(Base64.getEncoder().encode(authenticationMeans.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
    }

    private static Set<AuthenticationMeans> decodeBase64(final Set<AuthenticationMeans> input) {
        Set<AuthenticationMeans> authMeans = new HashSet<>();
        for (AuthenticationMeans meansDTO : input) {
            String decodedValue = decodeBase64(meansDTO.getValue());
            authMeans.add(new AuthenticationMeans(meansDTO.getName(), decodedValue));
        }
        return authMeans;
    }

    private static String decodeBase64(final String encodedAuthenticationMeans) {
        return encodedAuthenticationMeans == null ? null :
                new String(Base64.getDecoder().decode(encodedAuthenticationMeans), StandardCharsets.UTF_8);
    }

    public Map<String, BasicAuthenticationMean> validateAndConvertToTyped(
            final Set<AuthenticationMeans> authenticationMeans,
            final String providerKey,
            final ServiceType serviceType) {
        Map<String, BasicAuthenticationMean> typedAuthenticationMeans = new HashMap<>();
        Provider provider = providerFactory.getProvider(providerKey, Provider.class, serviceType, STABLE);
        Map<String, TypedAuthenticationMeans> providerAuthenticationMeanType = provider.getTypedAuthenticationMeans();
        authenticationMeans.forEach(authenticationMeanValue -> {
            String authenticationMeanKey = authenticationMeanValue.getName();
            if (!providerAuthenticationMeanType.containsKey(authenticationMeanKey)) {
                throw new UnrecognizableAuthenticationMeanKey("Authentication mean: " + authenticationMeanKey + " is not supported by given provider: " + providerKey);
            }
            TypedAuthenticationMeans typedAuthenticationMean = providerAuthenticationMeanType.get(authenticationMeanValue.getName());
            AuthenticationMeanType type = typedAuthenticationMean.getType();
            String value = authenticationMeanValue.getValue();
            if (!type.isStringValid(value)) {
                throw new AuthenticationMeanValidationException("Error validating: " + authenticationMeanValue.getName() + ". Should have format of " + type.getDisplayName());
            }
            typedAuthenticationMeans.put(authenticationMeanKey, new BasicAuthenticationMean(type, value));
        });
        return typedAuthenticationMeans;
    }

    private void validateCertificate(final Map<String, BasicAuthenticationMean> typedAuthenticationMeans, final KeyRequirements keyRequirements) {
        if (!StringUtils.isNotBlank(keyRequirements.getPublicKeyAuthenticationMeanReference())) {
            return;
        }
        BasicAuthenticationMean privateKid = typedAuthenticationMeans.get(keyRequirements.getPrivateKidAuthenticationMeanReference());
        BasicAuthenticationMean certificate = typedAuthenticationMeans.get(keyRequirements.getPublicKeyAuthenticationMeanReference());

        if (privateKid == null || certificate == null) {
            log.error("KeyRequirements are specified but kid or certificate is null");
        }
    }

    public void appendToAuthenticationMeans(UUID clientId, String providerName, Map<String, BasicAuthenticationMean> typedAuthenticationMeans) {
        Map<String, BasicAuthenticationMean> allAuthenticationMeans = getDecryptedAuthenticationMeansWithoutException(clientId, providerName).orElse(new HashMap<>());
        allAuthenticationMeans.putAll(typedAuthenticationMeans);

        ClientProviderAuthenticationMeans createdObject = new ClientProviderAuthenticationMeans(
                clientId,
                providerName,
                allAuthenticationMeans,
                Instant.now(clock));
        clientAuthenticationMeansRepository.save(authenticationMeansMapperService.mapToInternal(createdObject));
        meansEventDispatcherService.publishAuthenticationMeansUpdatedEvent(createdObject);
    }

    public void appendToAuthenticationMeans(UUID clientId, UUID redirectUrlId, ServiceType serviceType, String providerName, Map<String, BasicAuthenticationMean> stringBasicAuthenticationMeanMap) {
        Map<String, BasicAuthenticationMean> allAuthenticationMeans = getDecryptedAuthenticationMeansWithoutException(clientId, redirectUrlId, serviceType, providerName).orElse(new HashMap<>());
        allAuthenticationMeans.putAll(stringBasicAuthenticationMeanMap);
        ClientRedirectUrlProviderClientConfiguration createdObject = new ClientRedirectUrlProviderClientConfiguration(
                clientId, redirectUrlId, serviceType, providerName, allAuthenticationMeans, Instant.now(clock));
        clientRedirectUrlClientConfigurationRepository.upsert(authenticationMeansMapperService.mapToInternal(createdObject));
        meansEventDispatcherService.publishAuthenticationMeansUpdatedEvent(createdObject);
    }

    public void appendToGroupAuthenticationMeans(UUID clientGroupId, UUID redirectUrlId, ServiceType serviceType, String providerName, Map<String, BasicAuthenticationMean> stringBasicAuthenticationMeanMap) {
        Map<String, BasicAuthenticationMean> allAuthenticationMeans = getDecryptedGroupAuthenticationMeansWithoutException(clientGroupId, redirectUrlId, serviceType, providerName).orElse(new HashMap<>());
        allAuthenticationMeans.putAll(stringBasicAuthenticationMeanMap);
        ClientGroupRedirectUrlProviderClientConfiguration createdObject = new ClientGroupRedirectUrlProviderClientConfiguration(
                clientGroupId, redirectUrlId, serviceType, providerName, allAuthenticationMeans, Instant.now(clock));
        clientGroupRedirectUrlClientConfigurationRepository.upsert(authenticationMeansMapperService.mapToInternal(createdObject));
        meansEventDispatcherService.publishAuthenticationMeansUpdatedEvent(createdObject);
    }

    public void importFromProviderAuthenticationMeans(AuthenticationMeansReference from,
                                                      AuthenticationMeansReference to,
                                                      String fromProvider,
                                                      String toProvider,
                                                      ServiceType fromProviderServiceType,
                                                      ServiceType toProviderServiceType) {
        UUID fromClientId = from.getClientId();
        UUID toClientId = to.getClientId();
        UUID fromRedirectUrlId = from.getRedirectUrlId();
        UUID toRedirectUrlId = to.getRedirectUrlId();
        clientRedirectUrlRepository.get(fromClientId, fromRedirectUrlId)
                .orElseThrow(() -> new ClientConfigurationValidationException(fromClientId, fromRedirectUrlId)); //NOSONAR we dont need this value, ve only validate if it exists for given client and redirecturl id

        validateIfProviderExists(toProvider, toProviderServiceType);
        Map<String, BasicAuthenticationMean> decryptedAuthMeans = getDecryptedAuthenticationMeansMap(fromProvider, fromProviderServiceType, fromClientId, fromRedirectUrlId);
        UUID newClientId = toClientId == null ? fromClientId : toClientId;
        UUID newRedirectUrlId = toRedirectUrlId == null ? fromRedirectUrlId : toRedirectUrlId;
        appendToAuthenticationMeans(newClientId, newRedirectUrlId, toProviderServiceType, toProvider, decryptedAuthMeans);
    }

    public void importFromProviderAuthenticationMeansForGroup(AuthenticationMeansReference from,
                                                              AuthenticationMeansReference to,
                                                              String fromProvider,
                                                              String toProvider,
                                                              ServiceType fromProviderServiceType,
                                                              ServiceType toProviderServiceType) {
        UUID fromClientGroupId = from.getClientGroupId();
        UUID toClientGroupId = to.getClientGroupId();
        UUID fromRedirectUrlId = from.getRedirectUrlId();
        UUID toRedirectUrlId = to.getRedirectUrlId();

        validateIfProviderExists(toProvider, toProviderServiceType);

        Map<String, BasicAuthenticationMean> decryptedAuthMeans = getDecryptedGroupAuthenticationMeansMap(fromProvider, fromProviderServiceType, fromClientGroupId, fromRedirectUrlId);
        UUID newClientGroupId = toClientGroupId == null ? fromClientGroupId : toClientGroupId;
        UUID newRedirectUrlId = toRedirectUrlId == null ? fromRedirectUrlId : toRedirectUrlId;
        appendToGroupAuthenticationMeans(newClientGroupId, newRedirectUrlId, toProviderServiceType, toProvider, decryptedAuthMeans);
    }

    private void validateIfProviderExists(String toProvider, ServiceType toProviderServiceType) {
        Provider checkedProvider = providerFactory.getProvider(toProvider, Provider.class, toProviderServiceType, STABLE);
        if (checkedProvider == null) {
            throw new ProviderNotFoundException("There is no provider implementation: " + toProvider);
        }
    }

    public void importFromProviderAuthenticationMeansToNonExistingProviderKey(AuthenticationMeansReference from,
                                                                              AuthenticationMeansReference to,
                                                                              String fromProvider,
                                                                              String toProvider,
                                                                              ServiceType fromProviderServiceType,
                                                                              ServiceType toProviderServiceType) {
        UUID fromClientId = from.getClientId();
        UUID toClientId = to.getClientId();
        UUID fromRedirectUrlId = from.getRedirectUrlId();
        UUID toRedirectUrlId = to.getRedirectUrlId();
        clientRedirectUrlRepository.get(fromClientId, fromRedirectUrlId)
                .orElseThrow(() -> new ClientConfigurationValidationException(fromClientId, fromRedirectUrlId)); //NOSONAR we dont need this value, ve only validate if it exists for given client and redirecturl id

        Map<String, BasicAuthenticationMean> decryptedAuthMeans = getDecryptedAuthenticationMeansMap(fromProvider, fromProviderServiceType, fromClientId, fromRedirectUrlId);
        UUID newClientId = toClientId == null ? fromClientId : toClientId;
        UUID newRedirectUrlId = toRedirectUrlId == null ? fromRedirectUrlId : toRedirectUrlId;
        appendToAuthenticationMeans(newClientId, newRedirectUrlId, toProviderServiceType, toProvider, decryptedAuthMeans);
    }

    private Map<String, BasicAuthenticationMean> getDecryptedAuthenticationMeansMap(String fromProvider, ServiceType fromProviderServiceType, UUID fromClientId, UUID fromRedirectUrlId) {
        Map<String, BasicAuthenticationMean> decryptedAuthMeans = acquireAuthenticationMeans(fromProvider, fromProviderServiceType, new AuthenticationMeansReference(fromClientId, fromRedirectUrlId));
        if (decryptedAuthMeans.isEmpty()) {
            throw new ClientConfigurationValidationException(fromClientId, fromRedirectUrlId, fromProviderServiceType, fromProvider);
        }
        return decryptedAuthMeans;
    }

    private Map<String, BasicAuthenticationMean> getDecryptedGroupAuthenticationMeansMap(String fromProvider, ServiceType fromProviderServiceType, UUID fromClientGroupId, UUID fromRedirectUrlId) {
        Map<String, BasicAuthenticationMean> decryptedAuthMeans = acquireAuthenticationMeans(fromProvider, fromProviderServiceType, new AuthenticationMeansReference(null, fromClientGroupId, fromRedirectUrlId));
        if (decryptedAuthMeans.isEmpty()) {
            throw new ClientConfigurationValidationException(fromClientGroupId, fromRedirectUrlId, fromProviderServiceType, fromProvider);
        }
        return decryptedAuthMeans;
    }

    public Set<String> getRegisteredProviders(UUID clientId,
                                              UUID clientRedirectUrlId,
                                              ServiceType serviceType) {
        return clientRedirectUrlClientConfigurationRepository.get(clientId, clientRedirectUrlId, serviceType)
                .stream()
                .map(InternalClientRedirectUrlClientConfiguration::getProvider)
                .collect(Collectors.toSet());
    }

    public Set<String> getRegisteredProvidersForGroup(UUID clientGroupId,
                                                      UUID clientRedirectUrlId,
                                                      ServiceType serviceType) {
        return clientGroupRedirectUrlClientConfigurationRepository.get(clientGroupId, clientRedirectUrlId, serviceType)
                .stream()
                .map(InternalClientGroupRedirectUrlClientConfiguration::getProvider)
                .collect(Collectors.toSet());
    }
}
