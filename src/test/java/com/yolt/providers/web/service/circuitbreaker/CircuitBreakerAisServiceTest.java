package com.yolt.providers.web.service.circuitbreaker;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yolt.providers.common.ais.url.*;
import com.yolt.providers.common.cryptography.RestTemplateManager;
import com.yolt.providers.common.domain.authenticationmeans.BasicAuthenticationMean;
import com.yolt.providers.common.domain.dynamic.AccessMeansOrStepDTO;
import com.yolt.providers.common.domain.dynamic.step.RedirectStep;
import com.yolt.providers.common.domain.dynamic.step.Step;
import com.yolt.providers.common.exception.FormDecryptionFailedException;
import com.yolt.providers.common.exception.MissingAuthenticationMeansException;
import com.yolt.providers.common.exception.ProviderFetchDataException;
import com.yolt.providers.common.exception.TokenInvalidException;
import com.yolt.providers.common.providerinterface.UrlDataProvider;
import com.yolt.providers.web.authenticationmeans.ClientAuthenticationMeansService;
import com.yolt.providers.web.circuitbreaker.*;
import com.yolt.providers.web.controller.dto.*;
import com.yolt.providers.web.cryptography.signing.JcaSigner;
import com.yolt.providers.web.cryptography.signing.JcaSignerFactory;
import com.yolt.providers.web.cryptography.transport.MutualTLSRestTemplateManagerCache;
import com.yolt.providers.web.encryption.AesEncryptionUtil;
import com.yolt.providers.web.service.AccountsFilterService;
import com.yolt.providers.web.service.AccountsPostProcessingService;
import com.yolt.providers.web.service.ProviderVaultKeys;
import com.yolt.providers.web.service.TransactionsDataLimiter;
import nl.ing.lovebird.clienttokens.ClientUserToken;
import nl.ing.lovebird.providershared.AccessMeansDTO;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeoutException;

import static nl.ing.lovebird.providerdomain.ServiceType.AIS;
import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CircuitBreakerAisServiceTest {

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID SITE_ID = UUID.randomUUID();
    private static final String YOLT_PROVIDER = "YOLT_PROVIDER";
    private static final UUID REDIRECT_URL_ID = UUID.fromString("571c2c82-a2f0-4c3c-b110-627020f58351");
    private static final String REDIRECT_URL = "loginUrl";
    private static final RedirectStep REDIRECT_STEP = new RedirectStep(REDIRECT_URL);
    private static final String MY_ACCESS_MEANS = "MY_ACCESS_MEANS";

    private static final String secretKey = "a3f60fafc948035382fbe9ce7b4535c4";
    private static final UUID USER_ID = UUID.fromString("2068f360-0da5-4e74-ab53-2817d04c6242");
    private static final UUID USER_SITE_ID = UUID.fromString("021b71a5-8905-42c6-a223-eea6082bd355");
    private static final UUID redirectUrlId = UUID.fromString("ebe08670-e9c2-4fd5-b65c-cbfc4abc26f3");
    private static final UUID providerRequestId = UUID.fromString("999d1dde-b7bd-482b-b9d0-a2a75ca6659c");
    private static final String YOLT_PROVIDER_NAME = "YOLT_PROVIDER";
    private static final UUID CLIENT_REDIRECT_URL_ID_YOLT_APP = UUID.randomUUID();

    private final ProvidersCircuitBreaker circuitBreaker = new ProvidersCircuitBreakerMock();

    @InjectMocks
    private CircuitBreakerAisService circuitBreakerAisService;

    @Mock
    private ProvidersCircuitBreakerFactory circuitBreakerFactory;

    @Mock
    private TransactionsDataLimiter transactionsDataLimiter;

    @Mock
    private AccountsFilterService accountsFilterService;

    @Mock
    private AccountsPostProcessingService accountsPostProcessingService;

    @Mock
    private ClientUserToken clientUserToken;

    @Mock
    private UrlDataProvider urlDataProvider;

    @Mock
    private MutualTLSRestTemplateManagerCache restTemplateManagerCache;

    @Mock
    private ClientAuthenticationMeansService clientAuthenticationMeansService;

    @Mock
    private JcaSignerFactory jcaSignerFactory;

    @Mock
    private ProviderVaultKeys vaultKeys;

    @Mock
    private Clock clock;

    @Mock
    private ObjectMapper objectMapper;

    @BeforeEach
    public void beforeEach() {
        when(circuitBreakerFactory.create(any(), any(), any(), any(), any())).thenReturn(circuitBreaker);
    }

    @Test
    public void shouldReturnAccessMeansForRefreshAccessMeansWithCorrectData() throws Exception {
        // given
        AccessMeansDTO encryptedAccessMeansDTO = new AccessMeansDTO(UUID.randomUUID(), MY_ACCESS_MEANS,
                new Date(), new Date());

        RefreshAccessMeansDTO refreshAccessMeansDTO = new RefreshAccessMeansDTO(encryptedAccessMeansDTO, new AuthenticationMeansReference(CLIENT_ID, redirectUrlId), null, Instant.now().minus(30, ChronoUnit.DAYS));
        UrlRefreshAccessMeansRequest urlRefreshAccessMeans = new UrlRefreshAccessMeansRequest(
                refreshAccessMeansDTO.getAccessMeansDTO(),
                clientAuthenticationMeansService.acquireAuthenticationMeans(YOLT_PROVIDER, AIS, refreshAccessMeansDTO.getAuthenticationMeansReference()),
                jcaSignerFactory.getForClientToken(clientUserToken),
                restTemplateManagerCache.getForClientProvider(clientUserToken, AIS, YOLT_PROVIDER, false, urlDataProvider.getVersion()),
                refreshAccessMeansDTO.getPsuIpAddress());

        AccessMeansDTO expectedResult = new AccessMeansDTO(UUID.randomUUID(), MY_ACCESS_MEANS, new Date(), new Date());
        when(clock.instant()).thenReturn(Instant.now());
        when(urlDataProvider.refreshAccessMeans(any())).thenReturn(expectedResult);

        // when
        AccessMeansDTO result = circuitBreakerAisService.refreshAccessMeans(clientUserToken, SITE_ID, YOLT_PROVIDER, redirectUrlId, urlDataProvider, urlRefreshAccessMeans, Instant.now().minus(30, ChronoUnit.DAYS), 90);

        // then
        assertThat(result).isEqualTo(expectedResult);
    }

    @Test
    public void shouldThrowProvidersNonCircuitBreakingTokenInvalidExceptionWithMessageAboutExpiredConsenst() throws Exception {
        // given
        AccessMeansDTO encryptedAccessMeansDTO = new AccessMeansDTO(UUID.randomUUID(), MY_ACCESS_MEANS,
                new Date(), Date.from(Instant.now().minus(10, ChronoUnit.MINUTES)));
        UrlRefreshAccessMeansRequest urlRefreshAccessMeans = new UrlRefreshAccessMeansRequest(
                encryptedAccessMeansDTO, null, null, null, null);

        when(clock.instant()).thenReturn(Instant.now());
        when(urlDataProvider.refreshAccessMeans(any())).thenThrow(new TokenInvalidException());

        // when
        ThrowableAssert.ThrowingCallable callable = () -> circuitBreakerAisService.refreshAccessMeans(clientUserToken, SITE_ID, YOLT_PROVIDER, redirectUrlId, urlDataProvider, urlRefreshAccessMeans, Instant.now().minus(91, ChronoUnit.DAYS), 90);

        // then
        assertThatExceptionOfType(ProvidersNonCircuitBreakingTokenInvalidException.class)
                .isThrownBy(callable)
                .withMessage("Tried to refresh access means using expired consent");
    }

    @Test
    public void shouldThrowProvidersNonCircuitBreakingTokenInvalidExceptionWithMessageAboutExpiredToken() throws Exception {
        // given
        AccessMeansDTO encryptedAccessMeansDTO = new AccessMeansDTO(UUID.randomUUID(), MY_ACCESS_MEANS,
                new Date(), Date.from(Instant.now().minus(10, ChronoUnit.MINUTES)));
        UrlRefreshAccessMeansRequest urlRefreshAccessMeans = new UrlRefreshAccessMeansRequest(
                encryptedAccessMeansDTO, null, null, null, null);

        when(clock.instant()).thenReturn(Instant.now());
        when(urlDataProvider.refreshAccessMeans(any())).thenThrow(new TokenInvalidException());

        // when
        ThrowableAssert.ThrowingCallable callable = () -> circuitBreakerAisService.refreshAccessMeans(clientUserToken, SITE_ID, YOLT_PROVIDER, redirectUrlId, urlDataProvider, urlRefreshAccessMeans, Instant.now().minus(30, ChronoUnit.DAYS), 90);

        // then
        assertThatExceptionOfType(ProvidersNonCircuitBreakingTokenInvalidException.class)
                .isThrownBy(callable)
                .withMessage("Tried to refresh access means using expired access means");
    }

    @Test
    public void shouldThrowProvidersNonCircuitBreakingTokenInvalidExceptionWhenConsentCreationTimeIsNull() throws Exception {
        // given
        AccessMeansDTO encryptedAccessMeansDTO = new AccessMeansDTO(UUID.randomUUID(), MY_ACCESS_MEANS,
                new Date(), Date.from(Instant.now().plus(10, ChronoUnit.MINUTES)));
        UrlRefreshAccessMeansRequest urlRefreshAccessMeans = new UrlRefreshAccessMeansRequest(
                encryptedAccessMeansDTO, null, null, null, null);
        when(clock.instant()).thenReturn(Instant.now());
        when(urlDataProvider.refreshAccessMeans(any())).thenThrow(new TokenInvalidException());

        // when
        ThrowableAssert.ThrowingCallable callable = () -> circuitBreakerAisService.refreshAccessMeans(clientUserToken, SITE_ID, YOLT_PROVIDER, redirectUrlId, urlDataProvider, urlRefreshAccessMeans, null, 90);

        // then
        assertThatExceptionOfType(ProvidersNonCircuitBreakingTokenInvalidException.class)
                .isThrownBy(callable)
                .withCauseInstanceOf(TokenInvalidException.class);
    }

    @Test
    public void shouldReturnAccessMeansForCreateNewAccessMeansWithCorrectData() {
        // given
        ApiCreateAccessMeansDTO apiCreateAccessMeansDTO = new ApiCreateAccessMeansDTO(null, null, null, new AuthenticationMeansReference(CLIENT_ID, null), null, null, null, null);
        Map<String, BasicAuthenticationMean> authenticationMeans = new HashMap<>();

        AccessMeansDTO response = new AccessMeansDTO(UUID.randomUUID(), MY_ACCESS_MEANS, new Date(), new Date());
        UrlCreateAccessMeansRequest urlCreateAccessMeansRequest = new UrlCreateAccessMeansRequest(
                apiCreateAccessMeansDTO.getUserId(),
                apiCreateAccessMeansDTO.getRedirectUrlPostedBackFromSite(),
                apiCreateAccessMeansDTO.getBaseClientRedirectUrl(),
                authenticationMeans,
                apiCreateAccessMeansDTO.getProviderState(),
                jcaSignerFactory.getForClientToken(clientUserToken),
                restTemplateManagerCache.getForClientProvider(clientUserToken, AIS, YOLT_PROVIDER, false, urlDataProvider.getVersion()),
                apiCreateAccessMeansDTO.getFilledInUserSiteFormValues(),
                apiCreateAccessMeansDTO.getState(),
                apiCreateAccessMeansDTO.getPsuIpAddress()
        );
        when(urlDataProvider.createNewAccessMeans(any())).thenReturn(new AccessMeansOrStepDTO(response));

        // when
        AccessMeansOrStepDTO result = circuitBreakerAisService.createNewAccessMeans(clientUserToken, SITE_ID, YOLT_PROVIDER, redirectUrlId, vaultKeys, urlDataProvider, urlCreateAccessMeansRequest);

        // then
        assertThat(result.getAccessMeans()).isEqualTo(response);
    }

    @Test
    public void shouldThrowExceptionForCreateNewAccessMeansWhenCannotDecryptForm() {
        // given
        ApiCreateAccessMeansDTO apiCreateAccessMeansDTO = new ApiCreateAccessMeansDTO(null, null, null, new AuthenticationMeansReference(CLIENT_ID, null), null, null, null, null);
        Map<String, BasicAuthenticationMean> authenticationMeans = new HashMap<>();
        UrlCreateAccessMeansRequest urlCreateAccessMeansRequest = new UrlCreateAccessMeansRequest(
                apiCreateAccessMeansDTO.getUserId(),
                apiCreateAccessMeansDTO.getRedirectUrlPostedBackFromSite(),
                apiCreateAccessMeansDTO.getBaseClientRedirectUrl(),
                authenticationMeans,
                apiCreateAccessMeansDTO.getProviderState(),
                jcaSignerFactory.getForClientToken(clientUserToken),
                restTemplateManagerCache.getForClientProvider(clientUserToken, AIS, YOLT_PROVIDER, false, urlDataProvider.getVersion()),
                apiCreateAccessMeansDTO.getFilledInUserSiteFormValues(),
                apiCreateAccessMeansDTO.getState(),
                apiCreateAccessMeansDTO.getPsuIpAddress()
        );
        when(urlDataProvider.createNewAccessMeans(any()))
                .thenThrow(new FormDecryptionFailedException("Decryption failed"));

        // when
        ThrowableAssert.ThrowingCallable getLoginCallable = () -> circuitBreakerAisService.createNewAccessMeans(clientUserToken, SITE_ID, YOLT_PROVIDER, redirectUrlId, vaultKeys, urlDataProvider, urlCreateAccessMeansRequest);

        // then
        assertThatThrownBy(getLoginCallable)
                .isExactlyInstanceOf(ProvidersCircuitBreakerException.class)
                .hasCauseInstanceOf(FormDecryptionFailedException.class)
                .hasRootCauseMessage("Decryption failed");
    }

    @Test
    public void shouldReturnRedirectStepForGetLoginInfoWithCorrectData() {
        // given
        when(urlDataProvider.getLoginInfo(any(UrlGetLoginRequest.class))).thenReturn(new RedirectStep(REDIRECT_URL));

        Map<String, BasicAuthenticationMean> authenticationMeans = new HashMap<>();
        ApiGetLoginDTO apiGetLoginDTO = new ApiGetLoginDTO("redirectUrl", "loginState", new AuthenticationMeansReference(CLIENT_ID, redirectUrlId), null, null);

        UrlGetLoginRequest urlGetLogin = new UrlGetLoginRequest(
                apiGetLoginDTO.getBaseClientRedirectUrl(),
                apiGetLoginDTO.getState(),
                apiGetLoginDTO.getAuthenticationMeansReference(),
                authenticationMeans,
                null,
                jcaSignerFactory.getForClientToken(clientUserToken),
                restTemplateManagerCache.getForClientProvider(clientUserToken, AIS, YOLT_PROVIDER, false, urlDataProvider.getVersion()),
                apiGetLoginDTO.getPsuIpAddress()
        );

        // when
        Step asyncLoginInfo = circuitBreakerAisService.getLoginInfo(clientUserToken, SITE_ID, YOLT_PROVIDER, redirectUrlId, vaultKeys, urlDataProvider, urlGetLogin);

        // then
        assertThat(asyncLoginInfo).isEqualTo(REDIRECT_STEP);
    }

    @Test
    public void shouldThrowProvidersCircuitBreakerExceptionWithCauseMissingAuthenticationMeansExceptionWithProperMessageForGetLoginInfoWhenExceptionWrapperByResilience() {
        // given
        Map<String, BasicAuthenticationMean> authenticationMeans = new HashMap<>();
        ApiGetLoginDTO apiGetLoginDTO = new ApiGetLoginDTO("redirectUrl", "loginState", new AuthenticationMeansReference(CLIENT_ID, redirectUrlId), null, null);

        UrlGetLoginRequest urlGetLogin = new UrlGetLoginRequest(
                apiGetLoginDTO.getBaseClientRedirectUrl(),
                apiGetLoginDTO.getState(),
                apiGetLoginDTO.getAuthenticationMeansReference(),
                authenticationMeans,
                null,
                jcaSignerFactory.getForClientToken(clientUserToken),
                restTemplateManagerCache.getForClientProvider(clientUserToken, AIS, YOLT_PROVIDER, false, urlDataProvider.getVersion()),
                apiGetLoginDTO.getPsuIpAddress()
        );

        when(urlDataProvider.getLoginInfo(any(UrlGetLoginRequest.class)))
                .thenThrow(MissingAuthenticationMeansException.class);

        // when
        ThrowableAssert.ThrowingCallable getLoginCallable = () -> circuitBreakerAisService.getLoginInfo(clientUserToken, SITE_ID, YOLT_PROVIDER, redirectUrlId, vaultKeys, urlDataProvider, urlGetLogin);

        // then
        assertThatThrownBy(getLoginCallable)
                .isExactlyInstanceOf(ProvidersCircuitBreakerException.class)
                .hasCauseInstanceOf(MissingAuthenticationMeansException.class)
                .hasMessageContaining("Get login failed.");
    }

    @Test
    public void shouldThrowProvidersCircuitBreakerExceptionWithCauseIllegalStateExceptionWithProperMessageForGetLoginInfoWhenExceptionWrapperByResilience() {
        // given
        Map<String, BasicAuthenticationMean> authenticationMeans = new HashMap<>();
        ApiGetLoginDTO apiGetLoginDTO = new ApiGetLoginDTO("redirectUrl", "loginState", new AuthenticationMeansReference(CLIENT_ID, redirectUrlId), null, null);

        UrlGetLoginRequest urlGetLogin = new UrlGetLoginRequest(
                apiGetLoginDTO.getBaseClientRedirectUrl(),
                apiGetLoginDTO.getState(),
                apiGetLoginDTO.getAuthenticationMeansReference(),
                authenticationMeans,
                null,
                jcaSignerFactory.getForClientToken(clientUserToken),
                restTemplateManagerCache.getForClientProvider(clientUserToken, AIS, YOLT_PROVIDER, false, urlDataProvider.getVersion()),
                apiGetLoginDTO.getPsuIpAddress());

        when(urlDataProvider.getLoginInfo(any(UrlGetLoginRequest.class))).thenThrow(IllegalStateException.class);

        // when
        ThrowableAssert.ThrowingCallable getLoginCallable = () -> circuitBreakerAisService.getLoginInfo(clientUserToken, SITE_ID, YOLT_PROVIDER, redirectUrlId, vaultKeys, urlDataProvider, urlGetLogin);

        // then
        assertThatThrownBy(getLoginCallable)
                .isExactlyInstanceOf(ProvidersCircuitBreakerException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Get login failed.");
    }

    @Test
    public void shouldNotThrowAnyExceptionForNotifyUserSiteDeleteWithCorrectData() {
        // given
        String externalConsentId = "external-consent-id";
        AuthenticationMeansReference authenticationMeansReference = new AuthenticationMeansReference(CLIENT_ID, CLIENT_REDIRECT_URL_ID_YOLT_APP);
        Map<String, BasicAuthenticationMean> authenticationMeans =
                clientAuthenticationMeansService.acquireAuthenticationMeans(YOLT_PROVIDER, AIS, authenticationMeansReference);
        JcaSigner signer = jcaSignerFactory.getForClientToken(clientUserToken);
        RestTemplateManager stableVersionRestTemplateManager = restTemplateManagerCache.getForClientProvider(clientUserToken, AIS,
                YOLT_PROVIDER, false, urlDataProvider.getVersion());
        UrlOnUserSiteDeleteRequest urlOnUserSiteDeleteRequest = new UrlOnUserSiteDeleteRequest(
                externalConsentId,
                authenticationMeansReference,
                authenticationMeans,
                null,
                signer,
                stableVersionRestTemplateManager,
                null
        );

        // when
        ThrowableAssert.ThrowingCallable notifyUserSiteDeleteCallable = () ->
                circuitBreakerAisService.notifyUserSiteDeleteWithFallbackToStableVersion(clientUserToken, SITE_ID, YOLT_PROVIDER, redirectUrlId,
                        urlOnUserSiteDeleteRequest, urlOnUserSiteDeleteRequest, urlDataProvider, urlDataProvider);

        // then
        assertThatCode(notifyUserSiteDeleteCallable)
                .doesNotThrowAnyException();
    }

    @Test
    public void shouldThrowProvidersCircuitBreakerExceptionWithCauseIllegalStateExceptionWithProperMessageForNotifyUserSiteDeleteWhenExceptionWrappedByResilience() throws Exception {
        // given
        doThrow(IllegalStateException.class)
                .when(urlDataProvider).onUserSiteDelete(any(UrlOnUserSiteDeleteRequest.class));

        String externalConsentId = "external-consent-id";
        AuthenticationMeansReference authenticationMeansReference = new AuthenticationMeansReference(CLIENT_ID, CLIENT_REDIRECT_URL_ID_YOLT_APP);
        Map<String, BasicAuthenticationMean> authenticationMeans =
                clientAuthenticationMeansService.acquireAuthenticationMeans(YOLT_PROVIDER, AIS, authenticationMeansReference);
        JcaSigner signer = jcaSignerFactory.getForClientToken(clientUserToken);
        RestTemplateManager stableVersionRestTemplateManager = restTemplateManagerCache.getForClientProvider(clientUserToken, AIS,
                YOLT_PROVIDER, false, urlDataProvider.getVersion());
        UrlOnUserSiteDeleteRequest urlOnUserSiteDeleteRequest = new UrlOnUserSiteDeleteRequest(
                externalConsentId,
                authenticationMeansReference,
                authenticationMeans,
                null,
                signer,
                stableVersionRestTemplateManager,
                null
        );

        // when
        ThrowableAssert.ThrowingCallable notifyUserSiteDeleteCallable = () ->
                circuitBreakerAisService.notifyUserSiteDeleteWithFallbackToStableVersion(clientUserToken, SITE_ID, YOLT_PROVIDER, redirectUrlId,
                        urlOnUserSiteDeleteRequest, urlOnUserSiteDeleteRequest, urlDataProvider, urlDataProvider);

        // then
        assertThatThrownBy(notifyUserSiteDeleteCallable)
                .isExactlyInstanceOf(ProvidersCircuitBreakerException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Delete of user site failed.");
    }

    @Test
    public void shouldThrowProvidersCircuitBreakerExceptionWithCauseMissingAuthenticationMeansExceptionWithProperMessageForNotifyUserSiteDeleteWhenExceptionWrappedByResilience() throws Exception {
        // given
        doThrow(MissingAuthenticationMeansException.class)
                .when(urlDataProvider).onUserSiteDelete(any(UrlOnUserSiteDeleteRequest.class));

        String externalConsentId = "external-consent-id";
        AuthenticationMeansReference authenticationMeansReference = new AuthenticationMeansReference(CLIENT_ID, CLIENT_REDIRECT_URL_ID_YOLT_APP);
        Map<String, BasicAuthenticationMean> authenticationMeans =
                clientAuthenticationMeansService.acquireAuthenticationMeans(YOLT_PROVIDER, AIS, authenticationMeansReference);
        JcaSigner signer = jcaSignerFactory.getForClientToken(clientUserToken);
        RestTemplateManager stableVersionRestTemplateManager = restTemplateManagerCache.getForClientProvider(clientUserToken, AIS,
                YOLT_PROVIDER, false, urlDataProvider.getVersion());
        UrlOnUserSiteDeleteRequest urlOnUserSiteDeleteRequest = new UrlOnUserSiteDeleteRequest(
                externalConsentId,
                authenticationMeansReference,
                authenticationMeans,
                null,
                signer,
                stableVersionRestTemplateManager,
                null
        );
        // when
        ThrowableAssert.ThrowingCallable notifyUserSiteDeleteCallable = () ->
                circuitBreakerAisService.notifyUserSiteDeleteWithFallbackToStableVersion(clientUserToken, SITE_ID, YOLT_PROVIDER, redirectUrlId,
                        urlOnUserSiteDeleteRequest, urlOnUserSiteDeleteRequest, urlDataProvider, urlDataProvider);

        assertThatThrownBy(notifyUserSiteDeleteCallable)
                .isExactlyInstanceOf(ProvidersCircuitBreakerException.class)
                .hasCauseInstanceOf(MissingAuthenticationMeansException.class)
                .hasMessageContaining("Delete of user site failed.");
    }

    @Test
    public void shouldThrowProvidersCircuitBreakerExceptionWithTimeoutExceptionCauseForGetLoginInfoWhenTimeout() {
        // given
        Map<String, BasicAuthenticationMean> authenticationMeans = new HashMap<>();
        ApiGetLoginDTO apiGetLoginDTO = new ApiGetLoginDTO("redirectUrl", "loginState", new AuthenticationMeansReference(CLIENT_ID, redirectUrlId), null, null);

        UrlGetLoginRequest urlGetLogin = new UrlGetLoginRequest(
                apiGetLoginDTO.getBaseClientRedirectUrl(),
                apiGetLoginDTO.getState(),
                apiGetLoginDTO.getAuthenticationMeansReference(),
                authenticationMeans,
                null,
                jcaSignerFactory.getForClientToken(clientUserToken),
                restTemplateManagerCache.getForClientProvider(clientUserToken, AIS, YOLT_PROVIDER, false, urlDataProvider.getVersion()),
                apiGetLoginDTO.getPsuIpAddress()
        );
        when(urlDataProvider.getLoginInfo(urlGetLogin)).thenThrow(new RuntimeException(new TimeoutException("Timeout")));

        // when
        ThrowableAssert.ThrowingCallable getLoginInfoCallable = () -> circuitBreakerAisService.getLoginInfo(clientUserToken, SITE_ID, YOLT_PROVIDER, redirectUrlId, vaultKeys, urlDataProvider, urlGetLogin);
        assertThatThrownBy(getLoginInfoCallable)
                .isExactlyInstanceOf(ProvidersCircuitBreakerException.class);
    }

    @Test
    public void shouldLogBasicExceptionInfoOnFetchDataFailureWithoutDetailedExceptionData() throws Exception {
        Appender<ILoggingEvent> mockAppender = mock(Appender.class);
        ArgumentCaptor<ILoggingEvent> captorLoggingEvent = ArgumentCaptor.forClass(ILoggingEvent.class);
        Logger logger = (Logger) LoggerFactory.getLogger(CircuitBreakerAisService.class);
        logger.setLevel(Level.WARN);
        logger.addAppender(mockAppender);

        String encryptedAccessMeans = AesEncryptionUtil.encrypt(MY_ACCESS_MEANS, secretKey);
        AccessMeansDTO encryptedAccessMeansDTO = new AccessMeansDTO(USER_ID, encryptedAccessMeans,
                new Date(), new Date());
        ApiFetchDataDTO apiFetchDataDTO = new ApiFetchDataDTO(USER_ID, Instant.now(), encryptedAccessMeansDTO, new AuthenticationMeansReference(CLIENT_ID, redirectUrlId), null, null, null, null, new UserSiteDataFetchInformation(null, USER_SITE_ID, null, Collections.emptyList(), Collections.emptyList()));
        ReflectionTestUtils.setField(apiFetchDataDTO, "providerRequestId", providerRequestId);

        when(urlDataProvider.fetchData(any(UrlFetchDataRequest.class))).thenThrow(
                new ProviderFetchDataException()
        );

        // when
        circuitBreakerAisService.fetchDataAsync(clientUserToken,
                SITE_ID,
                YOLT_PROVIDER,
                REDIRECT_URL_ID,
                urlDataProvider,
                getUrlFetchDataRequest(apiFetchDataDTO),
                apiFetchDataDTO,
                accountsFilterService,
                transactionsDataLimiter,
                accountsPostProcessingService,
                UUID.randomUUID());

        // then
        verify(mockAppender, times(1)).doAppend(captorLoggingEvent.capture());
        ILoggingEvent exceptionLogLine = captorLoggingEvent.getAllValues().get(0);
        assertThat(exceptionLogLine.getFormattedMessage())
                .contains("Exception while provider " + YOLT_PROVIDER_NAME + " is fetching accounts and transactions: Failed fetching data")
                .doesNotContain("details in stacktrace");
        verifyNoMoreInteractions(mockAppender);
        logger.detachAppender(mockAppender);
    }

    private UrlFetchDataRequest getUrlFetchDataRequest(ApiFetchDataDTO apiFetchDataDTO) {
        return new UrlFetchDataRequest(
                apiFetchDataDTO.getUserId(),
                USER_SITE_ID,
                apiFetchDataDTO.getTransactionsFetchStartTime(),
                apiFetchDataDTO.getAccessMeans(),
                clientAuthenticationMeansService.acquireAuthenticationMeans(YOLT_PROVIDER, AIS, apiFetchDataDTO.getAuthenticationMeansReference()),
                jcaSignerFactory.getForClientToken(clientUserToken),
                restTemplateManagerCache.getForClientProvider(clientUserToken, AIS, YOLT_PROVIDER, false, urlDataProvider.getVersion()),
                apiFetchDataDTO.getAuthenticationMeansReference(),
                apiFetchDataDTO.getPsuIpAddress()
        );
    }
}