package com.yolt.providers.web.service;

import com.yolt.providers.common.pis.common.PeriodicPaymentExecutionRule;
import com.yolt.providers.common.pis.common.PeriodicPaymentFrequency;
import com.yolt.providers.common.pis.common.SepaPeriodicPaymentInfo;
import com.yolt.providers.common.pis.sepa.*;
import com.yolt.providers.common.providerinterface.SepaPaymentProvider;
import com.yolt.providers.web.authenticationmeans.ClientAuthenticationMeansService;
import com.yolt.providers.web.controller.dto.*;
import com.yolt.providers.web.cryptography.signing.JcaSignerFactory;
import com.yolt.providers.web.cryptography.transport.MutualTLSRestTemplateManagerCache;
import com.yolt.providers.web.encryption.AesEncryptionUtil;
import com.yolt.providers.web.exception.IncorrectDateException;
import com.yolt.providers.web.service.circuitbreaker.CircuitBreakerSecuredSepaPaymentService;
import com.yolt.providers.web.service.configuration.VersionType;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static nl.ing.lovebird.providerdomain.ServiceType.PIS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProviderSepaPaymentServiceTest {

    private static final UUID SITE_ID = UUID.fromString("571c2c82-a2f0-4c3c-b110-627020f58351");
    private static final UUID REDIRECT_URL_ID = UUID.fromString("171c2c82-a4f0-4c3c-b110-627020f56351");
    private static final UUID CLIENT_ID = UUID.fromString("f46fd8af-0e92-420d-be1e-904acd1086d4");
    private static final String YOLT_PROVIDER_SEPA_PIS_NAME = "YOLT_PROVIDER_SEPA_PIS";
    private static final String ENCRYPTION_KEY = "a3f60fafc948035382fbe9ce7b4535c4";
    private static final String DECRYPTED_PROVIDER_STATE = "ProviderState";
    private static final String ENCRYPTED_PROVIDER_STATE = AesEncryptionUtil.encrypt(DECRYPTED_PROVIDER_STATE, ENCRYPTION_KEY);

    @Mock
    private ProviderFactoryService providerFactoryService;
    @Mock
    private SepaPaymentProvider sepaPaymentProvider;
    @Mock
    private ClientAuthenticationMeansService clientAuthenticationMeansService;
    @Mock
    private JcaSignerFactory jcaSignerFactory;
    @Mock
    private MutualTLSRestTemplateManagerCache mutualTLSRestTemplateManagerCache;
    @Mock
    private ProviderVaultKeys vaultKeys;
    @Mock
    private Clock clock;
    @Captor
    private ArgumentCaptor<VersionType> versionTypeCaptor;

    @InjectMocks
    private ProviderSepaPaymentService providerSepaPaymentService;

    @Mock
    private ClientToken clientToken;

    @Mock
    private CircuitBreakerSecuredSepaPaymentService cbsSepaPaymentService;

    @Test
    public void shouldCallInitiateSinglePaymentWithCorrectData() {
        // given
        when(providerFactoryService.getProvider(eq(YOLT_PROVIDER_SEPA_PIS_NAME), eq(SepaPaymentProvider.class), eq(PIS), any(VersionType.class)))
                .thenReturn(sepaPaymentProvider);
        ExternalInitiateSepaPaymentRequestDTO initiateSepaPaymentRequestDTO = new ExternalInitiateSepaPaymentRequestDTO(minimalValidSingleRequest(),
                "state", new AuthenticationMeansReference(CLIENT_ID, REDIRECT_URL_ID), "http://redirect.url"
                , null);
        when(cbsSepaPaymentService.initiateSinglePayment(eq(clientToken), eq(SITE_ID), eq(YOLT_PROVIDER_SEPA_PIS_NAME), eq(REDIRECT_URL_ID), eq(sepaPaymentProvider),
                any(InitiatePaymentRequest.class))).thenReturn(new LoginUrlAndStateDTO("loginUrl", DECRYPTED_PROVIDER_STATE));
        when(vaultKeys.getEncryptionKey()).thenReturn(ENCRYPTION_KEY);

        // when
        ExternalLoginUrlAndStateDTO response = providerSepaPaymentService.initiateSinglePayment(YOLT_PROVIDER_SEPA_PIS_NAME, initiateSepaPaymentRequestDTO,
                clientToken, SITE_ID, false);

        // then
        ArgumentCaptor<InitiatePaymentRequest> captor = ArgumentCaptor.forClass(InitiatePaymentRequest.class);
        verify(cbsSepaPaymentService).initiateSinglePayment(eq(clientToken), eq(SITE_ID), eq(YOLT_PROVIDER_SEPA_PIS_NAME), eq(REDIRECT_URL_ID), eq(sepaPaymentProvider), captor.capture());

        InitiatePaymentRequest actual = captor.getValue();
        assertThat(actual.getRequestDTO()).isEqualTo(expectedSingleRequest());
        assertThat(actual.getBaseClientRedirectUrl()).isEqualTo(initiateSepaPaymentRequestDTO.getBaseClientRedirectUrl());
        assertThat(actual.getState()).isEqualTo(initiateSepaPaymentRequestDTO.getState());
        assertThat(response)
                .extracting(ExternalLoginUrlAndStateDTO::getProviderState)
                .returns(DECRYPTED_PROVIDER_STATE, it -> AesEncryptionUtil.decrypt(it, ENCRYPTION_KEY));
    }

    @Test
    public void shouldCallInitiateScheduledPaymentWithCorrectData() {
        // given
        when(clock.instant()).thenReturn(Instant.now());
        when(clock.getZone()).thenReturn(ZoneId.of("UTC"));
        when(providerFactoryService.getProvider(eq(YOLT_PROVIDER_SEPA_PIS_NAME), eq(SepaPaymentProvider.class), eq(PIS), any(VersionType.class)))
                .thenReturn(sepaPaymentProvider);
        ExternalInitiateSepaPaymentRequestDTO initiateSepaPaymentRequestDTO = new ExternalInitiateSepaPaymentRequestDTO(minimalValidFutureRequest(),
                "state", new AuthenticationMeansReference(CLIENT_ID, REDIRECT_URL_ID), "http://redirect.url"
                , null);
        when(cbsSepaPaymentService.initiateScheduledPayment(eq(clientToken), eq(SITE_ID), eq(YOLT_PROVIDER_SEPA_PIS_NAME), eq(REDIRECT_URL_ID), eq(sepaPaymentProvider),
                any(InitiatePaymentRequest.class))).thenReturn(new LoginUrlAndStateDTO("loginUrl", DECRYPTED_PROVIDER_STATE));
        when(vaultKeys.getEncryptionKey()).thenReturn(ENCRYPTION_KEY);

        // when
        ExternalLoginUrlAndStateDTO response = providerSepaPaymentService.initiateSinglePayment(YOLT_PROVIDER_SEPA_PIS_NAME, initiateSepaPaymentRequestDTO,
                clientToken, SITE_ID, false);

        // then
        ArgumentCaptor<InitiatePaymentRequest> captor = ArgumentCaptor.forClass(InitiatePaymentRequest.class);
        verify(cbsSepaPaymentService).initiateScheduledPayment(eq(clientToken), eq(SITE_ID), eq(YOLT_PROVIDER_SEPA_PIS_NAME), eq(REDIRECT_URL_ID), eq(sepaPaymentProvider), captor.capture());

        InitiatePaymentRequest actual = captor.getValue();
        assertThat(actual.getRequestDTO()).isEqualTo(expectedFutureRequest());
        assertThat(actual.getBaseClientRedirectUrl()).isEqualTo(initiateSepaPaymentRequestDTO.getBaseClientRedirectUrl());
        assertThat(actual.getState()).isEqualTo(initiateSepaPaymentRequestDTO.getState());
        assertThat(response)
                .extracting(ExternalLoginUrlAndStateDTO::getProviderState)
                .returns(DECRYPTED_PROVIDER_STATE, it -> AesEncryptionUtil.decrypt(it, ENCRYPTION_KEY));
    }

    @Test
    public void shouldThrowExceptionWhenReceivedPaymentCallWithTodaysDate() {
        // given
        when(clock.instant()).thenReturn(Instant.now());
        when(clock.getZone()).thenReturn(ZoneId.of("UTC"));
        when(providerFactoryService.getProvider(eq(YOLT_PROVIDER_SEPA_PIS_NAME), eq(SepaPaymentProvider.class), eq(PIS), any(VersionType.class)))
                .thenReturn(sepaPaymentProvider);
        ExternalInitiateSepaPaymentRequestDTO initiateSepaPaymentRequestDTO = new ExternalInitiateSepaPaymentRequestDTO(minimalRequestWithTodaysDate(),
                "state", new AuthenticationMeansReference(CLIENT_ID, REDIRECT_URL_ID), "http://redirect.url"
                , null);

        ThrowableAssert.ThrowingCallable paymentCallable = () -> providerSepaPaymentService.initiateSinglePayment(YOLT_PROVIDER_SEPA_PIS_NAME, initiateSepaPaymentRequestDTO,
                clientToken, SITE_ID, false);

        // then
        assertThatThrownBy(paymentCallable)
                .isInstanceOf(IncorrectDateException.class)
                .hasMessage("Execution date should not be today");
    }

    @Test
    public void shouldCallInitiatePeriodicPaymentWithCorrectData() {
        // given
        when(providerFactoryService.getProvider(eq(YOLT_PROVIDER_SEPA_PIS_NAME), eq(SepaPaymentProvider.class), eq(PIS), any(VersionType.class)))
                .thenReturn(sepaPaymentProvider);
        InitiateSepaPaymentRequestDTO initiateSepaPaymentRequestDTO = new InitiateSepaPaymentRequestDTO(minimalValidPeriodicRequest(),
                "state", new AuthenticationMeansReference(CLIENT_ID, REDIRECT_URL_ID), "http://redirect.url"
                , null);
        when(cbsSepaPaymentService.initiatePeriodicPayment(eq(clientToken), eq(SITE_ID), eq(YOLT_PROVIDER_SEPA_PIS_NAME), eq(REDIRECT_URL_ID), eq(sepaPaymentProvider),
                any(InitiatePaymentRequest.class))).thenReturn(new LoginUrlAndStateDTO("loginUrl", DECRYPTED_PROVIDER_STATE));
        when(vaultKeys.getEncryptionKey()).thenReturn(ENCRYPTION_KEY);

        // when
        LoginUrlAndStateDTO response = providerSepaPaymentService.initiatePeriodicPayment(YOLT_PROVIDER_SEPA_PIS_NAME, initiateSepaPaymentRequestDTO,
                clientToken, SITE_ID, false);

        // then
        ArgumentCaptor<InitiatePaymentRequest> captor = ArgumentCaptor.forClass(InitiatePaymentRequest.class);
        verify(cbsSepaPaymentService).initiatePeriodicPayment(eq(clientToken), eq(SITE_ID), eq(YOLT_PROVIDER_SEPA_PIS_NAME), eq(REDIRECT_URL_ID), eq(sepaPaymentProvider), captor.capture());

        InitiatePaymentRequest actual = captor.getValue();
        assertThat(actual.getRequestDTO()).isEqualTo(minimalValidPeriodicRequest());
        assertThat(actual.getBaseClientRedirectUrl()).isEqualTo(initiateSepaPaymentRequestDTO.getBaseClientRedirectUrl());
        assertThat(actual.getState()).isEqualTo(initiateSepaPaymentRequestDTO.getState());
        assertThat(response)
                .extracting(LoginUrlAndStateDTO::getProviderState)
                .returns(DECRYPTED_PROVIDER_STATE, it -> AesEncryptionUtil.decrypt(it, ENCRYPTION_KEY));
    }

    @Test
    public void shouldCallSubmitPaymentOnSepaSinglePaymentProviderForSubmitPaymentWithCorrectData() {
        // given
        when(vaultKeys.getEncryptionKey()).thenReturn(ENCRYPTION_KEY);
        when(providerFactoryService.getProvider(eq(YOLT_PROVIDER_SEPA_PIS_NAME), eq(SepaPaymentProvider.class), eq(PIS), any(VersionType.class)))
                .thenReturn(sepaPaymentProvider);
        when(cbsSepaPaymentService.submitSinglePayment(any(ClientToken.class), any(UUID.class), anyString(), any(UUID.class), any(SepaPaymentProvider.class), any(SubmitPaymentRequest.class)))
                .thenReturn(new SepaPaymentStatusResponseDTO(DECRYPTED_PROVIDER_STATE, "paymentId", null));
        SubmitSepaPaymentRequestDTO submitSepaPaymentRequestDTO = new SubmitSepaPaymentRequestDTO(ENCRYPTED_PROVIDER_STATE,
                new AuthenticationMeansReference(CLIENT_ID, REDIRECT_URL_ID), "redirectUrl", null);

        // when
        SepaPaymentStatusResponseDTO response = providerSepaPaymentService.submitSinglePayment(YOLT_PROVIDER_SEPA_PIS_NAME, submitSepaPaymentRequestDTO,
                clientToken, SITE_ID, false);

        // then
        ArgumentCaptor<SubmitPaymentRequest> captor = ArgumentCaptor.forClass(SubmitPaymentRequest.class);
        verify(cbsSepaPaymentService).submitSinglePayment(eq(clientToken), eq(SITE_ID), eq(YOLT_PROVIDER_SEPA_PIS_NAME), eq(REDIRECT_URL_ID), eq(sepaPaymentProvider), captor.capture());

        SubmitPaymentRequest actual = captor.getValue();
        assertThat(actual.getProviderState()).isEqualTo(DECRYPTED_PROVIDER_STATE);
        assertThat(response)
                .extracting(SepaPaymentStatusResponseDTO::getProviderState)
                .returns(DECRYPTED_PROVIDER_STATE, it -> AesEncryptionUtil.decrypt(it, ENCRYPTION_KEY));
    }

    @Test
    public void shouldCallSubmitPaymentOnSepaPeriodicPaymentProviderForSubmitPaymentWithCorrectData() {
        // given
        when(vaultKeys.getEncryptionKey()).thenReturn(ENCRYPTION_KEY);
        when(providerFactoryService.getProvider(eq(YOLT_PROVIDER_SEPA_PIS_NAME), eq(SepaPaymentProvider.class), eq(PIS), any(VersionType.class)))
                .thenReturn(sepaPaymentProvider);
        when(cbsSepaPaymentService.submitPeriodicPayment(any(ClientToken.class), any(UUID.class), anyString(), any(UUID.class), any(SepaPaymentProvider.class), any(SubmitPaymentRequest.class)))
                .thenReturn(new SepaPaymentStatusResponseDTO(DECRYPTED_PROVIDER_STATE, "paymentId", null));
        SubmitSepaPaymentRequestDTO submitSepaPaymentRequestDTO = new SubmitSepaPaymentRequestDTO(ENCRYPTED_PROVIDER_STATE,
                new AuthenticationMeansReference(CLIENT_ID, REDIRECT_URL_ID), "redirectUrl", null);

        // when
        SepaPaymentStatusResponseDTO response = providerSepaPaymentService.submitPeriodicPayment(YOLT_PROVIDER_SEPA_PIS_NAME, submitSepaPaymentRequestDTO,
                clientToken, SITE_ID, false);

        // then
        ArgumentCaptor<SubmitPaymentRequest> captor = ArgumentCaptor.forClass(SubmitPaymentRequest.class);
        verify(cbsSepaPaymentService).submitPeriodicPayment(eq(clientToken), eq(SITE_ID), eq(YOLT_PROVIDER_SEPA_PIS_NAME), eq(REDIRECT_URL_ID), eq(sepaPaymentProvider), captor.capture());

        SubmitPaymentRequest actual = captor.getValue();
        assertThat(actual.getProviderState()).isEqualTo(DECRYPTED_PROVIDER_STATE);
        assertThat(response)
                .extracting(SepaPaymentStatusResponseDTO::getProviderState)
                .returns(DECRYPTED_PROVIDER_STATE, it -> AesEncryptionUtil.decrypt(it, ENCRYPTION_KEY));
    }

    @Test
    public void shouldUseExperimentalProviderVersionWhenForceExperimentalFlagIsSetToTrue() {
        //given
        GetPaymentStatusRequestDTO getPaymentStatusRequestDTO = new GetPaymentStatusRequestDTO(new AuthenticationMeansReference(CLIENT_ID, REDIRECT_URL_ID), "paymentId", null, null);
        when(providerFactoryService.getProvider(any(), any(), any(), versionTypeCaptor.capture())).thenReturn(sepaPaymentProvider);
        when(cbsSepaPaymentService.getPaymentStatus(any(ClientToken.class), any(UUID.class), anyString(), any(UUID.class), any(SepaPaymentProvider.class), any(GetStatusRequest.class)))
                .thenReturn(new SepaPaymentStatusResponseDTO("paymentId"));
        when(vaultKeys.getEncryptionKey()).thenReturn(ENCRYPTION_KEY);

        // when
        SepaPaymentStatusResponseDTO response = providerSepaPaymentService.getPaymentStatus(YOLT_PROVIDER_SEPA_PIS_NAME, getPaymentStatusRequestDTO,
                clientToken, SITE_ID, true);

        //then
        assertThat(VersionType.EXPERIMENTAL).isEqualTo(versionTypeCaptor.getValue());
    }

    @Test
    public void shouldUseStableProviderVersionWhenForceExperimentalFlagIsSetToFalse() {
        //given
        GetPaymentStatusRequestDTO getPaymentStatusRequestDTO = new GetPaymentStatusRequestDTO(new AuthenticationMeansReference(CLIENT_ID, REDIRECT_URL_ID), "paymentId", null, null);
        when(providerFactoryService.getProvider(any(), any(), any(), versionTypeCaptor.capture())).thenReturn(sepaPaymentProvider);
        when(cbsSepaPaymentService.getPaymentStatus(any(ClientToken.class), any(UUID.class), anyString(), any(UUID.class), any(SepaPaymentProvider.class), any(GetStatusRequest.class)))
                .thenReturn(new SepaPaymentStatusResponseDTO("paymentId"));
        when(vaultKeys.getEncryptionKey()).thenReturn(ENCRYPTION_KEY);

        // when
        providerSepaPaymentService.getPaymentStatus(YOLT_PROVIDER_SEPA_PIS_NAME, getPaymentStatusRequestDTO,
                clientToken, SITE_ID, false);

        //then
        assertThat(VersionType.STABLE).isEqualTo(versionTypeCaptor.getValue());
    }

    private ExternalSepaInitiatePaymentRequestDTO minimalValidSingleRequest() {
        return ExternalSepaInitiatePaymentRequestDTO.builder()
                .creditorAccount(SepaAccountDTO.builder()
                        .iban("AB1234")
                        .build())
                .creditorName("fake creditor")
                .debtorAccount(SepaAccountDTO.builder()
                        .iban("CD5678")
                        .build())
                .endToEndIdentification("endToEndIdentification")
                .instructedAmount(SepaAmountDTO.builder()
                        .amount(BigDecimal.ONE)
                        .build())
                .remittanceInformationUnstructured("fake reference")
                .build();
    }

    private SepaInitiatePaymentRequestDTO expectedSingleRequest() {
        return SepaInitiatePaymentRequestDTO.builder()
                .creditorAccount(SepaAccountDTO.builder()
                        .iban("AB1234")
                        .build())
                .creditorName("fake creditor")
                .debtorAccount(SepaAccountDTO.builder()
                        .iban("CD5678")
                        .build())
                .endToEndIdentification("endToEndIdentification")
                .instructedAmount(SepaAmountDTO.builder()
                        .amount(BigDecimal.ONE)
                        .build())
                .remittanceInformationUnstructured("fake reference")
                .build();
    }

    private SepaInitiatePaymentRequestDTO expectedFutureRequest() {
        return SepaInitiatePaymentRequestDTO.builder()
                .creditorAccount(SepaAccountDTO.builder()
                        .iban("AB1234")
                        .build())
                .creditorName("fake creditor")
                .debtorAccount(SepaAccountDTO.builder()
                        .iban("CD5678")
                        .build())
                .endToEndIdentification("endToEndIdentification")
                .instructedAmount(SepaAmountDTO.builder()
                        .amount(BigDecimal.ONE)
                        .build())
                .remittanceInformationUnstructured("fake reference")
                .executionDate(LocalDate.now(clock).plusDays(5))
                .build();
    }

    private ExternalSepaInitiatePaymentRequestDTO minimalValidFutureRequest() {
        return ExternalSepaInitiatePaymentRequestDTO.builder()
                .creditorAccount(SepaAccountDTO.builder()
                        .iban("AB1234")
                        .build())
                .creditorName("fake creditor")
                .debtorAccount(SepaAccountDTO.builder()
                        .iban("CD5678")
                        .build())
                .endToEndIdentification("endToEndIdentification")
                .instructedAmount(SepaAmountDTO.builder()
                        .amount(BigDecimal.ONE)
                        .build())
                .remittanceInformationUnstructured("fake reference")
                .executionDate(LocalDate.now(clock).plusDays(5))
                .build();
    }

    private ExternalSepaInitiatePaymentRequestDTO minimalRequestWithTodaysDate() {
        return ExternalSepaInitiatePaymentRequestDTO.builder()
                .creditorAccount(SepaAccountDTO.builder()
                        .iban("AB1234")
                        .build())
                .creditorName("fake creditor")
                .debtorAccount(SepaAccountDTO.builder()
                        .iban("CD5678")
                        .build())
                .endToEndIdentification("endToEndIdentification")
                .instructedAmount(SepaAmountDTO.builder()
                        .amount(BigDecimal.ONE)
                        .build())
                .remittanceInformationUnstructured("fake reference")
                .executionDate(LocalDate.now(clock))
                .build();
    }

    private static SepaInitiatePaymentRequestDTO minimalValidPeriodicRequest() {
        return SepaInitiatePaymentRequestDTO.builder()
                .creditorAccount(SepaAccountDTO.builder()
                        .iban("AB1234")
                        .build())
                .creditorName("fake creditor")
                .debtorAccount(SepaAccountDTO.builder()
                        .iban("CD5678")
                        .build())
                .endToEndIdentification("endToEndIdentification")
                .instructedAmount(SepaAmountDTO.builder()
                        .amount(BigDecimal.ONE)
                        .build())
                .remittanceInformationUnstructured("fake reference")
                .periodicPaymentInfo(SepaPeriodicPaymentInfo.builder()
                        .startDate(LocalDate.now())
                        .endDate(LocalDate.now())
                        .frequency(PeriodicPaymentFrequency.DAILY)
                        .executionRule(PeriodicPaymentExecutionRule.FOLLOWING)
                        .build())
                .build();
    }
}
