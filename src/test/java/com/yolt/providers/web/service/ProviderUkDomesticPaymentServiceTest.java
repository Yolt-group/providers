package com.yolt.providers.web.service;

import com.yolt.providers.common.exception.ConfirmationFailedException;
import com.yolt.providers.common.exception.CreationFailedException;
import com.yolt.providers.common.pis.common.*;
import com.yolt.providers.common.pis.ukdomestic.*;
import com.yolt.providers.common.providerinterface.PaymentSubmissionProvider;
import com.yolt.providers.common.providerinterface.UkDomesticPaymentProvider;
import com.yolt.providers.web.authenticationmeans.ClientAuthenticationMeansService;
import com.yolt.providers.web.controller.dto.*;
import com.yolt.providers.web.cryptography.signing.JcaSignerFactory;
import com.yolt.providers.web.cryptography.transport.MutualTLSRestTemplateManagerCache;
import com.yolt.providers.web.encryption.AesEncryptionUtil;
import com.yolt.providers.web.exception.IncorrectDateException;
import com.yolt.providers.web.service.circuitbreaker.CircuitBreakerSecuredUkDomesticPaymentService;
import com.yolt.providers.web.service.configuration.VersionType;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.extendeddata.common.CurrencyCode;
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
import java.time.*;
import java.util.UUID;

import static nl.ing.lovebird.providerdomain.ServiceType.PIS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderUkDomesticPaymentServiceTest {

    private static final UUID SITE_ID = UUID.fromString("571c2c82-a2f0-4c3c-b110-627020f58351");
    private static final UUID USER_ID = UUID.fromString("2068f360-0da5-4e74-ab53-2817d04c6242");
    private static final UUID REDIRECT_URL_ID = UUID.fromString("171c2c82-a4f0-4c3c-b110-627020f56351");
    private static final UUID CLIENT_ID = UUID.fromString("f46fd8af-0e92-420d-be1e-904acd1086d4");
    private static final String TEST_IMPL_OPENBANKING_NAME = "TEST_IMPL_OPENBANKING";
    private static final String PAYMENT_ID = "paymentId";
    private static final String ENCRYPTION_KEY = "a3f60fafc948035382fbe9ce7b4535c4";
    private static final String DECRYPTED_PROVIDER_STATE = "state";
    private static final String ENCRYPTED_PROVIDER_STATE = AesEncryptionUtil.encrypt(DECRYPTED_PROVIDER_STATE, ENCRYPTION_KEY);

    @Mock
    private ProviderFactoryService providerFactoryService;
    @Mock
    private UkDomesticPaymentProvider paymentProvider;
    @Mock
    private PaymentSubmissionProvider paymentSubmissionProvider;
    @Mock
    private ClientAuthenticationMeansService clientAuthenticationMeansService;
    @Mock
    private JcaSignerFactory jcaSignerFactory;
    @Mock
    private MutualTLSRestTemplateManagerCache mutualTLSRestTemplateManagerCache;
    @Mock
    private ProviderVaultKeys vaultKeys;
    @Mock
    private CircuitBreakerSecuredUkDomesticPaymentService circuitBreakerSecuredUkDomesticPaymentService;
    @Mock
    private Clock clock;
    @Captor
    private ArgumentCaptor<VersionType> versionTypeCaptor;

    @InjectMocks
    private ProviderUkDomesticPaymentService providerUkDomesticPaymentService;

    @Mock
    private ClientToken clientToken;

    @Test
    void shouldCallInitiateUkSinglePaymentWithCorrectData() throws CreationFailedException {
        // given
        when(vaultKeys.getEncryptionKey()).thenReturn(ENCRYPTION_KEY);
        when(providerFactoryService.getProvider(eq(TEST_IMPL_OPENBANKING_NAME), eq(UkDomesticPaymentProvider.class), eq(PIS), any(VersionType.class)))
                .thenReturn(paymentProvider);
        ExternalInitiateUkScheduledPaymentRequestDTO initiateUkPaymentRequest = new ExternalInitiateUkScheduledPaymentRequestDTO(minimalValidSingleRequest(),
                "state", new AuthenticationMeansReference(CLIENT_ID, REDIRECT_URL_ID),
                "http://redirect.url", null);
        when(circuitBreakerSecuredUkDomesticPaymentService.initiateSinglePayment(eq(clientToken), eq(SITE_ID), eq(TEST_IMPL_OPENBANKING_NAME),
                eq(REDIRECT_URL_ID), any(UkDomesticPaymentProvider.class), any(InitiateUkDomesticPaymentRequest.class)))
                .thenReturn(new InitiateUkDomesticPaymentResponseDTO("", DECRYPTED_PROVIDER_STATE));
        // when
        ExternalInitiateUkDomesticPaymentResponseDTO response = providerUkDomesticPaymentService.initiateSinglePayment(
                TEST_IMPL_OPENBANKING_NAME, initiateUkPaymentRequest, clientToken, SITE_ID, false);

        // then
        ArgumentCaptor<InitiateUkDomesticPaymentRequest> captor = ArgumentCaptor.forClass(InitiateUkDomesticPaymentRequest.class);
        verify(circuitBreakerSecuredUkDomesticPaymentService).initiateSinglePayment(
                eq(clientToken), eq(SITE_ID), eq(TEST_IMPL_OPENBANKING_NAME), eq(REDIRECT_URL_ID), any(UkDomesticPaymentProvider.class), captor.capture());

        InitiateUkDomesticPaymentRequest actual = captor.getValue();
        assertThat(actual.getRequestDTO()).isEqualTo(minimalValidSingleRequest());
        assertThat(actual.getBaseClientRedirectUrl()).isEqualTo(initiateUkPaymentRequest.getBaseClientRedirectUrl());
        assertThat(actual.getState()).isEqualTo(initiateUkPaymentRequest.getState());
        assertThat(response)
                .extracting(ExternalInitiateUkDomesticPaymentResponseDTO::getProviderState)
                .returns(DECRYPTED_PROVIDER_STATE, it -> AesEncryptionUtil.decrypt(it, ENCRYPTION_KEY));
    }

    @Test
    void shouldCallInitiateUkScheduledPaymentWithCorrectData() throws CreationFailedException {
        // given
        when(clock.instant()).thenReturn(Instant.now());
        when(clock.getZone()).thenReturn(ZoneId.of("UTC"));
        when(vaultKeys.getEncryptionKey()).thenReturn(ENCRYPTION_KEY);
        when(providerFactoryService.getProvider(eq(TEST_IMPL_OPENBANKING_NAME), eq(UkDomesticPaymentProvider.class), eq(PIS), any(VersionType.class)))
                .thenReturn(paymentProvider);
        ExternalInitiateUkScheduledPaymentRequestDTO initiateUkPaymentRequest = new ExternalInitiateUkScheduledPaymentRequestDTO(minimalValidExternalFutureRequest(),
                "state", new AuthenticationMeansReference(CLIENT_ID, REDIRECT_URL_ID),
                "http://redirect.url", null);
        when(circuitBreakerSecuredUkDomesticPaymentService.initiateScheduledPayment(eq(clientToken), eq(SITE_ID), eq(TEST_IMPL_OPENBANKING_NAME), eq(REDIRECT_URL_ID), any(UkDomesticPaymentProvider.class), any(InitiateUkDomesticScheduledPaymentRequest.class)))
                .thenReturn(new InitiateUkDomesticPaymentResponseDTO("", DECRYPTED_PROVIDER_STATE));
        // when
        ExternalInitiateUkDomesticPaymentResponseDTO response = providerUkDomesticPaymentService.initiateSinglePayment(TEST_IMPL_OPENBANKING_NAME, initiateUkPaymentRequest,
                clientToken, SITE_ID, false);

        // then
        ArgumentCaptor<InitiateUkDomesticScheduledPaymentRequest> captor = ArgumentCaptor.forClass(InitiateUkDomesticScheduledPaymentRequest.class);
        verify(circuitBreakerSecuredUkDomesticPaymentService).initiateScheduledPayment(eq(clientToken), eq(SITE_ID), eq(TEST_IMPL_OPENBANKING_NAME), eq(REDIRECT_URL_ID), any(UkDomesticPaymentProvider.class), captor.capture());

        InitiateUkDomesticScheduledPaymentRequest actual = captor.getValue();
        assertThat(actual.getRequestDTO()).isEqualTo(expectedValidFutureRequest());
        assertThat(actual.getBaseClientRedirectUrl()).isEqualTo(initiateUkPaymentRequest.getBaseClientRedirectUrl());
        assertThat(actual.getState()).isEqualTo(initiateUkPaymentRequest.getState());
        assertThat(response)
                .extracting(ExternalInitiateUkDomesticPaymentResponseDTO::getProviderState)
                .returns(DECRYPTED_PROVIDER_STATE, it -> AesEncryptionUtil.decrypt(it, ENCRYPTION_KEY));
    }

    @Test
    void shouldThrowExceptionWhenReceivedPaymentCallWithTodaysDate() {
        // given
        when(clock.instant()).thenReturn(Instant.now());
        when(clock.getZone()).thenReturn(ZoneId.of("UTC"));
        when(providerFactoryService.getProvider(eq(TEST_IMPL_OPENBANKING_NAME), eq(UkDomesticPaymentProvider.class), eq(PIS), any(VersionType.class)))
                .thenReturn(paymentProvider);
        ExternalInitiateUkScheduledPaymentRequestDTO initiateUkPaymentRequest = new ExternalInitiateUkScheduledPaymentRequestDTO(minimalRequestWithTodaysDate(),
                "state", new AuthenticationMeansReference(CLIENT_ID, REDIRECT_URL_ID),
                "http://redirect.url", null);
        // when
        ThrowableAssert.ThrowingCallable callable = () -> providerUkDomesticPaymentService.initiateSinglePayment(TEST_IMPL_OPENBANKING_NAME, initiateUkPaymentRequest,
                clientToken, SITE_ID, false);

        // then
        assertThatThrownBy(callable)
                .isInstanceOf(IncorrectDateException.class)
                .hasMessage("Execution date should not be today");
    }

    @Test
    void shouldCallInitiateUkPeriodicPaymentWithCorrectData() {
        // given
        when(vaultKeys.getEncryptionKey()).thenReturn(ENCRYPTION_KEY);
        when(providerFactoryService.getProvider(eq(TEST_IMPL_OPENBANKING_NAME), eq(UkDomesticPaymentProvider.class), eq(PIS), any(VersionType.class)))
                .thenReturn(paymentProvider);
        InitiateUkPeriodicPaymentRequestDTO initiateUkPaymentRequest = new InitiateUkPeriodicPaymentRequestDTO(minimalValidPeriodicRequest(),
                "state", new AuthenticationMeansReference(CLIENT_ID, REDIRECT_URL_ID),
                "http://redirect.url", null);
        when(circuitBreakerSecuredUkDomesticPaymentService.initiatePeriodicPayment(eq(clientToken), eq(SITE_ID), eq(TEST_IMPL_OPENBANKING_NAME),
                eq(REDIRECT_URL_ID), any(UkDomesticPaymentProvider.class), any(InitiateUkDomesticPeriodicPaymentRequest.class)))
                .thenReturn(new InitiateUkDomesticPaymentResponseDTO("", DECRYPTED_PROVIDER_STATE));
        // when
        ExternalInitiateUkDomesticPaymentResponseDTO response = providerUkDomesticPaymentService.initiatePeriodicPayment(TEST_IMPL_OPENBANKING_NAME, initiateUkPaymentRequest,
                clientToken, SITE_ID, false);

        // then
        ArgumentCaptor<InitiateUkDomesticPeriodicPaymentRequest> captor = ArgumentCaptor.forClass(InitiateUkDomesticPeriodicPaymentRequest.class);
        verify(circuitBreakerSecuredUkDomesticPaymentService).initiatePeriodicPayment(eq(clientToken), eq(SITE_ID), eq(TEST_IMPL_OPENBANKING_NAME), eq(REDIRECT_URL_ID), any(UkDomesticPaymentProvider.class), captor.capture());

        InitiateUkDomesticPeriodicPaymentRequest actual = captor.getValue();
        assertThat(actual.getRequestDTO()).isEqualTo(minimalValidPeriodicRequest());
        assertThat(actual.getBaseClientRedirectUrl()).isEqualTo(initiateUkPaymentRequest.getBaseClientRedirectUrl());
        assertThat(actual.getState()).isEqualTo(initiateUkPaymentRequest.getState());
        assertThat(response)
                .extracting(ExternalInitiateUkDomesticPaymentResponseDTO::getProviderState)
                .returns(DECRYPTED_PROVIDER_STATE, it -> AesEncryptionUtil.decrypt(it, ENCRYPTION_KEY));
    }

    @Test
    void shouldCallSubmitSinglePaymentOnUkDomesticPaymentProviderForSubmitSinglePaymentWithCorrectData() throws ConfirmationFailedException {
        // given
        when(vaultKeys.getEncryptionKey()).thenReturn(ENCRYPTION_KEY);
        when(providerFactoryService.getProvider(eq(TEST_IMPL_OPENBANKING_NAME), eq(PaymentSubmissionProvider.class), eq(PIS), any(VersionType.class)))
                .thenReturn(paymentSubmissionProvider);
        SubmitPaymentRequestDTO submitPaymentRequestDTO = new SubmitPaymentRequestDTO(ENCRYPTED_PROVIDER_STATE,
                new AuthenticationMeansReference(CLIENT_ID, REDIRECT_URL_ID), "redirectUrl", null);
        PaymentStatusResponseDTO paymentStatusResponseDTO = new PaymentStatusResponseDTO(DECRYPTED_PROVIDER_STATE, PAYMENT_ID);
        when(circuitBreakerSecuredUkDomesticPaymentService.submitSinglePayment(eq(clientToken), eq(SITE_ID), eq(TEST_IMPL_OPENBANKING_NAME),
                eq(REDIRECT_URL_ID), any(PaymentSubmissionProvider.class), any(SubmitPaymentRequest.class))).thenReturn(paymentStatusResponseDTO);

        // when
        PaymentStatusResponseDTO response = providerUkDomesticPaymentService.submitSinglePayment(TEST_IMPL_OPENBANKING_NAME, submitPaymentRequestDTO,
                clientToken, SITE_ID, false);

        // then
        ArgumentCaptor<SubmitPaymentRequest> captor = ArgumentCaptor.forClass(SubmitPaymentRequest.class);
        verify(circuitBreakerSecuredUkDomesticPaymentService).submitSinglePayment(eq(clientToken), eq(SITE_ID), eq(TEST_IMPL_OPENBANKING_NAME), eq(REDIRECT_URL_ID), any(PaymentSubmissionProvider.class), captor.capture());

        SubmitPaymentRequest actual = captor.getValue();
        assertThat(actual.getProviderState()).isEqualTo(DECRYPTED_PROVIDER_STATE);
        assertThat(response)
                .extracting(PaymentStatusResponseDTO::getProviderState)
                .returns(DECRYPTED_PROVIDER_STATE, it -> AesEncryptionUtil.decrypt(it, ENCRYPTION_KEY));
    }

    @Test
    void shouldCallSubmitPeriodicPaymentOnUkDomesticPaymentProviderForSubmitPeriodicPaymentWithCorrectData() throws ConfirmationFailedException {
        // given
        when(vaultKeys.getEncryptionKey()).thenReturn(ENCRYPTION_KEY);
        when(providerFactoryService.getProvider(eq(TEST_IMPL_OPENBANKING_NAME), eq(PaymentSubmissionProvider.class), eq(PIS), any(VersionType.class)))
                .thenReturn(paymentSubmissionProvider);
        var submitPaymentRequestDTO = new SubmitPaymentRequestDTO(ENCRYPTED_PROVIDER_STATE,
                new AuthenticationMeansReference(CLIENT_ID, REDIRECT_URL_ID), "redirectUrl", null);
        var paymentStatusResponseDTO = new PaymentStatusResponseDTO(DECRYPTED_PROVIDER_STATE, PAYMENT_ID);
        when(circuitBreakerSecuredUkDomesticPaymentService.submitPeriodicPayment(eq(clientToken), eq(SITE_ID), eq(TEST_IMPL_OPENBANKING_NAME),
                eq(REDIRECT_URL_ID), any(PaymentSubmissionProvider.class), any(SubmitPaymentRequest.class))).thenReturn(paymentStatusResponseDTO);

        // when
        PaymentStatusResponseDTO response = providerUkDomesticPaymentService.submitPeriodicPayment(TEST_IMPL_OPENBANKING_NAME, submitPaymentRequestDTO,
                clientToken, SITE_ID, false);

        // then
        ArgumentCaptor<SubmitPaymentRequest> captor = ArgumentCaptor.forClass(SubmitPaymentRequest.class);
        verify(circuitBreakerSecuredUkDomesticPaymentService).submitPeriodicPayment(eq(clientToken), eq(SITE_ID), eq(TEST_IMPL_OPENBANKING_NAME), eq(REDIRECT_URL_ID), any(PaymentSubmissionProvider.class), captor.capture());

        SubmitPaymentRequest actual = captor.getValue();
        assertThat(actual.getProviderState()).isEqualTo(DECRYPTED_PROVIDER_STATE);
        assertThat(response)
                .extracting(PaymentStatusResponseDTO::getProviderState)
                .returns(DECRYPTED_PROVIDER_STATE, it -> AesEncryptionUtil.decrypt(it, ENCRYPTION_KEY));
    }

    @Test
    void shouldCallGetPaymentStatusOnUkDomesticPaymentProviderForGetPaymentStatusWithCorrectData() {
        // given
        when(vaultKeys.getEncryptionKey()).thenReturn(ENCRYPTION_KEY);
        when(providerFactoryService.getProvider(eq(TEST_IMPL_OPENBANKING_NAME), eq(PaymentSubmissionProvider.class), eq(PIS), any(VersionType.class)))
                .thenReturn(paymentSubmissionProvider);
        GetPaymentStatusRequestDTO getPaymentStatusRequestDTO = new GetPaymentStatusRequestDTO(
                new AuthenticationMeansReference(CLIENT_ID, REDIRECT_URL_ID), PAYMENT_ID, null, null);
        var paymentStatusResponseDTO = new PaymentStatusResponseDTO(DECRYPTED_PROVIDER_STATE, PAYMENT_ID);
        when(circuitBreakerSecuredUkDomesticPaymentService.getPaymentStatus(eq(clientToken), eq(SITE_ID), eq(TEST_IMPL_OPENBANKING_NAME),
                eq(REDIRECT_URL_ID), any(PaymentSubmissionProvider.class), any(GetStatusRequest.class))).thenReturn(paymentStatusResponseDTO);

        // when
        PaymentStatusResponseDTO response = providerUkDomesticPaymentService.getPaymentStatus(TEST_IMPL_OPENBANKING_NAME, getPaymentStatusRequestDTO,
                clientToken, SITE_ID, false);

        // then
        ArgumentCaptor<GetStatusRequest> captor = ArgumentCaptor.forClass(GetStatusRequest.class);
        verify(circuitBreakerSecuredUkDomesticPaymentService).getPaymentStatus(eq(clientToken), eq(SITE_ID), eq(TEST_IMPL_OPENBANKING_NAME),
                eq(REDIRECT_URL_ID), any(PaymentSubmissionProvider.class), captor.capture());

        GetStatusRequest actual = captor.getValue();
        assertThat(actual.getPaymentId()).isEqualTo(PAYMENT_ID);
        assertThat(response)
                .extracting(PaymentStatusResponseDTO::getProviderState)
                .returns(DECRYPTED_PROVIDER_STATE, it -> AesEncryptionUtil.decrypt(it, ENCRYPTION_KEY));
    }

    @Test
    void shouldUseExperimentalProviderVersionWhenForceExperimentalFlagIsSetToTrue() {
        //given
        when(vaultKeys.getEncryptionKey()).thenReturn(ENCRYPTION_KEY);
        var getPaymentStatusRequestDTO = new GetPaymentStatusRequestDTO(
                new AuthenticationMeansReference(CLIENT_ID, REDIRECT_URL_ID), PAYMENT_ID, null, null);
        var paymentStatusResponseDTO = new PaymentStatusResponseDTO(DECRYPTED_PROVIDER_STATE, PAYMENT_ID);
        when(providerFactoryService.getProvider(any(), any(), any(), versionTypeCaptor.capture())).thenReturn(paymentSubmissionProvider);
        when(circuitBreakerSecuredUkDomesticPaymentService.getPaymentStatus(eq(clientToken), eq(SITE_ID), eq(TEST_IMPL_OPENBANKING_NAME),
                eq(REDIRECT_URL_ID), any(PaymentSubmissionProvider.class), any(GetStatusRequest.class))).thenReturn(paymentStatusResponseDTO);

        // when
        providerUkDomesticPaymentService.getPaymentStatus(TEST_IMPL_OPENBANKING_NAME, getPaymentStatusRequestDTO,
                clientToken, SITE_ID, true);

        //then
        assertThat(versionTypeCaptor.getValue()).isEqualTo(VersionType.EXPERIMENTAL);
    }

    @Test
    void shouldUseStableProviderVersionWhenForceExperimentalFlagIsSetToFalse() {
        //given
        when(vaultKeys.getEncryptionKey()).thenReturn(ENCRYPTION_KEY);
        GetPaymentStatusRequestDTO getPaymentStatusRequestDTO = new GetPaymentStatusRequestDTO(
                new AuthenticationMeansReference(CLIENT_ID, REDIRECT_URL_ID), PAYMENT_ID, null, null);
        var paymentStatusResponseDTO = new PaymentStatusResponseDTO(DECRYPTED_PROVIDER_STATE, PAYMENT_ID);
        when(providerFactoryService.getProvider(any(), any(), any(), versionTypeCaptor.capture())).thenReturn(paymentSubmissionProvider);
        when(circuitBreakerSecuredUkDomesticPaymentService.getPaymentStatus(eq(clientToken), eq(SITE_ID), eq(TEST_IMPL_OPENBANKING_NAME),
                eq(REDIRECT_URL_ID), any(PaymentSubmissionProvider.class), any(GetStatusRequest.class))).thenReturn(paymentStatusResponseDTO);
        // when
        providerUkDomesticPaymentService.getPaymentStatus(TEST_IMPL_OPENBANKING_NAME, getPaymentStatusRequestDTO,
                clientToken, SITE_ID, false);

        //then
        assertThat(versionTypeCaptor.getValue()).isEqualTo(VersionType.STABLE);
    }

    private ExternalInitiateUkDomesticScheduledPaymentRequestDTO minimalValidSingleRequest() {
        UkAccountDTO debtor = new UkAccountDTO("someSortCode",
                AccountIdentifierScheme.SORTCODEACCOUNTNUMBER,
                "Henry Moneybags",
                null);
        UkAccountDTO creditor = new UkAccountDTO("someSortCode",
                AccountIdentifierScheme.SORTCODEACCOUNTNUMBER,
                "Sean Emptypocket",
                null);
        return new ExternalInitiateUkDomesticScheduledPaymentRequestDTO(
                "endToEndIdentification",
                CurrencyCode.GBP.name(),
                BigDecimal.ONE,
                creditor,
                debtor,
                "fake description",
                null,
                null
        );
    }

    private InitiateUkDomesticScheduledPaymentRequestDTO expectedValidFutureRequest() {
        UkAccountDTO debtor = new UkAccountDTO("someSortCode",
                AccountIdentifierScheme.SORTCODEACCOUNTNUMBER,
                "Henry Moneybags",
                null);
        UkAccountDTO creditor = new UkAccountDTO("someSortCode",
                AccountIdentifierScheme.SORTCODEACCOUNTNUMBER,
                "Sean Emptypocket",
                null);
        return new InitiateUkDomesticScheduledPaymentRequestDTO(
                "endToEndIdentification",
                CurrencyCode.GBP.name(),
                BigDecimal.ONE,
                creditor,
                debtor,
                "fake description",
                null,
                OffsetDateTime.of(LocalDate.now(clock).plusDays(5), LocalTime.of(8, 0), ZoneOffset.UTC)
        );
    }

    private ExternalInitiateUkDomesticScheduledPaymentRequestDTO minimalValidExternalFutureRequest() {
        UkAccountDTO debtor = new UkAccountDTO("someSortCode",
                AccountIdentifierScheme.SORTCODEACCOUNTNUMBER,
                "Henry Moneybags",
                null);
        UkAccountDTO creditor = new UkAccountDTO("someSortCode",
                AccountIdentifierScheme.SORTCODEACCOUNTNUMBER,
                "Sean Emptypocket",
                null);
        return new ExternalInitiateUkDomesticScheduledPaymentRequestDTO(
                "endToEndIdentification",
                CurrencyCode.GBP.name(),
                BigDecimal.ONE,
                creditor,
                debtor,
                "fake description",
                null,
                LocalDate.now(clock).plusDays(5)
        );
    }

    private InitiateUkDomesticPeriodicPaymentRequestDTO minimalValidPeriodicRequest() {
        UkAccountDTO debtor = new UkAccountDTO("someSortCode",
                AccountIdentifierScheme.SORTCODEACCOUNTNUMBER,
                "Henry Moneybags",
                null);
        UkAccountDTO creditor = new UkAccountDTO("someSortCode",
                AccountIdentifierScheme.SORTCODEACCOUNTNUMBER,
                "Sean Emptypocket",
                null);
        return new InitiateUkDomesticPeriodicPaymentRequestDTO(
                "endToEndIdentification",
                CurrencyCode.GBP.name(),
                BigDecimal.ONE,
                creditor,
                debtor,
                "fake description",
                null,
                new UkPeriodicPaymentInfo(
                        LocalDate.now(),
                        LocalDate.now(),
                        PeriodicPaymentFrequency.MONTHLY
                )
        );
    }

    private ExternalInitiateUkDomesticScheduledPaymentRequestDTO minimalRequestWithTodaysDate() {
        UkAccountDTO debtor = new UkAccountDTO("someSortCode",
                AccountIdentifierScheme.SORTCODEACCOUNTNUMBER,
                "Henry Moneybags",
                null);
        UkAccountDTO creditor = new UkAccountDTO("someSortCode",
                AccountIdentifierScheme.SORTCODEACCOUNTNUMBER,
                "Sean Emptypocket",
                null);
        return new ExternalInitiateUkDomesticScheduledPaymentRequestDTO(
                "endToEndIdentification",
                CurrencyCode.GBP.name(),
                BigDecimal.ONE,
                creditor,
                debtor,
                "fake description",
                null,
                LocalDate.now(clock)
        );
    }
}
