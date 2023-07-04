package com.yolt.providers.web.service.circuitbreaker;

import com.yolt.providers.common.pis.common.PeriodicPaymentExecutionRule;
import com.yolt.providers.common.pis.common.PeriodicPaymentFrequency;
import com.yolt.providers.common.pis.common.SepaPeriodicPaymentInfo;
import com.yolt.providers.common.pis.sepa.*;
import com.yolt.providers.common.providerinterface.SepaPaymentProvider;
import com.yolt.providers.web.authenticationmeans.ClientAuthenticationMeansService;
import com.yolt.providers.web.circuitbreaker.ProvidersCircuitBreaker;
import com.yolt.providers.web.circuitbreaker.ProvidersCircuitBreakerFactory;
import com.yolt.providers.web.circuitbreaker.ProvidersCircuitBreakerMock;
import com.yolt.providers.web.controller.dto.GetSepaPaymentStatusRequestDTO;
import com.yolt.providers.web.controller.dto.InitiateSepaPaymentRequestDTO;
import com.yolt.providers.web.controller.dto.SubmitSepaPaymentRequestDTO;
import com.yolt.providers.web.cryptography.signing.JcaSignerFactory;
import com.yolt.providers.web.cryptography.transport.MutualTLSRestTemplateManagerCache;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static nl.ing.lovebird.providerdomain.ServiceType.PIS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CircuitBreakerSecuredSepaPaymentServiceTest {

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID SITE_ID = UUID.randomUUID();
    private static final String YOLT_PROVIDER_SEPA_PIS_NAME = "YOLT_PROVIDER_SEPA_PIS";
    private static final UUID REDIRECT_URL_ID = UUID.fromString("571c2c82-a2f0-4c3c-b110-627020f58351");
    private static final AuthenticationMeansReference AUTHENTICATION_MEANS_REFERENCE = new AuthenticationMeansReference(UUID.randomUUID(), UUID.randomUUID());

    private final ProvidersCircuitBreaker circuitBreaker = new ProvidersCircuitBreakerMock();

    @Mock
    private ProvidersCircuitBreakerFactory circuitBreakerFactory;

    @Mock
    private SepaPaymentProvider sepaPaymentProvider;

    @Mock
    private ClientAuthenticationMeansService clientAuthenticationMeansService;

    @Mock
    private JcaSignerFactory jcaSignerFactory;

    @Mock
    private ClientToken clientToken;

    @Mock
    private MutualTLSRestTemplateManagerCache restTemplateManagerCache;

    @InjectMocks
    private CircuitBreakerSecuredSepaPaymentService cbsSepaPaymentService;

    @BeforeEach
    public void beforeEach() {
        when(circuitBreakerFactory.create(any(), any(), any(), any(), any())).thenReturn(circuitBreaker);
    }

    @Test
    void shouldCallInitiateSinglePayment() {
        // given
        String loginUrl = "http://login.url";
        String state = "fake-state";
        InitiateSepaPaymentRequestDTO requestDTO = new InitiateSepaPaymentRequestDTO(minimalValidSingleRequest(),
                state, new AuthenticationMeansReference(UUID.randomUUID(), UUID.randomUUID()),
                "http://redirect.url", null);
        InitiatePaymentRequest initiatePaymentRequest = new InitiatePaymentRequest(
                requestDTO.getRequestDTO(),
                requestDTO.getBaseClientRedirectUrl(),
                requestDTO.getState(),
                clientAuthenticationMeansService.acquireAuthenticationMeans(YOLT_PROVIDER_SEPA_PIS_NAME, PIS, requestDTO.getAuthenticationMeansReference()),
                jcaSignerFactory.getForClientToken(clientToken),
                restTemplateManagerCache.getForClientProvider(clientToken, PIS, YOLT_PROVIDER_SEPA_PIS_NAME, false, sepaPaymentProvider.getVersion()),
                requestDTO.getPsuIpAddress(),
                AUTHENTICATION_MEANS_REFERENCE
        );
        when(sepaPaymentProvider.initiatePayment(any(InitiatePaymentRequest.class))).thenReturn(new LoginUrlAndStateDTO(loginUrl, state));

        // when
        LoginUrlAndStateDTO response = cbsSepaPaymentService.initiateSinglePayment(clientToken, SITE_ID, YOLT_PROVIDER_SEPA_PIS_NAME, REDIRECT_URL_ID, sepaPaymentProvider, initiatePaymentRequest);

        // then
        ArgumentCaptor<InitiatePaymentRequest> captor = ArgumentCaptor.forClass(InitiatePaymentRequest.class);
        verify(sepaPaymentProvider).initiatePayment(captor.capture());

        InitiatePaymentRequest actual = captor.getValue();
        assertThat(actual.getRequestDTO()).isEqualTo(minimalValidSingleRequest());
        assertThat(actual.getBaseClientRedirectUrl()).isEqualTo(initiatePaymentRequest.getBaseClientRedirectUrl());
        assertThat(actual.getState()).isEqualTo(initiatePaymentRequest.getState());
        assertThat(response.getLoginUrl()).isEqualTo(loginUrl);
        assertThat(response.getProviderState()).isEqualTo(state);
    }

    @Test
    void shouldCallSubmitSinglePayment() {
        // given
        String paymentId = "fake-payment-id";
        SubmitSepaPaymentRequestDTO requestDTO = new SubmitSepaPaymentRequestDTO("providerState",
                new AuthenticationMeansReference(UUID.randomUUID(), UUID.randomUUID()), "redirectUrl", null);
        SubmitPaymentRequest submitPaymentRequest = new SubmitPaymentRequest(
                requestDTO.getProviderState(),
                clientAuthenticationMeansService.acquireAuthenticationMeans(YOLT_PROVIDER_SEPA_PIS_NAME, PIS, requestDTO.getAuthenticationMeansReference()),
                requestDTO.getRedirectUrlPostedBackFromSite(),
                jcaSignerFactory.getForClientToken(clientToken),
                restTemplateManagerCache.getForClientProvider(clientToken, PIS, YOLT_PROVIDER_SEPA_PIS_NAME, false, sepaPaymentProvider.getVersion()),
                requestDTO.getPsuIpAddress(),
                AUTHENTICATION_MEANS_REFERENCE
        );
        when(sepaPaymentProvider.submitPayment(any(SubmitPaymentRequest.class))).thenReturn(new SepaPaymentStatusResponseDTO(paymentId));

        // when
        SepaPaymentStatusResponseDTO response = cbsSepaPaymentService.submitSinglePayment(clientToken, SITE_ID, YOLT_PROVIDER_SEPA_PIS_NAME, REDIRECT_URL_ID, sepaPaymentProvider, submitPaymentRequest);

        // then
        ArgumentCaptor<SubmitPaymentRequest> captor = ArgumentCaptor.forClass(SubmitPaymentRequest.class);
        verify(sepaPaymentProvider).submitPayment(captor.capture());

        SubmitPaymentRequest actual = captor.getValue();
        assertThat(actual.getProviderState()).isEqualTo(submitPaymentRequest.getProviderState());
        assertThat(response.getPaymentId()).isEqualTo(paymentId);
    }

    @Test
    void shouldCallInitiatePeriodicPayment() {
        // given
        String loginUrl = "http://login.url";
        String state = "fake-state";
        InitiateSepaPaymentRequestDTO requestDTO = new InitiateSepaPaymentRequestDTO(minimalValidPeriodicRequest(),
                state, new AuthenticationMeansReference(UUID.randomUUID(), UUID.randomUUID()),
                "http://redirect.url", null);
        InitiatePaymentRequest initiatePeriodicPaymentRequest = new InitiatePaymentRequest(
                requestDTO.getRequestDTO(),
                requestDTO.getBaseClientRedirectUrl(),
                requestDTO.getState(),
                clientAuthenticationMeansService.acquireAuthenticationMeans(YOLT_PROVIDER_SEPA_PIS_NAME, PIS, requestDTO.getAuthenticationMeansReference()),
                jcaSignerFactory.getForClientToken(clientToken),
                restTemplateManagerCache.getForClientProvider(clientToken, PIS, YOLT_PROVIDER_SEPA_PIS_NAME, false, sepaPaymentProvider.getVersion()),
                requestDTO.getPsuIpAddress(),
                AUTHENTICATION_MEANS_REFERENCE
        );
        when(sepaPaymentProvider.initiatePeriodicPayment(any(InitiatePaymentRequest.class))).thenReturn(new LoginUrlAndStateDTO(loginUrl, state));

        // when
        LoginUrlAndStateDTO response = cbsSepaPaymentService.initiatePeriodicPayment(clientToken, SITE_ID, YOLT_PROVIDER_SEPA_PIS_NAME, REDIRECT_URL_ID, sepaPaymentProvider, initiatePeriodicPaymentRequest);

        // then
        ArgumentCaptor<InitiatePaymentRequest> captor = ArgumentCaptor.forClass(InitiatePaymentRequest.class);
        verify(sepaPaymentProvider).initiatePeriodicPayment(captor.capture());

        InitiatePaymentRequest actual = captor.getValue();
        assertThat(actual.getRequestDTO()).isEqualTo(minimalValidPeriodicRequest());
        assertThat(actual.getBaseClientRedirectUrl()).isEqualTo(initiatePeriodicPaymentRequest.getBaseClientRedirectUrl());
        assertThat(actual.getState()).isEqualTo(initiatePeriodicPaymentRequest.getState());
        assertThat(response.getLoginUrl()).isEqualTo(loginUrl);
        assertThat(response.getProviderState()).isEqualTo(state);
    }

    @Test
    void shouldCallSubmitPeriodicPayment() {
        // given
        String paymentId = "fake-payment-id";
        SubmitSepaPaymentRequestDTO requestDTO = new SubmitSepaPaymentRequestDTO("providerState",
                new AuthenticationMeansReference(UUID.randomUUID(), UUID.randomUUID()), "redirectUrl", null);
        SubmitPaymentRequest submitPaymentRequest = new SubmitPaymentRequest(
                requestDTO.getProviderState(),
                clientAuthenticationMeansService.acquireAuthenticationMeans(YOLT_PROVIDER_SEPA_PIS_NAME, PIS, requestDTO.getAuthenticationMeansReference()),
                requestDTO.getRedirectUrlPostedBackFromSite(),
                jcaSignerFactory.getForClientToken(clientToken),
                restTemplateManagerCache.getForClientProvider(clientToken, PIS, YOLT_PROVIDER_SEPA_PIS_NAME, false, sepaPaymentProvider.getVersion()),
                requestDTO.getPsuIpAddress(),
                AUTHENTICATION_MEANS_REFERENCE
        );
        when(sepaPaymentProvider.submitPayment(any(SubmitPaymentRequest.class))).thenReturn(new SepaPaymentStatusResponseDTO(paymentId));

        // when
        SepaPaymentStatusResponseDTO response = cbsSepaPaymentService.submitPeriodicPayment(clientToken, SITE_ID, YOLT_PROVIDER_SEPA_PIS_NAME, REDIRECT_URL_ID, sepaPaymentProvider, submitPaymentRequest);

        // then
        ArgumentCaptor<SubmitPaymentRequest> captor = ArgumentCaptor.forClass(SubmitPaymentRequest.class);
        verify(sepaPaymentProvider).submitPayment(captor.capture());

        SubmitPaymentRequest actual = captor.getValue();
        assertThat(actual.getProviderState()).isEqualTo(submitPaymentRequest.getProviderState());
        assertThat(response.getPaymentId()).isEqualTo(paymentId);
    }

    @Test
    void shouldCallInitiateScheduledPayment() {
        // given
        String loginUrl = "http://login.url";
        String state = "fake-state";
        InitiateSepaPaymentRequestDTO requestDTO = new InitiateSepaPaymentRequestDTO(minimalValidSingleRequest(),
                state, new AuthenticationMeansReference(UUID.randomUUID(), UUID.randomUUID()),
                "http://redirect.url", null);
        InitiatePaymentRequest initiatescheduledPaymentRequest = new InitiatePaymentRequest(
                requestDTO.getRequestDTO(),
                requestDTO.getBaseClientRedirectUrl(),
                requestDTO.getState(),
                clientAuthenticationMeansService.acquireAuthenticationMeans(YOLT_PROVIDER_SEPA_PIS_NAME, PIS, requestDTO.getAuthenticationMeansReference()),
                jcaSignerFactory.getForClientToken(clientToken),
                restTemplateManagerCache.getForClientProvider(clientToken, PIS, YOLT_PROVIDER_SEPA_PIS_NAME, false, sepaPaymentProvider.getVersion()),
                requestDTO.getPsuIpAddress(),
                AUTHENTICATION_MEANS_REFERENCE
        );
        when(sepaPaymentProvider.initiateScheduledPayment(any(InitiatePaymentRequest.class))).thenReturn(new LoginUrlAndStateDTO(loginUrl, state));

        // when
        LoginUrlAndStateDTO response = cbsSepaPaymentService.initiateScheduledPayment(clientToken, SITE_ID, YOLT_PROVIDER_SEPA_PIS_NAME, REDIRECT_URL_ID, sepaPaymentProvider, initiatescheduledPaymentRequest);

        // then
        ArgumentCaptor<InitiatePaymentRequest> captor = ArgumentCaptor.forClass(InitiatePaymentRequest.class);
        verify(sepaPaymentProvider).initiateScheduledPayment(captor.capture());

        InitiatePaymentRequest actual = captor.getValue();
        assertThat(actual.getRequestDTO()).isEqualTo(minimalValidSingleRequest());
        assertThat(actual.getBaseClientRedirectUrl()).isEqualTo(initiatescheduledPaymentRequest.getBaseClientRedirectUrl());
        assertThat(actual.getState()).isEqualTo(initiatescheduledPaymentRequest.getState());
        assertThat(response.getLoginUrl()).isEqualTo(loginUrl);
        assertThat(response.getProviderState()).isEqualTo(state);
    }

    @Test
    void shouldCallGetPaymentStatus() {
        // given
        String paymentId = "fake-payment-id";
        GetSepaPaymentStatusRequestDTO requestDTO = new GetSepaPaymentStatusRequestDTO(
                new AuthenticationMeansReference(UUID.randomUUID(), UUID.randomUUID()), "fakePaymentId", null);
        GetStatusRequest getStatusRequest = new GetStatusRequest(null,
                requestDTO.getPaymentId(),
                clientAuthenticationMeansService.acquireAuthenticationMeans(YOLT_PROVIDER_SEPA_PIS_NAME, PIS, requestDTO.getAuthenticationMeansReference()),
                jcaSignerFactory.getForClientToken(clientToken),
                restTemplateManagerCache.getForClientProvider(clientToken, PIS, YOLT_PROVIDER_SEPA_PIS_NAME, false, sepaPaymentProvider.getVersion()),
                requestDTO.getPsuIpAddress(),
                AUTHENTICATION_MEANS_REFERENCE
        );
        when(sepaPaymentProvider.getStatus(any(GetStatusRequest.class))).thenReturn(new SepaPaymentStatusResponseDTO(paymentId));

        // when
        SepaPaymentStatusResponseDTO response = cbsSepaPaymentService.getPaymentStatus(clientToken, SITE_ID, YOLT_PROVIDER_SEPA_PIS_NAME, REDIRECT_URL_ID, sepaPaymentProvider, getStatusRequest);

        // then
        ArgumentCaptor<GetStatusRequest> captor = ArgumentCaptor.forClass(GetStatusRequest.class);
        verify(sepaPaymentProvider).getStatus(captor.capture());

        GetStatusRequest actual = captor.getValue();
        assertThat(actual.getPaymentId()).isEqualTo(getStatusRequest.getPaymentId());
        assertThat(response.getPaymentId()).isEqualTo(paymentId);
    }

    private SepaInitiatePaymentRequestDTO minimalValidSingleRequest() {
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

    private SepaInitiatePaymentRequestDTO minimalValidPeriodicRequest() {
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