package com.yolt.providers.web.authenticationmeans;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yolt.providers.common.domain.AuthenticationMeans;
import com.yolt.providers.common.domain.authenticationmeans.TypedAuthenticationMeans;
import com.yolt.providers.common.providerinterface.Provider;
import com.yolt.providers.common.providerinterface.UrlDataProvider;
import com.yolt.providers.web.clientredirecturl.ClientRedirectUrl;
import com.yolt.providers.web.clientredirecturl.ClientRedirectUrlRepository;
import com.yolt.providers.web.configuration.IntegrationTestContext;
import com.yolt.providers.web.cryptography.signing.JcaSignerFactory;
import com.yolt.providers.web.cryptography.transport.MutualTLSRestTemplateManagerCache;
import com.yolt.providers.web.service.ProviderFactoryService;
import com.yolt.providers.web.service.ProviderVaultKeys;
import com.yolt.providers.web.service.configuration.VersionType;
import nl.ing.lovebird.clienttokens.ClientGroupToken;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.providerdomain.ServiceType;
import nl.ing.lovebird.providerdomain.TokenScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

import static com.yolt.providers.common.domain.authenticationmeans.TypedAuthenticationMeans.AUDIENCE_STRING;
import static com.yolt.providers.common.domain.authenticationmeans.TypedAuthenticationMeans.INSTITUTION_ID_STRING;
import static com.yolt.providers.web.configuration.TestConfiguration.JACKSON_OBJECT_MAPPER;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

@IntegrationTestContext
class ClientAuthenticationMeansConsumerServiceIntegrationTest {

    private static final String BUDGET_INSIGHT_NAME = "BUDGET_INSIGHT";

    @Autowired
    private Clock clock;
    @Autowired
    private ClientRedirectUrlRepository clientRedirectUrlRepository;
    @Autowired
    @Qualifier(JACKSON_OBJECT_MAPPER)
    private ObjectMapper objectMapper;
    @Autowired
    private ClientRedirectUrlClientConfigurationRepository clientRedirectUrlClientConfigurationRepository;
    @Autowired
    private ClientAuthenticationMeansRepository clientAuthenticationMeansRepository;
    @Autowired
    private ClientGroupRedirectUrlClientConfigurationRepository clientGroupRedirectUrlClientConfigurationRepository;
    @Autowired
    private ClientAuthenticationMeansCertificateVerifierService clientAuthenticationMeansCertificateVerifierService;
    @Autowired
    private ClientAuthenticationMeansEventDispatcherService meansEventDispatcherService;
    @Autowired
    private ClientAuthenticationMeansTestConsumer clientAuthenticationMeansConsumer;
    @Autowired
    private ProviderVaultKeys vaultKeys;

    @Value("${yolt.ytsGroup.redirectUrl}")
    private String redirectUrl;

    @Mock
    private ProviderFactoryService providerFactoryService;
    @Mock
    private UrlDataProvider urlDataProvider;
    @Mock
    private JcaSignerFactory jcaSignerFactory;
    @Mock
    private MutualTLSRestTemplateManagerCache restTemplateManagerCache;

    private ClientAuthenticationMeansService clientAuthenticationMeansService;

    private AuthenticationMeansEncryptionService authenticationMeansEncryptionService;

    private AuthenticationMeansMapperService authenticationMeansMapperService;

    @BeforeEach
    void beforeEach() {
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
                redirectUrl,
                jcaSignerFactory,
                restTemplateManagerCache,
                clientAuthenticationMeansCertificateVerifierService,
                authenticationMeansEncryptionService,
                authenticationMeansMapperService);
        when(providerFactoryService.getStableProviders(BUDGET_INSIGHT_NAME)).thenReturn(singletonList(urlDataProvider));
        when(providerFactoryService.getProvider(eq(BUDGET_INSIGHT_NAME), eq(Provider.class), eq(ServiceType.AIS), any(VersionType.class))).thenReturn(urlDataProvider);
        Map<String, TypedAuthenticationMeans> typedAuthenticationMeans = new HashMap<>();
        typedAuthenticationMeans.put("audience", AUDIENCE_STRING);
        typedAuthenticationMeans.put("institutionId", INSTITUTION_ID_STRING);
        when(urlDataProvider.getServiceType()).thenReturn(ServiceType.AIS);
        when(urlDataProvider.getTypedAuthenticationMeans()).thenReturn(typedAuthenticationMeans);
        when(urlDataProvider.getTransportKeyRequirements()).thenReturn(Optional.empty());
        clientAuthenticationMeansConsumer.reset();
    }

    @Test
    void shouldSendAuthMeansUpdateToProperKafkaTopicForSaveProviderAuthenticationMeansWithClientToken() {
        // given
        final UUID clientId = UUID.randomUUID();
        final ClientToken clientToken = mock(ClientToken.class);
        final UUID redirectUrlId = UUID.randomUUID();
        when(clientToken.getClientIdClaim()).thenReturn(clientId);
        AuthenticationMeans[] authenticationMeansArray = {
                new AuthenticationMeans("audience", "value1")
        };
        final Set<AuthenticationMeans> authenticationMeans = Set.of(authenticationMeansArray);
        final Set<AuthenticationMeans> encodedAuthenticationMeans = clientAuthenticationMeansService.encodeBase64(authenticationMeans);
        final ClientRedirectUrl clientRedirectUrl = new ClientRedirectUrl(clientId, redirectUrlId, "someUrl", Instant.now());
        clientRedirectUrlRepository.upsertClientRedirectUrl(clientRedirectUrl);

        // when
        clientAuthenticationMeansService.saveProviderAuthenticationMeans(clientToken, BUDGET_INSIGHT_NAME, singleton(redirectUrlId), singleton(ServiceType.AIS), singleton(TokenScope.ACCOUNTS), encodedAuthenticationMeans, true);

        // then
        await().untilAsserted(() -> {
            List<ClientAuthenticationMeansTestConsumer.Consumed> consumed = clientAuthenticationMeansConsumer.getConsumed();
            assertThat(consumed).hasSize(1);
            ClientAuthenticationMeansDTO dto = consumed.get(0).getValue();
            assertThat(consumed.get(0).getPayloadType()).isEqualTo(ClientAuthenticationMeansMessageType.CLIENT_AUTHENTICATION_MEANS_UPDATED.name());
            assertThat(dto.getRedirectUrlId()).isEqualTo(redirectUrlId);
            assertThat(dto.getClientId()).isEqualTo(clientId);
            assertThat(dto.getProvider()).isEqualTo(BUDGET_INSIGHT_NAME);
        });
    }


    @Test
    void shouldSendGroupAuthMeansUpdateToProperKafkaTopicForSaveProviderAuthenticationMeansWithClientGroupToken() {
        // given
        final UUID clientGroupId = UUID.randomUUID();
        final ClientGroupToken clientGroupToken = mock(ClientGroupToken.class);
        final UUID redirectUrlId = UUID.randomUUID();
        when(clientGroupToken.getClientGroupIdClaim()).thenReturn(clientGroupId);
        AuthenticationMeans[] authenticationMeansArray = {
                new AuthenticationMeans("audience", "value1")
        };
        final Set<AuthenticationMeans> authenticationMeans = Set.of(authenticationMeansArray);
        final Set<AuthenticationMeans> encodedAuthenticationMeans = clientAuthenticationMeansService.encodeBase64(authenticationMeans);

        // when
        clientAuthenticationMeansService.saveProviderAuthenticationMeans(clientGroupToken, BUDGET_INSIGHT_NAME, singleton(redirectUrlId), singleton(ServiceType.AIS), singleton(TokenScope.ACCOUNTS), encodedAuthenticationMeans, true);

        // then
        await().untilAsserted(() -> {
            List<ClientAuthenticationMeansTestConsumer.Consumed> consumed = clientAuthenticationMeansConsumer.getConsumed();
            assertThat(consumed).hasSize(1);
            ClientAuthenticationMeansDTO dto = consumed.get(0).getValue();
            assertThat(consumed.get(0).getPayloadType()).isEqualTo(ClientAuthenticationMeansMessageType.CLIENT_GROUP_AUTHENTICATION_MEANS_UPDATED.name());
            assertThat(dto.getRedirectUrlId()).isEqualTo(redirectUrlId);
            assertThat(dto.getClientId()).isEqualTo(clientGroupId);
            assertThat(dto.getProvider()).isEqualTo(BUDGET_INSIGHT_NAME);
        });
    }
}
