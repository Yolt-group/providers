package com.yolt.providers.web.service;

import com.yolt.providers.common.ais.url.UrlGetLoginRequest;
import com.yolt.providers.common.domain.authenticationmeans.BasicAuthenticationMean;
import com.yolt.providers.common.domain.dynamic.step.RedirectStep;
import com.yolt.providers.common.providerinterface.UrlDataProvider;
import com.yolt.providers.web.authenticationmeans.ClientAuthenticationMeansService;
import com.yolt.providers.web.controller.dto.*;
import com.yolt.providers.web.cryptography.signing.JcaSignerFactory;
import com.yolt.providers.web.cryptography.transport.MutualTLSRestTemplateManagerCache;
import com.yolt.providers.web.encryption.AesEncryptionUtil;
import com.yolt.providers.web.service.circuitbreaker.CircuitBreakerAisService;
import com.yolt.providers.web.service.configuration.VersionType;
import com.yolt.providers.web.service.dto.FetchDataResultDTO;
import com.yolt.providers.web.sitedetails.dto.AisProviderSiteData;
import com.yolt.providers.web.sitedetails.sites.SiteDetailsService;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.clienttokens.ClientUserToken;
import nl.ing.lovebird.providerdomain.ProviderAccountDTO;
import nl.ing.lovebird.providerdomain.ProviderAccountNumberDTO;
import nl.ing.lovebird.providerdomain.ProviderAccountNumberDTO.Scheme;
import nl.ing.lovebird.providershared.AccessMeansDTO;
import nl.ing.lovebird.providershared.ProviderServiceResponseStatus;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import nl.ing.lovebird.providershared.form.ExtendedProviderServiceResponseStatus;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.yolt.providers.common.providerdetail.dto.AisSiteDetails.site;
import static com.yolt.providers.common.providerdetail.dto.CountryCode.GB;
import static com.yolt.providers.common.providerdetail.dto.ProviderBehaviour.STATE;
import static com.yolt.providers.common.providerdetail.dto.ProviderType.DIRECT_CONNECTION;
import static java.util.List.of;
import static nl.ing.lovebird.providerdomain.AccountType.*;
import static nl.ing.lovebird.providerdomain.ServiceType.AIS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProviderServiceTest {

    private static final ProviderAccountNumberDTO PROVIDER_ACCOUNT = new ProviderAccountNumberDTO(Scheme.IBAN, "id123");
    private static final String YOLT_PROVIDER_NAME = "YOLT_PROVIDER";

    @Mock
    private ProviderFactoryService providerFactoryService;
    @Mock
    private ClientAuthenticationMeansService clientAuthenticationMeansService;
    @Mock
    private ProviderServiceResponseProducer providerServiceResponseProducer;
    @Mock
    private JcaSignerFactory jcaSignerFactory;
    @Mock
    private MutualTLSRestTemplateManagerCache templateManagerCache;
    @Mock
    private AccountsProducer accountsProducer;
    @Mock
    private CircuitBreakerAisService circuitBreakerAisService;
    @Mock
    private SiteDetailsService siteDetailsService;

    @InjectMocks
    private ProviderService service;

    @Mock
    private UrlDataProvider urlDataProvider;

    private static final String secretKey = "a3f60fafc948035382fbe9ce7b4535c4";
    private static final UUID SITE_ID = UUID.fromString("571c2c82-a2f0-4c3c-b110-627020f58351");
    private static final UUID USER_ID = UUID.fromString("2068f360-0da5-4e74-ab53-2817d04c6242");
    private static final UUID USER_SITE_ID = UUID.fromString("021b71a5-8905-42c6-a223-eea6082bd355");
    private static final UUID CLIENT_ID = UUID.fromString("4241ac58-f6a0-449e-a649-281e55553bcd");
    private static final UUID redirectUrlId = UUID.fromString("ebe08670-e9c2-4fd5-b65c-cbfc4abc26f3");
    private static final UUID providerRequestId = UUID.fromString("999d1dde-b7bd-482b-b9d0-a2a75ca6659c");
    private static final ClientUserToken CLIENT_USER_TOKEN = mock(ClientUserToken.class);
    private static final String MY_ACCESS_MEANS = "MY_ACCESS_MEANS";

    @Test
    public void shouldThrowIllegalStateExceptionWhenThereIsNoSiteWithProvidedKeyInList() {
        // given
        when(providerFactoryService.getProvider(any(String.class), eq(UrlDataProvider.class), eq(AIS), any(VersionType.class))).thenReturn(urlDataProvider);
        AccessMeansDTO encryptedAccessMeansDTO = new AccessMeansDTO(UUID.randomUUID(), MY_ACCESS_MEANS,
                new Date(), new Date());
        RefreshAccessMeansDTO refreshAccessMeansDTO = new RefreshAccessMeansDTO(encryptedAccessMeansDTO, new AuthenticationMeansReference(CLIENT_ID, null), null, Instant.now().minus(30, ChronoUnit.DAYS));
        Map<String, BasicAuthenticationMean> authenticationMeans = new HashMap<>();
        when(clientAuthenticationMeansService.acquireAuthenticationMeans(any(), any(), any())).thenReturn(authenticationMeans);
        UUID mockedSiteId = UUID.randomUUID();
        AisProviderSiteData mockSiteData = new AisProviderSiteData(List.of(site(mockedSiteId.toString(), "Provider name", YOLT_PROVIDER_NAME, DIRECT_CONNECTION, of(STATE), of(CURRENT_ACCOUNT, CREDIT_CARD, SAVINGS_ACCOUNT), of(GB)).build()));
        when(siteDetailsService.getAisProviderSitesDataBySiteId()).thenReturn(Map.of(mockedSiteId, mockSiteData));

        // when
        ThrowableAssert.ThrowingCallable callable = () -> service.refreshAccessMeans(YOLT_PROVIDER_NAME + "123", refreshAccessMeansDTO, CLIENT_USER_TOKEN, SITE_ID, false);

        // then
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(callable);
    }

    @Test
    public void shouldSendFetchDataResultToKafkaTopicForFetchDataAsyncWithCorrectData() {
        // given
        String encryptedAccessMeans = AesEncryptionUtil.encrypt(MY_ACCESS_MEANS, secretKey);
        AccessMeansDTO encryptedAccessMeansDTO = new AccessMeansDTO(USER_ID, encryptedAccessMeans,
                new Date(), new Date());
        ApiFetchDataDTO apiFetchDataDTO = new ApiFetchDataDTO(USER_ID, Instant.now(), encryptedAccessMeansDTO, new AuthenticationMeansReference(CLIENT_ID, redirectUrlId), providerRequestId, null, null, null, new UserSiteDataFetchInformation(null, USER_SITE_ID, null, Collections.emptyList(), Collections.emptyList()));

        when(providerFactoryService.getProvider(any(String.class), eq(UrlDataProvider.class), eq(AIS), any(VersionType.class))).thenReturn(urlDataProvider);
        ProviderServiceResponseDTO responseDTO = new ProviderServiceResponseDTO(
                Collections.singletonList(mock(ProviderAccountDTO.class)),
                ProviderServiceResponseStatus.FINISHED,
                providerRequestId);

        when(circuitBreakerAisService.fetchDataAsync(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(responseDTO);

        // when
        service.fetchDataAsync(YOLT_PROVIDER_NAME, apiFetchDataDTO, SITE_ID, CLIENT_USER_TOKEN, false);

        // then
        verify(providerServiceResponseProducer).sendMessage(eq(new FetchDataResultDTO(providerRequestId, ProviderServiceResponseStatus.FINISHED)), eq(CLIENT_USER_TOKEN));
    }

    @Test
    public void shouldCallGetLoginInfoOnUrlDataProviderForGetLoginInfoWithoutPassingExternalConsentIdIfAvailable() {
        // given
        ApiGetLoginDTO apiGetLoginDTO = new ApiGetLoginDTO(
                "redirectUrl",
                "loginState",
                new AuthenticationMeansReference(CLIENT_ID, redirectUrlId),
                null,
                null);

        when(providerFactoryService.getProvider(any(String.class), eq(UrlDataProvider.class), eq(AIS), any(VersionType.class))).thenReturn(urlDataProvider);
        when(circuitBreakerAisService.getLoginInfo(any(), any(), any(), any(), any(), any(), any())).thenReturn(mock(RedirectStep.class));

        // when
        service.getLoginInfo(YOLT_PROVIDER_NAME, apiGetLoginDTO, CLIENT_USER_TOKEN, SITE_ID, false);

        // then
        ArgumentCaptor<UrlGetLoginRequest> argumentCaptor = ArgumentCaptor.forClass(UrlGetLoginRequest.class);
        ArgumentCaptor<Object> dummyCaptor = ArgumentCaptor.forClass(Object.class);

        verify(circuitBreakerAisService).getLoginInfo(
                (ClientToken) dummyCaptor.capture(),
                (UUID) dummyCaptor.capture(),
                (String) dummyCaptor.capture(),
                (UUID) dummyCaptor.capture(),
                (ProviderVaultKeys) dummyCaptor.capture(),
                (UrlDataProvider) dummyCaptor.capture(),
                argumentCaptor.capture());

        UrlGetLoginRequest passedRequest = argumentCaptor.getValue();
        assertThat(passedRequest.getExternalConsentId()).isNull();
    }

    @Test
    public void shouldSendSiteActionNeededMessageToKafkaTopicForFetchDataAsyncWhenSiteActionNeededRuntimeException() {
        // given
        String encryptedAccessMeans = AesEncryptionUtil.encrypt(MY_ACCESS_MEANS, secretKey);
        AccessMeansDTO encryptedAccessMeansDTO = new AccessMeansDTO(USER_ID, encryptedAccessMeans,
                new Date(), new Date());
        ApiFetchDataDTO apiFetchDataDTO = new ApiFetchDataDTO(USER_ID, Instant.now(), encryptedAccessMeansDTO, new AuthenticationMeansReference(CLIENT_ID, redirectUrlId), null, null, null, null, new UserSiteDataFetchInformation(null, USER_SITE_ID, null, Collections.emptyList(), Collections.emptyList()));
        ReflectionTestUtils.setField(apiFetchDataDTO, "providerRequestId", providerRequestId);

        ProviderServiceResponseDTO responseDTO = new ProviderServiceResponseDTO(
                Collections.emptyList(),
                ExtendedProviderServiceResponseStatus.SITE_ACTION_NEEDED,
                providerRequestId);

        when(providerFactoryService.getProvider(any(String.class), eq(UrlDataProvider.class), eq(AIS), any(VersionType.class))).thenReturn(urlDataProvider);
        when(circuitBreakerAisService.fetchDataAsync(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(responseDTO);

        // when
        service.fetchDataAsync(YOLT_PROVIDER_NAME, apiFetchDataDTO, SITE_ID, CLIENT_USER_TOKEN, false);

        // then
        verify(providerServiceResponseProducer).sendMessage(eq(new FetchDataResultDTO(providerRequestId, ExtendedProviderServiceResponseStatus.SITE_ACTION_NEEDED)), eq(CLIENT_USER_TOKEN));
    }

    @Test
    public void shouldSendNoSupportedAccountsMessageToKafkaTopicForFetchDataAsyncWhenNoSupportedAccounts() {
        // given
        UUID activityId = UUID.randomUUID();
        String encryptedAccessMeans = AesEncryptionUtil.encrypt(MY_ACCESS_MEANS, secretKey);
        AccessMeansDTO encryptedAccessMeansDTO = new AccessMeansDTO(USER_ID, encryptedAccessMeans,
                new Date(), new Date());
        ApiFetchDataDTO apiFetchDataDTO = new ApiFetchDataDTO(USER_ID, Instant.now(), encryptedAccessMeansDTO, new AuthenticationMeansReference(CLIENT_ID, redirectUrlId), null, null, activityId, null, new UserSiteDataFetchInformation(null, USER_SITE_ID, SITE_ID, Collections.emptyList(), Collections.emptyList()));

        ProviderServiceResponseDTO responseDTO = new ProviderServiceResponseDTO(
                Collections.emptyList(),
                ProviderServiceResponseStatus.FINISHED,
                providerRequestId);

        when(providerFactoryService.getProvider(any(String.class), eq(UrlDataProvider.class), eq(AIS), any(VersionType.class))).thenReturn(urlDataProvider);

        when(circuitBreakerAisService.fetchDataAsync(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(responseDTO);

        // when
        service.fetchDataAsync(YOLT_PROVIDER_NAME, apiFetchDataDTO, SITE_ID, CLIENT_USER_TOKEN, false);

        // then
        verify(providerServiceResponseProducer).sendNoSupportedAccountsMessage(eq(USER_SITE_ID), eq(CLIENT_USER_TOKEN));
        // We need to send an empty list so health (the orchestrator) 'knows' that the flow is still succesfull. This is important because
        // it waits for all the usersites to complete. It waits for either a data-message (ingestionFinished from accounts-and-transactions)
        // or a failure event (from site-management)
        verify(accountsProducer).publishAccountAndTransactions(eq(activityId), eq(USER_SITE_ID), eq(SITE_ID), argThat(List::isEmpty), eq(CLIENT_USER_TOKEN), anyString());
    }

    @Test
    public void shouldSendBackPressureRequestMessageToKafkaTopicForFetchDataAsyncWhenBackPressureRequestException() {
        // given
        String encryptedAccessMeans = AesEncryptionUtil.encrypt(MY_ACCESS_MEANS, secretKey);
        AccessMeansDTO encryptedAccessMeansDTO = new AccessMeansDTO(USER_ID, encryptedAccessMeans,
                new Date(), new Date());
        ApiFetchDataDTO apiFetchDataDTO = new ApiFetchDataDTO(USER_ID, Instant.now(), encryptedAccessMeansDTO, new AuthenticationMeansReference(CLIENT_ID, redirectUrlId), null, null, null, null, new UserSiteDataFetchInformation(null, USER_SITE_ID, null, Collections.emptyList(), Collections.emptyList()));
        ReflectionTestUtils.setField(apiFetchDataDTO, "providerRequestId", providerRequestId);

        ProviderServiceResponseDTO responseDTO = new ProviderServiceResponseDTO(
                Collections.emptyList(),
                ProviderServiceResponseStatus.BACK_PRESSURE_REQUEST,
                providerRequestId);

        when(providerFactoryService.getProvider(any(String.class), eq(UrlDataProvider.class), eq(AIS), any(VersionType.class))).thenReturn(urlDataProvider);

        when(circuitBreakerAisService.fetchDataAsync(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(responseDTO);

        // when
        service.fetchDataAsync(YOLT_PROVIDER_NAME, apiFetchDataDTO, SITE_ID, CLIENT_USER_TOKEN, false);

        // then
        verify(providerServiceResponseProducer).sendMessage(eq(new FetchDataResultDTO(providerRequestId, ProviderServiceResponseStatus.BACK_PRESSURE_REQUEST)), eq(CLIENT_USER_TOKEN));
    }
}
