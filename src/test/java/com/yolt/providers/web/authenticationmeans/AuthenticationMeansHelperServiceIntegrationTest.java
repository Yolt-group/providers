package com.yolt.providers.web.authenticationmeans;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yolt.providers.common.domain.AuthenticationMeans;
import com.yolt.providers.common.domain.authenticationmeans.BasicAuthenticationMean;
import com.yolt.providers.common.domain.authenticationmeans.TypedAuthenticationMeans;
import com.yolt.providers.common.domain.authenticationmeans.types.NoWhiteCharacterStringType;
import com.yolt.providers.common.providerinterface.Provider;
import com.yolt.providers.common.providerinterface.UrlDataProvider;
import com.yolt.providers.web.ProviderApp;
import com.yolt.providers.web.clientredirecturl.ClientRedirectUrl;
import com.yolt.providers.web.clientredirecturl.ClientRedirectUrlRepository;
import com.yolt.providers.web.configuration.IntegrationTestContext;
import com.yolt.providers.web.cryptography.signing.JcaSignerFactory;
import com.yolt.providers.web.cryptography.transport.MutualTLSRestTemplateManagerCache;
import com.yolt.providers.web.encryption.AesEncryptionUtil;
import com.yolt.providers.web.service.ProviderFactoryService;
import com.yolt.providers.web.service.ProviderVaultKeys;
import com.yolt.providers.web.service.configuration.VersionType;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.providerdomain.ServiceType;
import nl.ing.lovebird.providerdomain.TokenScope;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

import static com.yolt.providers.common.domain.authenticationmeans.TypedAuthenticationMeans.AUDIENCE_STRING;
import static com.yolt.providers.common.domain.authenticationmeans.TypedAuthenticationMeans.INSTITUTION_ID_STRING;
import static com.yolt.providers.web.authenticationmeans.ClientAuthenticationMeansService.ASTRIX_PLACEHOLDER;
import static com.yolt.providers.web.configuration.TestConfiguration.JACKSON_OBJECT_MAPPER;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@IntegrationTestContext
@ContextConfiguration(classes = {ProviderApp.class})
class AuthenticationMeansHelperServiceIntegrationTest {

    private static final String VALUE_THAT_SHOULD_BE_ENCRYPTED = "valueThatShouldBeEncrypted";
    private static final String BUDGET_INSIGHT_NAME = "BUDGET_INSIGHT";
    private static final String ENCRYPTION_KEY = "3a74c4e02bc688bd4249920c1d3e0ca6cc212b4625976433b14f4338d75e4ee6";

    @Autowired
    private Clock clock;
    @Autowired
    private ClientRedirectUrlRepository clientRedirectUrlRepository;
    @Autowired
    private ClientRedirectUrlClientConfigurationRepository clientRedirectUrlClientConfigurationRepository;
    @Autowired
    private ClientGroupRedirectUrlClientConfigurationRepository clientGroupRedirectUrlClientConfigurationRepository;
    @Autowired
    private ClientAuthenticationMeansRepository clientAuthenticationMeansRepository;
    @Autowired
    private ClientAuthenticationMeansEventDispatcherService meansEventDispatcherService;
    @Autowired
    private ClientAuthenticationMeansCertificateVerifierService clientAuthenticationMeansCertificateVerifierService;
    @Autowired
    @Qualifier(JACKSON_OBJECT_MAPPER)
    private ObjectMapper objectMapper;

    @Mock
    private ProviderFactoryService providerFactoryService;
    @Mock
    private UrlDataProvider urlDataProvider;
    @Mock
    private JcaSignerFactory jcaSignerFactory;
    @Mock
    private MutualTLSRestTemplateManagerCache restTemplateManagerCache;
    @Mock
    private ClientToken clientToken;
    @Mock
    private ProviderVaultKeys vaultKeys;

    private ClientAuthenticationMeansService clientAuthenticationMeansService;

    private AuthenticationMeansEncryptionService authenticationMeansEncryptionService;

    private AuthenticationMeansMapperService authenticationMeansMapperService;

    @Value("${yolt.ytsGroup.redirectUrl}")
    private String ytsGroupRedirectUrl;

    @BeforeEach
    void setup() {
        initMocks(this);
        authenticationMeansEncryptionService = new AuthenticationMeansEncryptionService(vaultKeys, objectMapper);
        authenticationMeansMapperService = new AuthenticationMeansMapperService(clock, authenticationMeansEncryptionService);
        clientAuthenticationMeansService = new ClientAuthenticationMeansService(
                clock,
                providerFactoryService,
                clientRedirectUrlClientConfigurationRepository,
                clientAuthenticationMeansRepository,
                clientGroupRedirectUrlClientConfigurationRepository,
                clientRedirectUrlRepository,
                meansEventDispatcherService,
                ytsGroupRedirectUrl,
                jcaSignerFactory,
                restTemplateManagerCache,
                clientAuthenticationMeansCertificateVerifierService,
                authenticationMeansEncryptionService,
                authenticationMeansMapperService);
    }

    @Test
    void shouldStoreEncryptedAuthenticationMeansIntoDBForSaveProviderAuthenticationMeansWithCorrectData() {
        // given
        when(vaultKeys.getAuthEncryptionKey()).thenReturn(ENCRYPTION_KEY);
        ServiceType serviceType = ServiceType.AIS;
        when(providerFactoryService.getStableProviders(BUDGET_INSIGHT_NAME)).thenReturn(singletonList(urlDataProvider));
        when(providerFactoryService.getProvider(eq(BUDGET_INSIGHT_NAME), eq(Provider.class), eq(serviceType), any(VersionType.class))).thenReturn(urlDataProvider);
        Map<String, TypedAuthenticationMeans> typedAuthenticationMeans = new HashMap<>();
        typedAuthenticationMeans.put("audience", AUDIENCE_STRING);
        typedAuthenticationMeans.put("institutionId", INSTITUTION_ID_STRING);
        when(urlDataProvider.getServiceType()).thenReturn(serviceType);
        when(urlDataProvider.getTypedAuthenticationMeans()).thenReturn(typedAuthenticationMeans);
        when(urlDataProvider.getTransportKeyRequirements()).thenReturn(Optional.empty());
        UUID clientId = UUID.randomUUID();
        when(clientToken.getClientIdClaim()).thenReturn(clientId);
        UUID redirectUrlId = UUID.randomUUID();
        AuthenticationMeans[] authenticationMeansArray = {
                new AuthenticationMeans("audience", VALUE_THAT_SHOULD_BE_ENCRYPTED),
                new AuthenticationMeans("institutionId", VALUE_THAT_SHOULD_BE_ENCRYPTED)
        };
        Set<AuthenticationMeans> authenticationMeans = new HashSet<>(Arrays.asList(authenticationMeansArray));
        Set<AuthenticationMeans> encodedAuthenticationMeans = clientAuthenticationMeansService.encodeBase64(authenticationMeans);
        ClientRedirectUrl clientRedirectUrl = new ClientRedirectUrl(clientId, redirectUrlId, "someUrl", Instant.now());
        clientRedirectUrlRepository.upsertClientRedirectUrl(clientRedirectUrl);

        // when
        clientAuthenticationMeansService.saveProviderAuthenticationMeans(clientToken, BUDGET_INSIGHT_NAME, singleton(redirectUrlId), singleton(serviceType), singleton(TokenScope.ACCOUNTS), encodedAuthenticationMeans, true);

        // then
        Optional<InternalClientRedirectUrlClientConfiguration> internalClientRedirectUrlAuthenticationMeans =
                clientRedirectUrlClientConfigurationRepository.get(clientId, redirectUrlId, serviceType, BUDGET_INSIGHT_NAME);

        InternalClientRedirectUrlClientConfiguration value = internalClientRedirectUrlAuthenticationMeans.get();
        String encrypted = value.getAuthenticationMeans();
        String decrypted = AesEncryptionUtil.decrypt(encrypted, ENCRYPTION_KEY);
        assertThat(encrypted).doesNotContain(VALUE_THAT_SHOULD_BE_ENCRYPTED);
        assertThat(decrypted).contains(VALUE_THAT_SHOULD_BE_ENCRYPTED);

        List<ProviderTypedAuthenticationMeans> providerCensoredAuthenticationMeansList =
                clientAuthenticationMeansService.getCensoredAuthenticationMeans(clientId, redirectUrlId, serviceType);
        ProviderTypedAuthenticationMeans providerCensoredAuthenticationMeans = providerCensoredAuthenticationMeansList.get(0);
        assertThat(providerCensoredAuthenticationMeans.getProvider()).isEqualTo(BUDGET_INSIGHT_NAME);

        Map<String, BasicAuthenticationMean> censoredAuthenticationMeans = providerCensoredAuthenticationMeans.getAuthenticationMeans();
        assertThat(censoredAuthenticationMeans).hasSize(2);
        assertThat(censoredAuthenticationMeans.get("audience").getType()).isInstanceOf(NoWhiteCharacterStringType.class);
        assertThat(censoredAuthenticationMeans.get("institutionId").getType()).isInstanceOf(NoWhiteCharacterStringType.class);
        assertThat(censoredAuthenticationMeans.get("audience").getValue()).isEqualTo(ASTRIX_PLACEHOLDER);
        assertThat(censoredAuthenticationMeans.get("institutionId").getValue()).isEqualTo(ASTRIX_PLACEHOLDER);

        Map<String, BasicAuthenticationMean> decryptedAuthenticationMeans =
                clientAuthenticationMeansService.acquireAuthenticationMeans(BUDGET_INSIGHT_NAME, serviceType, new AuthenticationMeansReference(clientId, redirectUrlId));
        assertThat(decryptedAuthenticationMeans).hasSize(2);
        assertThat(decryptedAuthenticationMeans.get("audience").getType()).isInstanceOf(NoWhiteCharacterStringType.class);
        assertThat(decryptedAuthenticationMeans.get("institutionId").getType()).isInstanceOf(NoWhiteCharacterStringType.class);
        assertThat(decryptedAuthenticationMeans.get("audience").getValue()).isEqualTo(VALUE_THAT_SHOULD_BE_ENCRYPTED);
        assertThat(decryptedAuthenticationMeans.get("institutionId").getValue()).isEqualTo(VALUE_THAT_SHOULD_BE_ENCRYPTED);
    }
}
