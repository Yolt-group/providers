package com.yolt.providers.web.authenticationmeans;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yolt.providers.common.domain.AuthenticationMeans;
import com.yolt.providers.common.domain.authenticationmeans.BasicAuthenticationMean;
import com.yolt.providers.common.domain.authenticationmeans.TypedAuthenticationMeans;
import com.yolt.providers.common.domain.authenticationmeans.keymaterial.KeyRequirements;
import com.yolt.providers.common.exception.AuthenticationMeanValidationException;
import com.yolt.providers.common.exception.UnrecognizableAuthenticationMeanKey;
import com.yolt.providers.common.providerinterface.AutoOnboardingProvider;
import com.yolt.providers.common.providerinterface.Provider;
import com.yolt.providers.common.providerinterface.UrlDataProvider;
import com.yolt.providers.web.authenticationmeans.startuplogging.AuthenticationMeansLoggingProperties;
import com.yolt.providers.web.clientredirecturl.ClientRedirectUrl;
import com.yolt.providers.web.clientredirecturl.ClientRedirectUrlRepository;
import com.yolt.providers.web.configuration.TestConfiguration;
import com.yolt.providers.web.cryptography.signing.JcaSignerFactory;
import com.yolt.providers.web.cryptography.transport.MutualTLSRestTemplateManagerCache;
import com.yolt.providers.web.encryption.AesEncryptionUtil;
import com.yolt.providers.web.exception.ClientConfigurationValidationException;
import com.yolt.providers.web.exception.ProviderNotFoundException;
import com.yolt.providers.web.service.ProviderFactoryService;
import com.yolt.providers.web.service.ProviderVaultKeys;
import com.yolt.providers.web.service.configuration.VersionType;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.clienttokens.ClientGroupToken;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.providerdomain.ServiceType;
import nl.ing.lovebird.providerdomain.TokenScope;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

import static com.yolt.providers.common.domain.authenticationmeans.TypedAuthenticationMeans.*;
import static com.yolt.providers.web.authenticationmeans.ClientAuthenticationMeansService.ASTRIX_PLACEHOLDER;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Slf4j
class ClientAuthenticationMeansServiceTest {

    private static final UUID CLIENT_ID = UUID.fromString("c8365fa8-8697-11e8-adc0-fa7ae01bbebc");
    private static final UUID CLIENT_GROUP_ID = UUID.randomUUID();
    private static final ClientToken CLIENT_TOKEN = mock(ClientToken.class);
    private static final ClientGroupToken CLIENT_GROUP_TOKEN = mock(ClientGroupToken.class);
    private static final Set<TokenScope> CLIENT_TOKEN_SCOPES = Set.of(TokenScope.ACCOUNTS, TokenScope.PAYMENTS);
    private static final UUID CLIENT_REDIRECT_URL_ID = UUID.fromString("a53a0b8c-8b1d-11e8-9eb6-529269fb1459");
    private static final UUID CLIENT_GROUP_REDIRECT_URL_ID = UUID.randomUUID();
    private static final ServiceType SERVICE_TYPE = ServiceType.AIS;
    private static final ClientRedirectUrl CLIENT_REDIRECT_URL = new ClientRedirectUrl(CLIENT_ID, CLIENT_REDIRECT_URL_ID, "https://fakeurl.com", Instant.now());
    private static final String CLIENT_GROUP_REDIRECT_URL = "https://fakeurl.com";
    private static final ProviderAuthenticationMeans PROV_AUTH_MEANS = new ProviderAuthenticationMeans(
            "STARLINGBANK",
            Set.of(new AuthenticationMeans("audience", Base64.getEncoder().encodeToString("any-value".getBytes())))
    );
    private static final String ENCRYPTION_KEY = "DDE37CF420642375E0E4FBBB19825431";
    private static final TypeReference<Map<String, BasicAuthenticationMean>> TYPE_REF_AUTHENTICATION_MEANS_LIST = new TypeReference<>() {
    };
    private static final String ANY_PROVIDER_KEY = "ANY_PROVIDER_KEY";
    private static final String AUDIENCE = "audience";

    @Mock
    private Clock clock;

    @Mock
    private ClientRedirectUrlClientConfigurationRepository authMeansRedirectUrlRepo;
    @Mock
    private ClientAuthenticationMeansRepository authMeansRepo;
    @Mock
    private ClientGroupRedirectUrlClientConfigurationRepository clientGroupRedirectUrlClientConfigurationRepository;

    @Mock
    private ClientRedirectUrlRepository clientRedirectUrlRepo;
    @Mock
    private ClientAuthenticationMeansEventDispatcherService eventDispatcher;
    @Mock
    private ProviderFactoryService providerFactoryService;
    @Mock(extraInterfaces = AutoOnboardingProvider.class)
    private UrlDataProvider urlDataProvider;
    @Mock
    private JcaSignerFactory jcaSignerFactory;
    @Mock
    private MutualTLSRestTemplateManagerCache restTemplateManagerCache;
    @Mock
    private AuthenticationMeansLoggingProperties loggingProperties;
    @Mock
    private ProviderVaultKeys vaultKeys;

    private ClientAuthenticationMeansService service;

    private AuthenticationMeansEncryptionService authenticationMeansEncryptionService;

    private AuthenticationMeansMapperService authenticationMeansMapperService;

    @Spy
    private ObjectMapper objectMapper = new TestConfiguration().jacksonObjectMapper();
    private Appender<ILoggingEvent> mockAppender;
    private ArgumentCaptor<ILoggingEvent> captorLoggingEvent;

    private static String certificate;

    static {
        try {
            certificate = readFromClasspath("certificate-to-store.pem");
        } catch (IOException e) {
            log.error("Could not read from classpath");
        }
    }

    private static String readFromClasspath(String resource) throws IOException {
        return StreamUtils.copyToString(ClientAuthenticationMeansServiceTest.class.getClassLoader().getResourceAsStream("certificates/" + resource), StandardCharsets.UTF_8);
    }

    @BeforeEach
    void beforeEach() {
        authenticationMeansEncryptionService = new AuthenticationMeansEncryptionService(vaultKeys, objectMapper);
        authenticationMeansMapperService = new AuthenticationMeansMapperService(clock, authenticationMeansEncryptionService);
        service = new ClientAuthenticationMeansService(
                clock,
                providerFactoryService,
                authMeansRedirectUrlRepo,
                authMeansRepo,
                clientGroupRedirectUrlClientConfigurationRepository,
                clientRedirectUrlRepo,
                eventDispatcher,
                CLIENT_GROUP_REDIRECT_URL,
                jcaSignerFactory,
                restTemplateManagerCache,
                new ClientAuthenticationMeansCertificateVerifierService(Clock.systemUTC(), providerFactoryService, loggingProperties),
                authenticationMeansEncryptionService,
                authenticationMeansMapperService);
        when(CLIENT_TOKEN.getClientIdClaim()).thenReturn(CLIENT_ID);
        when(CLIENT_GROUP_TOKEN.getClientGroupIdClaim()).thenReturn(CLIENT_GROUP_ID);
        mockAppender = mock(Appender.class);
        captorLoggingEvent = ArgumentCaptor.forClass(ILoggingEvent.class);
        final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.addAppender(mockAppender);
    }

    @AfterEach
    void afterEach() {
        final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.detachAppender(mockAppender);
    }

    @Test
    void shouldTriggerEventForSaveProviderAuthenticationMeansWhenAuthMeansAreCreated() {
        // given
        when(vaultKeys.getAuthEncryptionKey()).thenReturn(ENCRYPTION_KEY);
        given(clientRedirectUrlRepo.get(CLIENT_ID, CLIENT_REDIRECT_URL_ID)).willReturn(Optional.of(CLIENT_REDIRECT_URL));

        initializeProviderWithTypedAuthenticationMean("audience", AUDIENCE_STRING);

        // when
        service.saveProviderAuthenticationMeans(
                CLIENT_TOKEN,
                PROV_AUTH_MEANS.getProvider(),
                singleton(CLIENT_REDIRECT_URL_ID),
                singleton(ServiceType.AIS),
                CLIENT_TOKEN_SCOPES,
                PROV_AUTH_MEANS.getAuthenticationMeans(),
                true);

        // then
        then(authMeansRedirectUrlRepo).should().upsert(any());
        final ArgumentCaptor<ClientRedirectUrlProviderClientConfiguration> clientAuthMeansCaptor = ArgumentCaptor.forClass(ClientRedirectUrlProviderClientConfiguration.class);
        then(eventDispatcher).should().publishAuthenticationMeansUpdatedEvent(clientAuthMeansCaptor.capture());
        assertThat(clientAuthMeansCaptor.getValue().getClientId()).isEqualTo(CLIENT_ID);
        assertThat(clientAuthMeansCaptor.getValue().getRedirectUrlId()).isEqualTo(CLIENT_REDIRECT_URL_ID);
        assertThat(clientAuthMeansCaptor.getValue().getProvider()).isEqualTo(PROV_AUTH_MEANS.getProvider());
        assertThat(clientAuthMeansCaptor.getValue().getAuthenticationMeans()).isNotEmpty();
    }

    @Test
    void shouldTriggerAutoOnboardinWhenAuthMeansAreCreatedAndAutoOnboardingIsRequested() {
        // given
        when(vaultKeys.getAuthEncryptionKey()).thenReturn(ENCRYPTION_KEY);
        given(clientRedirectUrlRepo.get(CLIENT_ID, CLIENT_REDIRECT_URL_ID)).willReturn(Optional.of(CLIENT_REDIRECT_URL));

        initializeProviderWithTypedAuthenticationMean("audience", AUDIENCE_STRING);

        // when
        service.saveProviderAuthenticationMeans(
                CLIENT_TOKEN,
                PROV_AUTH_MEANS.getProvider(),
                singleton(CLIENT_REDIRECT_URL_ID),
                singleton(ServiceType.AIS),
                CLIENT_TOKEN_SCOPES,
                PROV_AUTH_MEANS.getAuthenticationMeans(),
                false);

        // then
        then((AutoOnboardingProvider) urlDataProvider).should().autoConfigureMeans(any());
        then(authMeansRedirectUrlRepo).should().upsert(any());
    }

    @Test
    void shouldTriggerEventForDeleteWhenAuthMeansAreDeleted() {
        // given
        doThrow(ProviderNotFoundException.class).when(providerFactoryService).getProvider(any(String.class), eq(AutoOnboardingProvider.class), isNull(), any(VersionType.class));

        // when
        service.delete(null, CLIENT_ID, CLIENT_REDIRECT_URL_ID, SERVICE_TYPE, PROV_AUTH_MEANS.getProvider());

        // then
        then(authMeansRedirectUrlRepo).should().delete(CLIENT_ID, CLIENT_REDIRECT_URL_ID, SERVICE_TYPE, PROV_AUTH_MEANS.getProvider());
        then(eventDispatcher).should().publishAuthenticationMeansDeletedEvent(CLIENT_ID, CLIENT_REDIRECT_URL_ID, SERVICE_TYPE, PROV_AUTH_MEANS.getProvider());
    }

    @Test
    void shouldLogWarnMessageForDeleteWhenInvalidClient() {
        // given
        doThrow(ProviderNotFoundException.class).when(providerFactoryService).getProvider(any(String.class), eq(AutoOnboardingProvider.class), isNull(), any(VersionType.class));

        // when
        service.delete(null, UUID.nameUUIDFromBytes("not existing client ID".getBytes()), CLIENT_REDIRECT_URL_ID, SERVICE_TYPE, PROV_AUTH_MEANS.getProvider());

        // then
        verify(mockAppender).doAppend(captorLoggingEvent.capture());

        //Verify WARN that delete on not extsing provider client configuration was triggered
        ILoggingEvent warnLog = captorLoggingEvent.getValue();
        assertThat(warnLog.getLevel()).isEqualTo(Level.WARN);
        assertThat(warnLog.getMessage()).contains("Attempting to delete client configuration that is NOT present for given client.");
    }

    @Test
    void shouldReturnDecryptedAuthMeansForAcquireAuthenticationMeansWithCorrectData() {
        // given
        when(vaultKeys.getAuthEncryptionKey()).thenReturn(ENCRYPTION_KEY);
        when(providerFactoryService.getProvider(eq("STARLINGBANK"), eq(Provider.class), eq(ServiceType.AIS), any(VersionType.class))).thenReturn(urlDataProvider);
        Map<String, TypedAuthenticationMeans> typedAuthenticationMeans = new HashMap<>();
        typedAuthenticationMeans.put("audience", AUDIENCE_STRING);
        when(urlDataProvider.getTypedAuthenticationMeans()).thenReturn(typedAuthenticationMeans);
        service.saveProviderAuthenticationMeans(CLIENT_ID, PROV_AUTH_MEANS, SERVICE_TYPE);
        ArgumentCaptor<InternalClientAuthenticationMeans> storedAuthMeans = ArgumentCaptor.forClass(InternalClientAuthenticationMeans.class);
        verify(authMeansRepo).save(storedAuthMeans.capture());
        when(authMeansRepo.get(CLIENT_ID, PROV_AUTH_MEANS.getProvider())).thenReturn(Optional.of(storedAuthMeans.getValue()));

        // when
        Map<String, BasicAuthenticationMean> decryptedAuthenticationMeans =
                service.acquireAuthenticationMeans(PROV_AUTH_MEANS.getProvider(), ServiceType.AIS, new AuthenticationMeansReference(CLIENT_ID, CLIENT_REDIRECT_URL_ID));

        // then
        assertThat(decryptedAuthenticationMeans).hasSize(1);
        BasicAuthenticationMean decryptedAuthenticationMean = decryptedAuthenticationMeans.get("audience");
        assertThat(decryptedAuthenticationMean.getValue()).isEqualTo("any-value");
    }

    @Test
    void shouldReturnDecryptedAuthMeansWithMoreSpecificRedirectUrlForAcquireAuthenticationMeansWithCorrectData() {
        // given
        when(vaultKeys.getAuthEncryptionKey()).thenReturn(ENCRYPTION_KEY);
        when(CLIENT_TOKEN.getClientIdClaim()).thenReturn(CLIENT_ID);
        when(clientRedirectUrlRepo.get(CLIENT_ID, CLIENT_REDIRECT_URL_ID)).thenReturn(Optional.of(CLIENT_REDIRECT_URL));
        ProviderAuthenticationMeans PROV_AUTH_MEANS_FOR_REDIRECT_URL = new ProviderAuthenticationMeans(
                "STARLINGBANK",
                Set.of(new AuthenticationMeans("audience", Base64.getEncoder().encodeToString("specific-for-redirect-url".getBytes())))
        );
        initializeProviderWithTypedAuthenticationMean("audience", AUDIENCE_STRING);
        service.saveProviderAuthenticationMeans(
                CLIENT_TOKEN,
                PROV_AUTH_MEANS_FOR_REDIRECT_URL.getProvider(),
                singleton(CLIENT_REDIRECT_URL_ID),
                singleton(SERVICE_TYPE),
                CLIENT_TOKEN_SCOPES,
                PROV_AUTH_MEANS_FOR_REDIRECT_URL.getAuthenticationMeans(),
                true);

        ArgumentCaptor<InternalClientRedirectUrlClientConfiguration> storedAuthMeansRedirectUrl = ArgumentCaptor.forClass(InternalClientRedirectUrlClientConfiguration.class);
        verify(authMeansRedirectUrlRepo).upsert(storedAuthMeansRedirectUrl.capture());
        when(authMeansRedirectUrlRepo.get(CLIENT_ID, CLIENT_REDIRECT_URL_ID, SERVICE_TYPE, PROV_AUTH_MEANS_FOR_REDIRECT_URL.getProvider())).thenReturn(Optional.of(storedAuthMeansRedirectUrl.getValue()));

        // when
        Map<String, BasicAuthenticationMean> decryptedAuthenticationMeans =
                service.acquireAuthenticationMeans(PROV_AUTH_MEANS.getProvider(), ServiceType.AIS, new AuthenticationMeansReference(CLIENT_ID, CLIENT_REDIRECT_URL_ID));

        // then
        assertThat(decryptedAuthenticationMeans).hasSize(1);
        BasicAuthenticationMean decryptedAuthenticationMean = decryptedAuthenticationMeans.get("audience");
        assertThat(decryptedAuthenticationMean.getValue()).isEqualTo("specific-for-redirect-url");
    }

    @Test
    void shouldReturnDecryptedGroupAuthMeansWithMoreSpecificRedirectUrlForAcquireAuthenticationMeansWithCorrectData() {
        // given
        when(vaultKeys.getAuthEncryptionKey()).thenReturn(ENCRYPTION_KEY);
        when(CLIENT_GROUP_TOKEN.getClientGroupIdClaim()).thenReturn(CLIENT_GROUP_ID);
        ProviderAuthenticationMeans PROV_AUTH_MEANS_FOR_REDIRECT_URL = new ProviderAuthenticationMeans(
                "STARLINGBANK",
                Set.of(new AuthenticationMeans("audience", Base64.getEncoder().encodeToString("specific-for-redirect-url".getBytes())))
        );
        when(providerFactoryService.getStableProviders("STARLINGBANK")).thenReturn(singletonList(urlDataProvider));
        when(providerFactoryService.getProvider(eq("STARLINGBANK"), eq(Provider.class), eq(ServiceType.AIS), any(VersionType.class))).thenReturn(urlDataProvider);
        Map<String, TypedAuthenticationMeans> typedAuthenticationMeans = new HashMap<>();
        typedAuthenticationMeans.put("audience", AUDIENCE_STRING);
        when(urlDataProvider.getServiceType()).thenReturn(ServiceType.AIS);
        when(urlDataProvider.getTypedAuthenticationMeans()).thenReturn(typedAuthenticationMeans);
        service.saveProviderAuthenticationMeans(
                CLIENT_GROUP_TOKEN,
                PROV_AUTH_MEANS_FOR_REDIRECT_URL.getProvider(),
                singleton(CLIENT_REDIRECT_URL_ID),
                singleton(SERVICE_TYPE),
                CLIENT_TOKEN_SCOPES,
                PROV_AUTH_MEANS_FOR_REDIRECT_URL.getAuthenticationMeans(),
                true);

        ArgumentCaptor<InternalClientGroupRedirectUrlClientConfiguration> storedAuthMeansRedirectUrl = ArgumentCaptor.forClass(InternalClientGroupRedirectUrlClientConfiguration.class);
        verify(clientGroupRedirectUrlClientConfigurationRepository).upsert(storedAuthMeansRedirectUrl.capture());
        when(clientGroupRedirectUrlClientConfigurationRepository.get(CLIENT_GROUP_ID, CLIENT_REDIRECT_URL_ID, SERVICE_TYPE, PROV_AUTH_MEANS_FOR_REDIRECT_URL.getProvider())).thenReturn(Optional.of(storedAuthMeansRedirectUrl.getValue()));

        // when
        Map<String, BasicAuthenticationMean> decryptedAuthenticationMeans =
                service.acquireAuthenticationMeans(PROV_AUTH_MEANS.getProvider(), ServiceType.AIS, new AuthenticationMeansReference(null, CLIENT_GROUP_ID, CLIENT_REDIRECT_URL_ID));

        // then
        assertThat(decryptedAuthenticationMeans).hasSize(1);
        BasicAuthenticationMean decryptedAuthenticationMean = decryptedAuthenticationMeans.get("audience");
        assertThat(decryptedAuthenticationMean.getValue()).isEqualTo("specific-for-redirect-url");
    }

    @Test
    void shouldValidateCertificateForSaveProviderAuthenticationMeansWithCorrectData() {
        // given
        when(vaultKeys.getAuthEncryptionKey()).thenReturn(ENCRYPTION_KEY);
        when(clientRedirectUrlRepo.get(CLIENT_ID, CLIENT_REDIRECT_URL_ID)).thenReturn(Optional.of(CLIENT_REDIRECT_URL));
        String kid = "12345678-9abc-def0-1234-56789abcdef0";
        ProviderAuthenticationMeans PROV_AUTH_MEANS_FOR_REDIRECT_URL = new ProviderAuthenticationMeans(
                "STARLINGBANK",
                Set.of(new AuthenticationMeans("privateKid", Base64.getEncoder().encodeToString(kid.getBytes())),
                        new AuthenticationMeans("certificate", Base64.getEncoder().encodeToString(certificate.getBytes())))
        );
        Map<String, TypedAuthenticationMeans> authMeans = initializeProviderWithTypedAuthenticationMean("privateKid", KEY_ID);
        authMeans.put("certificate", CERTIFICATE_PEM);
        when(urlDataProvider.getTransportKeyRequirements()).thenReturn(Optional.of(new KeyRequirements(null, "privateKid", "certificate")));

        // when
        service.saveProviderAuthenticationMeans(
                CLIENT_TOKEN,
                PROV_AUTH_MEANS_FOR_REDIRECT_URL.getProvider(),
                singleton(CLIENT_REDIRECT_URL_ID),
                singleton(SERVICE_TYPE),
                CLIENT_TOKEN_SCOPES,
                PROV_AUTH_MEANS_FOR_REDIRECT_URL.getAuthenticationMeans(),
                true);

        // then
        verify(authMeansRedirectUrlRepo).upsert(any(InternalClientRedirectUrlClientConfiguration.class));
    }

    @Test
    public void shouldThrowUnrecognizableAuthenticationMeanKeyExceptionForSaveProviderAuthenticationMeansWithRedirectUrlIdWhenKeyNotFound() {
        // given
        when(CLIENT_TOKEN.getClientIdClaim()).thenReturn(CLIENT_ID);
        given(clientRedirectUrlRepo.get(CLIENT_ID, CLIENT_REDIRECT_URL_ID)).willReturn(Optional.of(CLIENT_REDIRECT_URL));
        AuthenticationMeans authenticationMeansWithNonExistingKey = new AuthenticationMeans(
                "nonExistingKey",
                Base64.getEncoder().encodeToString("any-value".getBytes()));
        when(providerFactoryService.getStableProviders("STARLINGBANK")).thenReturn(singletonList(urlDataProvider));
        when(providerFactoryService.getProvider(eq("STARLINGBANK"), eq(Provider.class), eq(ServiceType.AIS), any(VersionType.class))).thenReturn(urlDataProvider);
        Map<String, TypedAuthenticationMeans> typedAuthenticationMeans = new HashMap<>();
        typedAuthenticationMeans.put("audience", AUDIENCE_STRING);
        when(urlDataProvider.getServiceType()).thenReturn(ServiceType.AIS);
        when(urlDataProvider.getTypedAuthenticationMeans()).thenReturn(typedAuthenticationMeans);

        // when
        ThrowableAssert.ThrowingCallable saveProviderAuthenticationMeansCallable = () ->
                service.saveProviderAuthenticationMeans(
                        CLIENT_TOKEN,
                        "STARLINGBANK",
                        singleton(CLIENT_REDIRECT_URL_ID),
                        singleton(SERVICE_TYPE),
                        CLIENT_TOKEN_SCOPES,
                        Set.of(authenticationMeansWithNonExistingKey),
                        true);

        // then
        assertThatThrownBy(saveProviderAuthenticationMeansCallable)
                .isInstanceOf(UnrecognizableAuthenticationMeanKey.class);
    }

    @Test
    void shouldThrowAuthenticationMeanValidationExceptionForSaveProviderAuthenticationMeansWithRedirectUrlIdWhenWrongFormat() {
        // given
        when(CLIENT_TOKEN.getClientIdClaim()).thenReturn(CLIENT_ID);
        given(clientRedirectUrlRepo.get(CLIENT_ID, CLIENT_REDIRECT_URL_ID)).willReturn(Optional.of(CLIENT_REDIRECT_URL));
        AuthenticationMeans authenticationMeansWithValueInWrongFormat = new AuthenticationMeans(
                "clientId",
                Base64.getEncoder().encodeToString("wrong-format".getBytes()));
        when(providerFactoryService.getStableProviders("STARLINGBANK")).thenReturn(singletonList(urlDataProvider));
        when(providerFactoryService.getProvider(eq("STARLINGBANK"), eq(Provider.class), eq(ServiceType.AIS), any(VersionType.class))).thenReturn(urlDataProvider);
        Map<String, TypedAuthenticationMeans> typedAuthenticationMeans = new HashMap<>();
        typedAuthenticationMeans.put("clientId", CLIENT_ID_UUID);
        when(urlDataProvider.getServiceType()).thenReturn(ServiceType.AIS);
        when(urlDataProvider.getTypedAuthenticationMeans()).thenReturn(typedAuthenticationMeans);

        // when
        ThrowableAssert.ThrowingCallable saveProviderAuthenticationMeansCallable = () ->
                service.saveProviderAuthenticationMeans(
                        CLIENT_TOKEN,
                        "STARLINGBANK",
                        singleton(CLIENT_REDIRECT_URL_ID),
                        singleton(SERVICE_TYPE),
                        CLIENT_TOKEN_SCOPES,
                        Set.of(authenticationMeansWithValueInWrongFormat), true);

        // then
        assertThatThrownBy(saveProviderAuthenticationMeansCallable)
                .isInstanceOf(AuthenticationMeanValidationException.class);
    }

    @Test
    void shouldThrowUnrecognizableAuthenticationMeanKeyForSaveProviderAuthenticationMeansWhenKeyNotFound() {
        // given
        AuthenticationMeans authenticationMeansWithNonExistingKey = new AuthenticationMeans(
                "nonExistingKey",
                Base64.getEncoder().encodeToString("any-value".getBytes()));
        ProviderAuthenticationMeans providerAuthenticationMeans = new ProviderAuthenticationMeans(
                "STARLINGBANK",
                Set.of(authenticationMeansWithNonExistingKey));
        when(providerFactoryService.getProvider(eq("STARLINGBANK"), eq(Provider.class), eq(ServiceType.AIS), any(VersionType.class))).thenReturn(urlDataProvider);
        Map<String, TypedAuthenticationMeans> typedAuthenticationMeans = new HashMap<>();
        typedAuthenticationMeans.put("audience", AUDIENCE_STRING);
        when(urlDataProvider.getTypedAuthenticationMeans()).thenReturn(typedAuthenticationMeans);

        // when
        ThrowableAssert.ThrowingCallable saveProviderAuthenticationMeansCallable = () -> service.saveProviderAuthenticationMeans(CLIENT_ID, providerAuthenticationMeans, SERVICE_TYPE);

        // then
        assertThatThrownBy(saveProviderAuthenticationMeansCallable)
                .isInstanceOf(UnrecognizableAuthenticationMeanKey.class);
    }

    @Test
    void shouldThrowClientConfigurationValidationExceptionForImportFromProviderAuthenticationMeansWithNonExistingClient() {
        // given
        UUID id = UUID.fromString("38bb5ad9-3abb-454f-9ad9-8a79fbc3b6c3");

        // when
        ThrowableAssert.ThrowingCallable importFromProviderAuthenticationMeansCallable = () -> service.importFromProviderAuthenticationMeans(new AuthenticationMeansReference(id, id), new AuthenticationMeansReference(null, null), "", "HALIFAX", SERVICE_TYPE, SERVICE_TYPE);

        // then
        assertThatThrownBy(importFromProviderAuthenticationMeansCallable)
                .isInstanceOf(ClientConfigurationValidationException.class);
    }

    @Test
    void shouldThrowClientConfigurationValidationExceptionForImportFromProviderAuthenticationMeansWithNonExistingProvider() {
        // given
        when(clientRedirectUrlRepo.get(any(), any())).thenReturn(Optional.of(CLIENT_REDIRECT_URL));
        when(providerFactoryService.getProvider(eq("HALIFAX"), eq(Provider.class), any(ServiceType.class), any(VersionType.class))).thenReturn(urlDataProvider);
        UUID id = UUID.fromString("38bb5ad9-3abb-454f-9ad9-8a79fbc3b6c3");

        // when
        ThrowableAssert.ThrowingCallable importFromProviderAuthenticationMeansCallable = () -> service.importFromProviderAuthenticationMeans(new AuthenticationMeansReference(id, id), new AuthenticationMeansReference(null, null), "", "HALIFAX", SERVICE_TYPE, SERVICE_TYPE);

        // then
        assertThatThrownBy(importFromProviderAuthenticationMeansCallable)
                .isInstanceOf(ClientConfigurationValidationException.class);
    }

    @Test
    void shouldThrowProviderNotFoundExceptionForImportFromProviderAuthenticationMeansWhenMissingProvider() {
        // given
        when(vaultKeys.getAuthEncryptionKey()).thenReturn(ENCRYPTION_KEY);
        when(clientRedirectUrlRepo.get(any(), any())).thenReturn(Optional.of(CLIENT_REDIRECT_URL));
        when(providerFactoryService.getProvider(eq("STARLINGBANK"), eq(Provider.class), eq(ServiceType.AIS), any(VersionType.class))).thenReturn(urlDataProvider);
        Map<String, TypedAuthenticationMeans> typedAuthenticationMeans = new HashMap<>();
        typedAuthenticationMeans.put("audience", AUDIENCE_STRING);
        when(urlDataProvider.getTypedAuthenticationMeans()).thenReturn(typedAuthenticationMeans);

        service.saveProviderAuthenticationMeans(CLIENT_ID, PROV_AUTH_MEANS, SERVICE_TYPE);

        // when
        ThrowableAssert.ThrowingCallable importFromProviderAuthenticationMeansCallable = () -> service.importFromProviderAuthenticationMeans(new AuthenticationMeansReference(CLIENT_ID, CLIENT_REDIRECT_URL_ID), new AuthenticationMeansReference(null, null), "STARLINGBANK", "NON_EXISTING_PROVIDER", SERVICE_TYPE, SERVICE_TYPE);

        // then
        ArgumentCaptor<InternalClientAuthenticationMeans> storedAuthMeans = ArgumentCaptor.forClass(InternalClientAuthenticationMeans.class);
        verify(authMeansRepo).save(storedAuthMeans.capture());
        assertThatThrownBy(importFromProviderAuthenticationMeansCallable)
                .isInstanceOf(ProviderNotFoundException.class);
    }

    @Test
    void shouldImportAuthMeansForImportFromProviderAuthenticationMeansWithCorrectData() {
        // given
        when(vaultKeys.getAuthEncryptionKey()).thenReturn(ENCRYPTION_KEY);
        when(clientRedirectUrlRepo.get(any(), any())).thenReturn(Optional.of(CLIENT_REDIRECT_URL));
        when(providerFactoryService.getProvider(eq("HALIFAX"), eq(Provider.class), any(ServiceType.class), any(VersionType.class))).thenReturn(urlDataProvider);
        when(providerFactoryService.getProvider(eq("STARLINGBANK"), eq(Provider.class), eq(ServiceType.AIS), any(VersionType.class))).thenReturn(urlDataProvider);
        Map<String, TypedAuthenticationMeans> typedAuthenticationMeans = new HashMap<>();
        typedAuthenticationMeans.put("audience", AUDIENCE_STRING);
        when(urlDataProvider.getTypedAuthenticationMeans()).thenReturn(typedAuthenticationMeans);
        service.saveProviderAuthenticationMeans(CLIENT_ID, PROV_AUTH_MEANS, SERVICE_TYPE);
        ArgumentCaptor<InternalClientAuthenticationMeans> storedAuthMeans = ArgumentCaptor.forClass(InternalClientAuthenticationMeans.class);
        verify(authMeansRepo).save(storedAuthMeans.capture());
        when(authMeansRepo.get(CLIENT_ID, PROV_AUTH_MEANS.getProvider())).thenReturn(Optional.of(storedAuthMeans.getValue()));

        Map<String, BasicAuthenticationMean> authenticationMeanMap = service.acquireAuthenticationMeans("STARLINGBANK", ServiceType.AIS, new AuthenticationMeansReference(CLIENT_ID, CLIENT_REDIRECT_URL_ID));
        when(service.acquireAuthenticationMeans("STARLINGBANK", ServiceType.AIS, new AuthenticationMeansReference(CLIENT_ID, CLIENT_REDIRECT_URL_ID))).thenReturn(authenticationMeanMap);

        // when
        ThrowableAssert.ThrowingCallable importFromProviderAuthenticationMeansCallable = () -> service.importFromProviderAuthenticationMeans(new AuthenticationMeansReference(CLIENT_ID, CLIENT_REDIRECT_URL_ID), new AuthenticationMeansReference(null, null), "STARLINGBANK", "HALIFAX", SERVICE_TYPE, SERVICE_TYPE);

        // then
        assertThatCode(importFromProviderAuthenticationMeansCallable)
                .doesNotThrowAnyException();
    }

    @Test
    void shouldImportAuthMeansForGroupWhenImportingFromProviderGroupAuthenticationMeans() {
        // given
        when(vaultKeys.getAuthEncryptionKey()).thenReturn(ENCRYPTION_KEY);
        when(providerFactoryService.getProvider(eq("HALIFAX"), eq(Provider.class), any(ServiceType.class), any(VersionType.class))).thenReturn(urlDataProvider);
        when(providerFactoryService.getProvider(eq("STARLINGBANK"), eq(Provider.class), eq(ServiceType.AIS), any(VersionType.class))).thenReturn(urlDataProvider);
        Map<String, TypedAuthenticationMeans> typedAuthenticationMeans = new HashMap<>();
        typedAuthenticationMeans.put("audience", AUDIENCE_STRING);
        when(urlDataProvider.getTypedAuthenticationMeans()).thenReturn(typedAuthenticationMeans);
        Map<String, BasicAuthenticationMean> authMeansMap = Map.of(
                "audience",
                new BasicAuthenticationMean(typedAuthenticationMeans.get("audience").getType(), Base64.getEncoder().encodeToString("any-value".getBytes()))
        );
        ClientGroupRedirectUrlProviderClientConfiguration starlingbankConfig = new ClientGroupRedirectUrlProviderClientConfiguration(
                CLIENT_GROUP_ID, CLIENT_GROUP_REDIRECT_URL_ID, ServiceType.AIS, "STARLINGBANK",
                authMeansMap,
                Instant.now());
        InternalClientGroupRedirectUrlClientConfiguration internalStarlingbankConfig = authenticationMeansMapperService.mapToInternal(starlingbankConfig);
        when(clientGroupRedirectUrlClientConfigurationRepository.get(any(), any(), any(), any()))
                .thenReturn(Optional.of(internalStarlingbankConfig));

        // when
        ThrowableAssert.ThrowingCallable importFromProviderGroupAuthenticationMeansCallable = () -> service.importFromProviderAuthenticationMeansForGroup(
                new AuthenticationMeansReference(null, CLIENT_GROUP_ID, CLIENT_GROUP_REDIRECT_URL_ID),
                new AuthenticationMeansReference(null, CLIENT_GROUP_ID, CLIENT_GROUP_REDIRECT_URL_ID),
                "STARLINGBANK",
                "HALIFAX",
                SERVICE_TYPE,
                SERVICE_TYPE);

        // then
        assertThatCode(importFromProviderGroupAuthenticationMeansCallable)
                .doesNotThrowAnyException();
        verify(clientGroupRedirectUrlClientConfigurationRepository, times(1))
                .upsert(any());
    }

    @Test
    void shouldReturnClientGroupCensoredAuthenticationMeansForGetCensoredAuthenticationMeansWithCorrectData() throws Exception {
        // given
        when(vaultKeys.getAuthEncryptionKey()).thenReturn(ENCRYPTION_KEY);
        when(CLIENT_TOKEN.getClientIdClaim()).thenReturn(CLIENT_ID);
        when(CLIENT_GROUP_TOKEN.getClientGroupIdClaim()).thenReturn(CLIENT_GROUP_ID);
        Map<String, BasicAuthenticationMean> authenticationMeans = new HashMap<>();
        authenticationMeans.put("secret", new BasicAuthenticationMean(PRIVATE_KEY_PEM.getType(), "-----BEGIN PRIVATE KEY-----\nduh-I'm-providing-an-actual-private-key\n-----END PRIVATE KEY-----"));
        String encryptedAuthMeans = AesEncryptionUtil.encrypt(objectMapper.writeValueAsString(authenticationMeans), ENCRYPTION_KEY);
        when(clientGroupRedirectUrlClientConfigurationRepository.get(CLIENT_GROUP_ID, CLIENT_GROUP_REDIRECT_URL_ID, null))
                .thenReturn(Arrays.asList(
                        new InternalClientGroupRedirectUrlClientConfiguration(CLIENT_GROUP_ID, CLIENT_GROUP_REDIRECT_URL_ID, ServiceType.AIS, "LLOYDS_BANK", encryptedAuthMeans, Instant.now()),
                        new InternalClientGroupRedirectUrlClientConfiguration(CLIENT_GROUP_ID, CLIENT_GROUP_REDIRECT_URL_ID, ServiceType.PIS, "BANK_OF_SCOTLAND", encryptedAuthMeans, Instant.now())
                ));

        // when
        List<ProviderTypedAuthenticationMeans> means = service.getCensoredAuthenticationMeans(CLIENT_GROUP_TOKEN, CLIENT_GROUP_REDIRECT_URL_ID, null);

        // then
        assertThat(means).hasSize(2);
        assertThat(means.get(0).getAuthenticationMeans().get("secret").getValue()).isEqualTo(ASTRIX_PLACEHOLDER);
        assertThat(means.get(1).getAuthenticationMeans().get("secret").getValue()).isEqualTo(ASTRIX_PLACEHOLDER);
    }

    @Test
    void shouldThrowClientConfigurationValidationExceptionForImportFromProviderAuthenticationMeansToNonExistingProviderKeyWithNonExistingClient() {
        // given
        UUID id = UUID.fromString("38bb5ad9-3abb-454f-9ad9-8a79fbc3b6c3");

        // when
        ThrowableAssert.ThrowingCallable importFromProviderAuthenticationMeansCallable = () -> service.importFromProviderAuthenticationMeansToNonExistingProviderKey(new AuthenticationMeansReference(id, id), new AuthenticationMeansReference(null, null), "", "HALIFAX", SERVICE_TYPE, SERVICE_TYPE);

        // then
        assertThatThrownBy(importFromProviderAuthenticationMeansCallable)
                .isInstanceOf(ClientConfigurationValidationException.class);
    }

    @Test
    void shouldImportAuthMeansForImportFromProviderAuthenticationMeansToNonExistingProviderKeyWithCorrectData() throws JsonProcessingException {
        // given
        when(vaultKeys.getAuthEncryptionKey()).thenReturn(ENCRYPTION_KEY);
        when(clientRedirectUrlRepo.get(CLIENT_ID, CLIENT_REDIRECT_URL_ID)).thenReturn(Optional.of(CLIENT_REDIRECT_URL));
        when(providerFactoryService.getProvider(eq("STARLINGBANK"), eq(Provider.class), eq(ServiceType.AIS), any(VersionType.class))).thenReturn(urlDataProvider);
        Map<String, TypedAuthenticationMeans> typedAuthenticationMeans = new HashMap<>();
        typedAuthenticationMeans.put(AUDIENCE, AUDIENCE_STRING);
        when(urlDataProvider.getTypedAuthenticationMeans()).thenReturn(typedAuthenticationMeans);
        service.saveProviderAuthenticationMeans(CLIENT_ID, PROV_AUTH_MEANS, SERVICE_TYPE);
        ArgumentCaptor<InternalClientAuthenticationMeans> storedAuthMeans = ArgumentCaptor.forClass(InternalClientAuthenticationMeans.class);
        verify(authMeansRepo).save(storedAuthMeans.capture());
        when(authMeansRepo.get(CLIENT_ID, PROV_AUTH_MEANS.getProvider())).thenReturn(Optional.of(storedAuthMeans.getValue()));

        AuthenticationMeansReference authenticationMeansReference = new AuthenticationMeansReference(CLIENT_ID, CLIENT_REDIRECT_URL_ID);
        Map<String, BasicAuthenticationMean> authenticationMeanMap = service.acquireAuthenticationMeans("STARLINGBANK", ServiceType.AIS, authenticationMeansReference);
        when(service.acquireAuthenticationMeans("STARLINGBANK", ServiceType.AIS, authenticationMeansReference)).thenReturn(authenticationMeanMap);

        // when

        service.importFromProviderAuthenticationMeansToNonExistingProviderKey(authenticationMeansReference, new AuthenticationMeansReference(null, null), "STARLINGBANK", ANY_PROVIDER_KEY, SERVICE_TYPE, SERVICE_TYPE);

        // then
        ArgumentCaptor<InternalClientRedirectUrlClientConfiguration> internalClientGroupRedirectUrlClientConfigurationCaptor = ArgumentCaptor.forClass(InternalClientRedirectUrlClientConfiguration.class);
        verify(authMeansRedirectUrlRepo).upsert(internalClientGroupRedirectUrlClientConfigurationCaptor.capture());

        InternalClientRedirectUrlClientConfiguration capturedValue = internalClientGroupRedirectUrlClientConfigurationCaptor.getValue();
        String capturedAuthenticationMeans = capturedValue.getAuthenticationMeans();
        String decrypt = AesEncryptionUtil.decrypt(capturedAuthenticationMeans, ENCRYPTION_KEY);
        Map<String, BasicAuthenticationMean> actual = objectMapper.readValue(decrypt, TYPE_REF_AUTHENTICATION_MEANS_LIST);

        assertThat(actual.get(AUDIENCE).getValue()).isEqualTo(authenticationMeanMap.get(AUDIENCE).getValue());
        assertThat(capturedValue.getClientId()).isEqualTo(CLIENT_ID);
        assertThat(capturedValue.getRedirectUrlId()).isEqualTo(CLIENT_REDIRECT_URL_ID);
        assertThat(capturedValue.getProvider()).isEqualTo(ANY_PROVIDER_KEY);
        assertThat(capturedValue.getServiceType()).isEqualTo(ServiceType.AIS);
    }

    private Map<String, TypedAuthenticationMeans> initializeProviderWithTypedAuthenticationMean(
            final String authenticationMeanKey,
            final TypedAuthenticationMeans authenticationMeanType) {
        when(providerFactoryService.getStableProviders("STARLINGBANK")).thenReturn(singletonList(urlDataProvider));
        when(providerFactoryService.getProvider(eq("STARLINGBANK"), eq(Provider.class), eq(ServiceType.AIS), any(VersionType.class))).thenReturn(urlDataProvider);
        Map<String, TypedAuthenticationMeans> typedAuthenticationMeans = new HashMap<>();
        typedAuthenticationMeans.put(authenticationMeanKey, authenticationMeanType);
        when(urlDataProvider.getServiceType()).thenReturn(ServiceType.AIS);
        when(urlDataProvider.getTypedAuthenticationMeans()).thenReturn(typedAuthenticationMeans);
        when(urlDataProvider.getTransportKeyRequirements()).thenReturn(Optional.empty());
        return typedAuthenticationMeans;
    }
}
