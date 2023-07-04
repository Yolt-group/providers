package com.yolt.providers.web.authenticationmeans;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yolt.providers.common.domain.authenticationmeans.BasicAuthenticationMean;
import com.yolt.providers.common.domain.authenticationmeans.TypedAuthenticationMeans;
import com.yolt.providers.common.providerinterface.UrlDataProvider;
import com.yolt.providers.web.configuration.TestConfiguration;
import com.yolt.providers.web.encryption.AesEncryptionUtil;
import com.yolt.providers.web.service.ProviderFactoryService;
import com.yolt.providers.web.service.ProviderVaultKeys;
import nl.ing.lovebird.clienttokens.ClientGroupToken;
import nl.ing.lovebird.providerdomain.ServiceType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

import static com.yolt.providers.common.domain.authenticationmeans.TypedAuthenticationMeans.*;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ClientGroupAuthenticationMeansCleanupServiceTest {

    private static final String BUDGET_INSIGHT_NAME = "BUDGET_INSIGHT";
    private static final String ENCRYPTION_KEY = "3a74c4e02bc688bd4249920c1d3e0ca6cc212b4625976433b14f4338d75e4ee6";
    private static final ServiceType SERVICE_TYPE = ServiceType.AIS;
    private static final UUID CLIENT_GROUP_ID = UUID.randomUUID();
    private static final UUID REDIRECT_URL_ID = UUID.randomUUID();

    private static final Map<String, TypedAuthenticationMeans> providerAuthenticationMeans = new HashMap<>();
    private static final Map<String, BasicAuthenticationMean> savedAuthenticationMeans = new HashMap<>();

    private ClientGroupAuthenticationMeansCleanupService subject;

    @Mock
    private Clock clock;

    @Mock
    private ProviderFactoryService providerFactory;

    @Mock
    private ClientGroupRedirectUrlClientConfigurationRepository clientGroupRedirectUrlClientConfigurationRepository;

    @Mock
    private ClientAuthenticationMeansEventDispatcherService meansEventDispatcherService;

    @Mock
    private UrlDataProvider urlDataProvider;

    @Mock
    private ClientGroupToken clientGroupToken;

    @Mock
    private ProviderVaultKeys vaultKeys;

    private final ObjectMapper objectMapper = new TestConfiguration().jacksonObjectMapper();

    private AuthenticationMeansEncryptionService authenticationMeansEncryptionService;

    private AuthenticationMeansMapperService authenticationMeansMapperService;

    @BeforeAll
    public static void init() {
        providerAuthenticationMeans.put("audience", AUDIENCE_STRING);
        providerAuthenticationMeans.put("institutionId", INSTITUTION_ID_STRING);

        savedAuthenticationMeans.put("audience", new BasicAuthenticationMean(AUDIENCE_STRING.getType(), "test-audience"));
        savedAuthenticationMeans.put("institutionId", new BasicAuthenticationMean(INSTITUTION_ID_STRING.getType(), "test-institutionId"));
        savedAuthenticationMeans.put("key-to-remove", new BasicAuthenticationMean(KEY_ID.getType(), "test-key-to-remove"));
    }

    @BeforeEach
    public void setup() {
        authenticationMeansEncryptionService = new AuthenticationMeansEncryptionService(vaultKeys, objectMapper);
        authenticationMeansMapperService = new AuthenticationMeansMapperService(clock, authenticationMeansEncryptionService);
        subject = new ClientGroupAuthenticationMeansCleanupService(clock, providerFactory, clientGroupRedirectUrlClientConfigurationRepository,
                meansEventDispatcherService, authenticationMeansEncryptionService, authenticationMeansMapperService);
    }

    @Test
    public void shouldCleanupProviderAuthenticationMeans() throws JsonProcessingException {
        //given
        when(providerFactory.getStableProviders(BUDGET_INSIGHT_NAME)).thenReturn(singletonList(urlDataProvider));
        when(urlDataProvider.getServiceType()).thenReturn(SERVICE_TYPE);
        when(urlDataProvider.getTypedAuthenticationMeans()).thenReturn(providerAuthenticationMeans);
        when(clientGroupToken.getClientGroupIdClaim()).thenReturn(CLIENT_GROUP_ID);
        when(vaultKeys.getAuthEncryptionKey()).thenReturn(ENCRYPTION_KEY);

        String encryptedAuthenticationMeans = AesEncryptionUtil.encrypt(objectMapper.writeValueAsString(savedAuthenticationMeans), ENCRYPTION_KEY);

        Optional<InternalClientGroupRedirectUrlClientConfiguration> authMeansFromDB = Optional.of(new InternalClientGroupRedirectUrlClientConfiguration(
                CLIENT_GROUP_ID, REDIRECT_URL_ID, SERVICE_TYPE, BUDGET_INSIGHT_NAME, encryptedAuthenticationMeans, Instant.now()));
        when(clientGroupRedirectUrlClientConfigurationRepository.get(CLIENT_GROUP_ID, REDIRECT_URL_ID, SERVICE_TYPE, BUDGET_INSIGHT_NAME)).thenReturn(authMeansFromDB);

        ArgumentCaptor<InternalClientGroupRedirectUrlClientConfiguration> storedAuthMeansRedirectUrl = ArgumentCaptor.forClass(InternalClientGroupRedirectUrlClientConfiguration.class);

        //when
        subject.cleanupProviderAuthenticationMeans(clientGroupToken,
                new ProviderAuthenticationMeansCleanupDTO(BUDGET_INSIGHT_NAME, Collections.singleton(REDIRECT_URL_ID), Collections.singleton(SERVICE_TYPE), false));

        //then
        verify(clientGroupRedirectUrlClientConfigurationRepository).upsert(storedAuthMeansRedirectUrl.capture());
        Map<String, BasicAuthenticationMean> newSavedAuthenticationMeans = objectMapper.readValue(AesEncryptionUtil.decrypt(storedAuthMeansRedirectUrl.getValue().getAuthenticationMeans(), ENCRYPTION_KEY),
                new TypeReference<>() {
                });

        assertThat(newSavedAuthenticationMeans).hasSize(2);
        assertThat(newSavedAuthenticationMeans).containsOnlyKeys("audience", "institutionId");
    }

    @Test
    public void shouldOnlyLogProviderAuthenticationMeansToRemove() throws JsonProcessingException {
        //given
        when(providerFactory.getStableProviders(BUDGET_INSIGHT_NAME)).thenReturn(singletonList(urlDataProvider));
        when(urlDataProvider.getServiceType()).thenReturn(SERVICE_TYPE);
        when(urlDataProvider.getTypedAuthenticationMeans()).thenReturn(providerAuthenticationMeans);
        when(clientGroupToken.getClientGroupIdClaim()).thenReturn(CLIENT_GROUP_ID);
        when(vaultKeys.getAuthEncryptionKey()).thenReturn(ENCRYPTION_KEY);

        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        Appender mockAppender = mock(Appender.class);
        root.addAppender(mockAppender);

        String encryptedAuthenticationMeans = AesEncryptionUtil.encrypt(objectMapper.writeValueAsString(savedAuthenticationMeans), ENCRYPTION_KEY);

        Optional<InternalClientGroupRedirectUrlClientConfiguration> authMeansFromDB = Optional.of(new InternalClientGroupRedirectUrlClientConfiguration(
                CLIENT_GROUP_ID, REDIRECT_URL_ID, SERVICE_TYPE, BUDGET_INSIGHT_NAME, encryptedAuthenticationMeans, Instant.now()));
        when(clientGroupRedirectUrlClientConfigurationRepository.get(CLIENT_GROUP_ID, REDIRECT_URL_ID, SERVICE_TYPE, BUDGET_INSIGHT_NAME)).thenReturn(authMeansFromDB);
        //when
        subject.cleanupProviderAuthenticationMeans(clientGroupToken, new ProviderAuthenticationMeansCleanupDTO(BUDGET_INSIGHT_NAME, Collections.singleton(REDIRECT_URL_ID),
                Collections.singleton(SERVICE_TYPE), true));

        //then
        verify(clientGroupRedirectUrlClientConfigurationRepository, times(0)).upsert(any());
        verify(mockAppender).doAppend(argThat((ArgumentMatcher) argument -> {
            LoggingEvent event = (LoggingEvent) argument;
            return Level.INFO.equals(event.getLevel()) && event.getFormattedMessage().contains("Authentication means to remove: [key-to-remove]");
        }));
    }
}
