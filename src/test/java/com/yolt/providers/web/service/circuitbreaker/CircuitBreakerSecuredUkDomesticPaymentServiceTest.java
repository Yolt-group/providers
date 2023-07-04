package com.yolt.providers.web.service.circuitbreaker;

import com.yolt.providers.common.pis.common.*;
import com.yolt.providers.common.pis.ukdomestic.*;
import com.yolt.providers.common.providerinterface.PaymentSubmissionProvider;
import com.yolt.providers.common.providerinterface.UkDomesticPaymentProvider;
import com.yolt.providers.web.authenticationmeans.ClientAuthenticationMeansService;
import com.yolt.providers.web.circuitbreaker.ProvidersCircuitBreaker;
import com.yolt.providers.web.circuitbreaker.ProvidersCircuitBreakerFactory;
import com.yolt.providers.web.circuitbreaker.ProvidersCircuitBreakerMock;
import com.yolt.providers.web.controller.dto.ExternalInitiateUkDomesticScheduledPaymentRequestDTO;
import com.yolt.providers.web.controller.dto.InitiateUkPaymentRequestDTO;
import com.yolt.providers.web.controller.dto.InitiateUkPeriodicPaymentRequestDTO;
import com.yolt.providers.web.cryptography.signing.JcaSignerFactory;
import com.yolt.providers.web.cryptography.transport.MutualTLSRestTemplateManagerCache;
import lombok.SneakyThrows;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.extendeddata.common.CurrencyCode;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

import static nl.ing.lovebird.providerdomain.ServiceType.PIS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CircuitBreakerSecuredUkDomesticPaymentServiceTest {

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final String YOLT_PROVIDER_UK_DOMESTIC_NAME = "YOLT_PROVIDER_UK_DOMESTIC";
    private static final UUID SITE_ID = UUID.randomUUID();
    private static final UUID REDIRECT_URL_ID = UUID.fromString("571c2c82-a2f0-4c3c-b110-627020f58351");
    private static final AuthenticationMeansReference AUTHENTICATION_MEANS_REFERENCE = new AuthenticationMeansReference(UUID.randomUUID(), UUID.randomUUID());

    private final ProvidersCircuitBreaker circuitBreaker = new ProvidersCircuitBreakerMock();

    @Mock
    private ProvidersCircuitBreakerFactory circuitBreakerFactory;

    @Mock
    private UkDomesticPaymentProvider ukDomesticPaymentProvider;

    @Mock
    private PaymentSubmissionProvider paymentSubmissionProvider;

    @Mock
    private ClientAuthenticationMeansService clientAuthenticationMeansService;

    @Mock
    private JcaSignerFactory jcaSignerFactory;

    @Mock
    private ClientToken clientToken;

    @Mock
    private MutualTLSRestTemplateManagerCache restTemplateManagerCache;

    @Mock
    private Clock clock;

    @InjectMocks
    private CircuitBreakerSecuredUkDomesticPaymentService circuitBreakerSecuredUkDomesticPaymentService;

    @BeforeEach
    public void beforeEach() {
        when(circuitBreakerFactory.create(any(), any(), any(), any(), any())).thenReturn(circuitBreaker);
    }

    @SneakyThrows
    @Test
    void shouldCallInitiateSinglePayment() {
        // given
        String loginUrl = "http://login.url";
        String state = "fake-state";
        InitiateUkPaymentRequestDTO requestDTO = new InitiateUkPaymentRequestDTO(minimalValidSingleRequest(),
                state, new AuthenticationMeansReference(UUID.randomUUID(), UUID.randomUUID()),
                "http://redirect.url", null);
        InitiateUkDomesticPaymentRequest initiatePaymentRequest = new InitiateUkDomesticPaymentRequest(
                requestDTO.getRequestDTO(),
                requestDTO.getBaseClientRedirectUrl(),
                requestDTO.getState(),
                clientAuthenticationMeansService.acquireAuthenticationMeans(YOLT_PROVIDER_UK_DOMESTIC_NAME, PIS, requestDTO.getAuthenticationMeansReference()),
                jcaSignerFactory.getForClientToken(clientToken),
                restTemplateManagerCache.getForClientProvider(clientToken, PIS, YOLT_PROVIDER_UK_DOMESTIC_NAME, false, ukDomesticPaymentProvider.getVersion()),
                requestDTO.getPsuIpAddress(),
                AUTHENTICATION_MEANS_REFERENCE
        );
        when(ukDomesticPaymentProvider.initiateSinglePayment(any(InitiateUkDomesticPaymentRequest.class)))
                .thenReturn(new InitiateUkDomesticPaymentResponseDTO(loginUrl, state));

        // when
        InitiateUkDomesticPaymentResponseDTO response = circuitBreakerSecuredUkDomesticPaymentService.initiateSinglePayment(clientToken, SITE_ID, YOLT_PROVIDER_UK_DOMESTIC_NAME, REDIRECT_URL_ID, ukDomesticPaymentProvider, initiatePaymentRequest);

        // then
        ArgumentCaptor<InitiateUkDomesticPaymentRequest> captor = ArgumentCaptor.forClass(InitiateUkDomesticPaymentRequest.class);
        verify(ukDomesticPaymentProvider).initiateSinglePayment(captor.capture());

        InitiateUkDomesticPaymentRequest actual = captor.getValue();
        assertThat(actual.getRequestDTO()).isEqualTo(minimalValidSingleRequest());
        assertThat(actual.getBaseClientRedirectUrl()).isEqualTo(initiatePaymentRequest.getBaseClientRedirectUrl());
        assertThat(actual.getState()).isEqualTo(initiatePaymentRequest.getState());
        assertThat(response.getLoginUrl()).isEqualTo(loginUrl);
        assertThat(response.getProviderState()).isEqualTo(state);
    }

    @SneakyThrows
    @Test
    void shouldCallInitiateScheduledPayment() {
        // given
        when(clock.instant()).thenReturn(Instant.now());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        String loginUrl = "http://login.url";
        String state = "fake-state";
        InitiateUkDomesticScheduledPaymentRequest initiatePaymentRequest = new InitiateUkDomesticScheduledPaymentRequest(
                minimalValidScheduledRequest(),
                "http://redirect.url",
                state,
                clientAuthenticationMeansService.acquireAuthenticationMeans(YOLT_PROVIDER_UK_DOMESTIC_NAME, PIS, new AuthenticationMeansReference(UUID.randomUUID(), UUID.randomUUID())),
                jcaSignerFactory.getForClientToken(clientToken),
                restTemplateManagerCache.getForClientProvider(clientToken, PIS, YOLT_PROVIDER_UK_DOMESTIC_NAME, false, ukDomesticPaymentProvider.getVersion()),
                null,
                AUTHENTICATION_MEANS_REFERENCE
        );
        when(ukDomesticPaymentProvider.initiateScheduledPayment(any(InitiateUkDomesticScheduledPaymentRequest.class)))
                .thenReturn(new InitiateUkDomesticPaymentResponseDTO(loginUrl, state));

        // when
        InitiateUkDomesticPaymentResponseDTO response = circuitBreakerSecuredUkDomesticPaymentService.initiateScheduledPayment(clientToken, SITE_ID, YOLT_PROVIDER_UK_DOMESTIC_NAME, REDIRECT_URL_ID, ukDomesticPaymentProvider, initiatePaymentRequest);

        // then
        ArgumentCaptor<InitiateUkDomesticScheduledPaymentRequest> captor = ArgumentCaptor.forClass(InitiateUkDomesticScheduledPaymentRequest.class);
        verify(ukDomesticPaymentProvider).initiateScheduledPayment(captor.capture());

        InitiateUkDomesticScheduledPaymentRequest actual = captor.getValue();
        assertThat(actual.getRequestDTO()).isEqualTo(expectedScheduledRequest());
        assertThat(actual.getBaseClientRedirectUrl()).isEqualTo(initiatePaymentRequest.getBaseClientRedirectUrl());
        assertThat(actual.getState()).isEqualTo(initiatePaymentRequest.getState());
        assertThat(response.getLoginUrl()).isEqualTo(loginUrl);
        assertThat(response.getProviderState()).isEqualTo(state);
    }

    @SneakyThrows
    @Test
    void shouldCallInitiatePeriodicPayment() {
        // given
        String loginUrl = "http://login.url";
        String state = "fake-state";
        InitiateUkPeriodicPaymentRequestDTO requestDTO = new InitiateUkPeriodicPaymentRequestDTO(minimalValidPeriodicRequest(),
                state, new AuthenticationMeansReference(UUID.randomUUID(), UUID.randomUUID()),
                "http://redirect.url", null);
        InitiateUkDomesticPeriodicPaymentRequest initiatePaymentRequest = new InitiateUkDomesticPeriodicPaymentRequest(
                requestDTO.getRequestDTO(),
                requestDTO.getBaseClientRedirectUrl(),
                requestDTO.getState(),
                clientAuthenticationMeansService.acquireAuthenticationMeans(YOLT_PROVIDER_UK_DOMESTIC_NAME, PIS, requestDTO.getAuthenticationMeansReference()),
                jcaSignerFactory.getForClientToken(clientToken),
                restTemplateManagerCache.getForClientProvider(clientToken, PIS, YOLT_PROVIDER_UK_DOMESTIC_NAME, false, ukDomesticPaymentProvider.getVersion()),
                requestDTO.getPsuIpAddress(),
                AUTHENTICATION_MEANS_REFERENCE
        );
        when(ukDomesticPaymentProvider.initiatePeriodicPayment(any(InitiateUkDomesticPeriodicPaymentRequest.class)))
                .thenReturn(new InitiateUkDomesticPaymentResponseDTO(loginUrl, state));

        // when
        InitiateUkDomesticPaymentResponseDTO response = circuitBreakerSecuredUkDomesticPaymentService.initiatePeriodicPayment(clientToken, SITE_ID, YOLT_PROVIDER_UK_DOMESTIC_NAME, REDIRECT_URL_ID, ukDomesticPaymentProvider, initiatePaymentRequest);

        // then
        ArgumentCaptor<InitiateUkDomesticPeriodicPaymentRequest> captor = ArgumentCaptor.forClass(InitiateUkDomesticPeriodicPaymentRequest.class);
        verify(ukDomesticPaymentProvider).initiatePeriodicPayment(captor.capture());

        InitiateUkDomesticPeriodicPaymentRequest actual = captor.getValue();
        assertThat(actual.getRequestDTO()).isEqualTo(minimalValidPeriodicRequest());
        assertThat(actual.getBaseClientRedirectUrl()).isEqualTo(initiatePaymentRequest.getBaseClientRedirectUrl());
        assertThat(actual.getState()).isEqualTo(initiatePaymentRequest.getState());
        assertThat(response.getLoginUrl()).isEqualTo(loginUrl);
        assertThat(response.getProviderState()).isEqualTo(state);
    }

    @SneakyThrows
    @Test
    void shouldCallSubmitSinglePayment() {
        // given
        String paymentId = "fake-payment-id";
        SubmitPaymentRequest submitPaymentRequest = new SubmitPaymentRequest(
                "providerState",
                clientAuthenticationMeansService.acquireAuthenticationMeans(YOLT_PROVIDER_UK_DOMESTIC_NAME, PIS, new AuthenticationMeansReference(UUID.randomUUID(), UUID.randomUUID())),
                "fakeRedirectUrl",
                jcaSignerFactory.getForClientToken(clientToken),
                restTemplateManagerCache.getForClientProvider(clientToken, PIS, YOLT_PROVIDER_UK_DOMESTIC_NAME, false, paymentSubmissionProvider.getVersion()),
                "127.0.0.1",
                AUTHENTICATION_MEANS_REFERENCE
        );
        when(paymentSubmissionProvider.submitPayment(any(SubmitPaymentRequest.class)))
                .thenReturn(new PaymentStatusResponseDTO("ReturnedProviderState", paymentId));

        // when
        PaymentStatusResponseDTO response = circuitBreakerSecuredUkDomesticPaymentService.submitSinglePayment(clientToken, SITE_ID, YOLT_PROVIDER_UK_DOMESTIC_NAME, REDIRECT_URL_ID, paymentSubmissionProvider, submitPaymentRequest);

        // then
        ArgumentCaptor<SubmitPaymentRequest> captor = ArgumentCaptor.forClass(SubmitPaymentRequest.class);
        verify(paymentSubmissionProvider).submitPayment(captor.capture());

        SubmitPaymentRequest actual = captor.getValue();
        assertThat(response.getProviderState()).isEqualTo("ReturnedProviderState");
        assertThat(actual.getProviderState()).isEqualTo(submitPaymentRequest.getProviderState());
        assertThat(response.getPaymentId()).isEqualTo(paymentId);
    }

    @SneakyThrows
    @Test
    void shouldCallSubmitPeriodicPayment() {
        // given
        String paymentId = "fake-payment-id";
        SubmitPaymentRequest submitPaymentRequest = new SubmitPaymentRequest(
                "providerState",
                clientAuthenticationMeansService.acquireAuthenticationMeans(YOLT_PROVIDER_UK_DOMESTIC_NAME, PIS, new AuthenticationMeansReference(UUID.randomUUID(), UUID.randomUUID())),
                "fakeRedirectUrl",
                jcaSignerFactory.getForClientToken(clientToken),
                restTemplateManagerCache.getForClientProvider(clientToken, PIS, YOLT_PROVIDER_UK_DOMESTIC_NAME, false, paymentSubmissionProvider.getVersion()),
                "127.0.0.1",
                AUTHENTICATION_MEANS_REFERENCE
        );
        when(paymentSubmissionProvider.submitPayment(any(SubmitPaymentRequest.class)))
                .thenReturn(new PaymentStatusResponseDTO("ReturnedProviderState", paymentId));

        // when
        PaymentStatusResponseDTO response = circuitBreakerSecuredUkDomesticPaymentService.submitPeriodicPayment(clientToken, SITE_ID, YOLT_PROVIDER_UK_DOMESTIC_NAME, REDIRECT_URL_ID, paymentSubmissionProvider, submitPaymentRequest);

        // then
        ArgumentCaptor<SubmitPaymentRequest> captor = ArgumentCaptor.forClass(SubmitPaymentRequest.class);
        verify(paymentSubmissionProvider).submitPayment(captor.capture());

        SubmitPaymentRequest actual = captor.getValue();
        assertThat(actual.getProviderState()).isEqualTo(submitPaymentRequest.getProviderState());
        assertThat(response.getProviderState()).isEqualTo("ReturnedProviderState");
        assertThat(response.getPaymentId()).isEqualTo(paymentId);
    }

    @SneakyThrows
    @Test
    void shouldCallGetPaymentStatus() {
        // given
        String paymentId = "fake-payment-id";
        GetStatusRequest getStatusRequest = new GetStatusRequest(null,
                paymentId,
                clientAuthenticationMeansService.acquireAuthenticationMeans(YOLT_PROVIDER_UK_DOMESTIC_NAME, PIS, new AuthenticationMeansReference(UUID.randomUUID(), UUID.randomUUID())),
                jcaSignerFactory.getForClientToken(clientToken),
                restTemplateManagerCache.getForClientProvider(clientToken, PIS, YOLT_PROVIDER_UK_DOMESTIC_NAME, false, paymentSubmissionProvider.getVersion()),
                "127.0.0.1",
                AUTHENTICATION_MEANS_REFERENCE
        );
        when(paymentSubmissionProvider.getStatus(any(GetStatusRequest.class)))
                .thenReturn(new PaymentStatusResponseDTO("ReturnedProviderState", paymentId));

        // when
        PaymentStatusResponseDTO response = circuitBreakerSecuredUkDomesticPaymentService
                .getPaymentStatus(clientToken, SITE_ID, YOLT_PROVIDER_UK_DOMESTIC_NAME, REDIRECT_URL_ID, paymentSubmissionProvider, getStatusRequest);

        // then
        ArgumentCaptor<GetStatusRequest> captor = ArgumentCaptor.forClass(GetStatusRequest.class);
        verify(paymentSubmissionProvider).getStatus(captor.capture());

        assertThat(response.getProviderState()).isEqualTo("ReturnedProviderState");
        assertThat(response.getPaymentId()).isEqualTo(paymentId);
    }

    private InitiateUkDomesticPaymentRequestDTO minimalValidSingleRequest() {
        UkAccountDTO debtor = new UkAccountDTO("someSortCode",
                AccountIdentifierScheme.SORTCODEACCOUNTNUMBER,
                "Henry Moneybags",
                null);
        UkAccountDTO creditor = new UkAccountDTO("someSortCode",
                AccountIdentifierScheme.SORTCODEACCOUNTNUMBER,
                "Sean Emptypocket",
                null);
        return new InitiateUkDomesticPaymentRequestDTO(
                "endToEndIdentification",
                CurrencyCode.GBP.name(),
                BigDecimal.ONE,
                creditor,
                debtor,
                "fake description",
                null
        );
    }

    private InitiateUkDomesticScheduledPaymentRequestDTO minimalValidScheduledRequest() {
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
                OffsetDateTime.now(clock)
        );
    }

    private InitiateUkDomesticScheduledPaymentRequestDTO expectedScheduledRequest() {
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
                OffsetDateTime.now(clock)
        );
    }

    private ExternalInitiateUkDomesticScheduledPaymentRequestDTO minimalValidExternalScheduledRequest() {
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
                        PeriodicPaymentFrequency.DAILY)
        );
    }
}