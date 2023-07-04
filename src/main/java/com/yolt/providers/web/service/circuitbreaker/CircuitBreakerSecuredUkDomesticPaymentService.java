package com.yolt.providers.web.service.circuitbreaker;

import com.yolt.providers.common.exception.ConfirmationFailedException;
import com.yolt.providers.common.exception.CreationFailedException;
import com.yolt.providers.common.pis.common.GetStatusRequest;
import com.yolt.providers.common.pis.common.PaymentStatusResponseDTO;
import com.yolt.providers.common.pis.common.SubmitPaymentRequest;
import com.yolt.providers.common.pis.ukdomestic.InitiateUkDomesticPaymentRequest;
import com.yolt.providers.common.pis.ukdomestic.InitiateUkDomesticPaymentResponseDTO;
import com.yolt.providers.common.pis.ukdomestic.InitiateUkDomesticPeriodicPaymentRequest;
import com.yolt.providers.common.pis.ukdomestic.InitiateUkDomesticScheduledPaymentRequest;
import com.yolt.providers.common.providerinterface.PaymentSubmissionProvider;
import com.yolt.providers.common.providerinterface.UkDomesticPaymentProvider;
import com.yolt.providers.web.circuitbreaker.ProvidersCircuitBreaker;
import com.yolt.providers.web.circuitbreaker.ProvidersCircuitBreakerException;
import com.yolt.providers.web.circuitbreaker.ProvidersCircuitBreakerFactory;
import lombok.RequiredArgsConstructor;
import nl.ing.lovebird.clienttokens.ClientToken;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.yolt.providers.web.circuitbreaker.ProvidersCircuitBreakerCommand.*;

@Service
@RequiredArgsConstructor
public class CircuitBreakerSecuredUkDomesticPaymentService {

    private final ProvidersCircuitBreakerFactory circuitBreakerFactory;

    public InitiateUkDomesticPaymentResponseDTO initiateSinglePayment(ClientToken clientToken,
                                                                      UUID siteId,
                                                                      String provider,
                                                                      UUID redirectUrlId,
                                                                      UkDomesticPaymentProvider ukDomesticPaymentProvider,
                                                                      InitiateUkDomesticPaymentRequest initiateUkDomesticPaymentRequest) {

        ProvidersCircuitBreaker circuitBreaker = circuitBreakerFactory.create(clientToken, siteId, provider, PIS_UK_DOMESTIC_INITIATE_SINGLE_PAYMENT, redirectUrlId);
        Map<String, String> mdcContext = getContextMapOrEmpty();
        return circuitBreaker.run(() -> {
                    try {
                        MDC.setContextMap(mdcContext);
                        InitiateUkDomesticPaymentResponseDTO result = ukDomesticPaymentProvider.initiateSinglePayment(initiateUkDomesticPaymentRequest);
                        PaymentResultLogUtils.logPaymentResult(result::getPaymentExecutionContextMetadata, PaymentResultLogUtils.PaymentFlowType.UK_DOMESTIC_INIT);
                        return result;
                    } catch (CreationFailedException e) {
                        throw new ProvidersCircuitBreakerException(e);
                    }
                },
                throwable -> {
                    throw new ProvidersCircuitBreakerException("UK domestic payment initiation failed.", throwable);
                });
    }

    public InitiateUkDomesticPaymentResponseDTO initiateScheduledPayment(final ClientToken clientToken,
                                                                         final UUID siteId,
                                                                         final String provider,
                                                                         final UUID redirectUrlId,
                                                                         final UkDomesticPaymentProvider ukPaymentProvider,
                                                                         final InitiateUkDomesticScheduledPaymentRequest initiateScheduledPaymentRequest) {

        ProvidersCircuitBreaker circuitBreaker = circuitBreakerFactory.create(clientToken, siteId, provider, PIS_UK_DOMESTIC_INITIATE_SCHEDULED_PAYMENT, redirectUrlId);
        Map<String, String> mdcContext = getContextMapOrEmpty();
        return circuitBreaker.run(() -> {
                    try {
                        MDC.setContextMap(mdcContext);
                        InitiateUkDomesticPaymentResponseDTO result = ukPaymentProvider.initiateScheduledPayment(initiateScheduledPaymentRequest);
                        PaymentResultLogUtils.logPaymentResult(result::getPaymentExecutionContextMetadata, PaymentResultLogUtils.PaymentFlowType.UK_DOMESTIC_SCHEDULED_INIT);
                        return result;
                    } catch (CreationFailedException e) {
                        throw new ProvidersCircuitBreakerException(e);
                    }
                },
                throwable -> {
                    throw new ProvidersCircuitBreakerException("UK domestic scheduled payment initiation failed.", throwable);
                });
    }

    public InitiateUkDomesticPaymentResponseDTO initiatePeriodicPayment(final ClientToken clientToken,
                                                                        final UUID siteId,
                                                                        final String provider,
                                                                        final UUID redirectUrlId,
                                                                        final UkDomesticPaymentProvider ukPaymentProvider,
                                                                        final InitiateUkDomesticPeriodicPaymentRequest initiatePeriodicPaymentRequest) {
        ProvidersCircuitBreaker circuitBreaker = circuitBreakerFactory.create(clientToken, siteId, provider, PIS_UK_DOMESTIC_INITIATE_PERIODIC_PAYMENT, redirectUrlId);
        Map<String, String> mdcContext = getContextMapOrEmpty();
        return circuitBreaker.run(() -> {
                    try {
                        MDC.setContextMap(mdcContext);
                        InitiateUkDomesticPaymentResponseDTO result = ukPaymentProvider.initiatePeriodicPayment(initiatePeriodicPaymentRequest);
                        PaymentResultLogUtils.logPaymentResult(result::getPaymentExecutionContextMetadata, PaymentResultLogUtils.PaymentFlowType.UK_DOMESTIC_PERIODIC_INIT);
                        return result;
                    } catch (CreationFailedException e) {
                        throw new ProvidersCircuitBreakerException(e);
                    }
                },
                throwable -> {
                    throw new ProvidersCircuitBreakerException("UK domestic periodic payment initiation failed.", throwable);
                });
    }

    public PaymentStatusResponseDTO submitSinglePayment(ClientToken clientToken,
                                                        UUID siteId,
                                                        String provider,
                                                        UUID redirectUrlId,
                                                        PaymentSubmissionProvider ukDomesticPaymentSubmissionProvider,
                                                        SubmitPaymentRequest submitPaymentRequest) {
        ProvidersCircuitBreaker circuitBreaker = circuitBreakerFactory.create(clientToken, siteId, provider, PIS_UK_DOMESTIC_SUBMIT_PAYMENT, redirectUrlId);
        Map<String, String> mdcContext = getContextMapOrEmpty();
        return circuitBreaker.run(() -> {
                    try {
                        MDC.setContextMap(mdcContext);
                        PaymentStatusResponseDTO result = ukDomesticPaymentSubmissionProvider.submitPayment(submitPaymentRequest);
                        PaymentResultLogUtils.logPaymentResult(result::getPaymentExecutionContextMetadata, PaymentResultLogUtils.PaymentFlowType.UK_DOMESTIC_SUBMIT);
                        return result;
                    } catch (ConfirmationFailedException e) {
                        throw new ProvidersCircuitBreakerException(e);
                    }
                },
                throwable -> {
                    throw new ProvidersCircuitBreakerException("UK domestic payment submission failed.", throwable);
                });
    }

    public PaymentStatusResponseDTO submitPeriodicPayment(ClientToken clientToken,
                                                          UUID siteId,
                                                          String provider,
                                                          UUID redirectUrlId,
                                                          PaymentSubmissionProvider ukDomesticPaymentSubmissionProvider,
                                                          SubmitPaymentRequest submitPaymentRequest) {
        ProvidersCircuitBreaker circuitBreaker = circuitBreakerFactory.create(clientToken, siteId, provider, PIS_UK_DOMESTIC_SUBMIT_PERIODIC_PAYMENT, redirectUrlId);
        Map<String, String> mdcContext = getContextMapOrEmpty();
        return circuitBreaker.run(() -> {
                    try {
                        MDC.setContextMap(mdcContext);
                        PaymentStatusResponseDTO result = ukDomesticPaymentSubmissionProvider.submitPayment(submitPaymentRequest);
                        PaymentResultLogUtils.logPaymentResult(result::getPaymentExecutionContextMetadata, PaymentResultLogUtils.PaymentFlowType.UK_DOMESTIC_PERIODIC_SUBMIT);
                        return result;
                    } catch (ConfirmationFailedException e) {
                        throw new ProvidersCircuitBreakerException(e);
                    }
                },
                throwable -> {
                    throw new ProvidersCircuitBreakerException("UK domestic periodic payment submission failed.", throwable);
                });
    }

    public PaymentStatusResponseDTO getPaymentStatus(ClientToken clientToken,
                                                     UUID siteId,
                                                     String provider,
                                                     UUID redirectUrlId,
                                                     PaymentSubmissionProvider ukDomesticPaymentSubmissionProvider,
                                                     GetStatusRequest getStatusRequest) {
        ProvidersCircuitBreaker circuitBreaker = circuitBreakerFactory.create(clientToken, siteId, provider, PIS_UK_DOMESTIC_GET_PAYMENT_STATUS, redirectUrlId);
        Map<String, String> mdcContext = getContextMapOrEmpty();
        return circuitBreaker.run(() -> {
                    MDC.setContextMap(mdcContext);
                    PaymentStatusResponseDTO result = ukDomesticPaymentSubmissionProvider.getStatus(getStatusRequest);
                    PaymentResultLogUtils.logPaymentResult(result::getPaymentExecutionContextMetadata, PaymentResultLogUtils.PaymentFlowType.UK_DOMESTIC_STATUS);
                    return result;
                },
                throwable -> {
                    throw new ProvidersCircuitBreakerException("Getting UK domestic payment status failed.", throwable);
                });
    }

    private Map<String, String> getContextMapOrEmpty() {
        Map<String, String> copyOfContextMap = MDC.getCopyOfContextMap();
        return copyOfContextMap != null ? copyOfContextMap : new HashMap<>();
    }
}
