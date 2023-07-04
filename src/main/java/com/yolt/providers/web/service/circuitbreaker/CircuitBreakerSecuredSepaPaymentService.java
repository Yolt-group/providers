package com.yolt.providers.web.service.circuitbreaker;

import com.yolt.providers.common.pis.sepa.*;
import com.yolt.providers.common.providerinterface.SepaPaymentProvider;
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
public class CircuitBreakerSecuredSepaPaymentService {

    private final ProvidersCircuitBreakerFactory circuitBreakerFactory;

    public LoginUrlAndStateDTO initiateSinglePayment(ClientToken clientToken,
                                                     UUID siteId,
                                                     String provider,
                                                     UUID redirectUrlId,
                                                     SepaPaymentProvider sepaPaymentProvider,
                                                     InitiatePaymentRequest initiatePaymentRequest) {
        ProvidersCircuitBreaker circuitBreaker = circuitBreakerFactory.create(clientToken, siteId, provider, PIS_SEPA_INITIATE_PAYMENT, redirectUrlId);
        Map<String, String> mdcContext = getContextMapOrEmpty();
        return circuitBreaker.run(() -> {
                    MDC.setContextMap(mdcContext);
                    LoginUrlAndStateDTO result = sepaPaymentProvider.initiatePayment(initiatePaymentRequest);
                    PaymentResultLogUtils.logPaymentResult(result::getPaymentExecutionContextMetadata, PaymentResultLogUtils.PaymentFlowType.SEPA_INIT);
                    return result;
                },
                throwable -> {
                    throw new ProvidersCircuitBreakerException("SEPA payment initiation failed.", throwable);
                });
    }

    public LoginUrlAndStateDTO initiateScheduledPayment(ClientToken clientToken,
                                                        UUID siteId,
                                                        String provider,
                                                        UUID redirectUrlId,
                                                        SepaPaymentProvider sepaPaymentProvider,
                                                        InitiatePaymentRequest initiatePaymentRequest) {
        ProvidersCircuitBreaker circuitBreaker = circuitBreakerFactory.create(clientToken, siteId, provider, PIS_SEPA_INITIATE_SCHEDULED_PAYMENT, redirectUrlId);
        Map<String, String> mdcContext = getContextMapOrEmpty();
        return circuitBreaker.run(() -> {
                    MDC.setContextMap(mdcContext);
                    LoginUrlAndStateDTO result = sepaPaymentProvider.initiateScheduledPayment(initiatePaymentRequest);
                    PaymentResultLogUtils.logPaymentResult(result::getPaymentExecutionContextMetadata, PaymentResultLogUtils.PaymentFlowType.SEPA_SCHEDULED_INIT);
                    return result;
                },
                throwable -> {
                    throw new ProvidersCircuitBreakerException("SEPA scheduled payment initiation failed.", throwable);
                });
    }

    public LoginUrlAndStateDTO initiatePeriodicPayment(ClientToken clientToken,
                                                       UUID siteId,
                                                       String provider,
                                                       UUID redirectUrlId,
                                                       SepaPaymentProvider sepaPaymentProvider,
                                                       InitiatePaymentRequest initiatePaymentRequest) {
        ProvidersCircuitBreaker circuitBreaker = circuitBreakerFactory.create(clientToken, siteId, provider, PIS_SEPA_INITIATE_PERIODIC_PAYMENT, redirectUrlId);
        Map<String, String> mdcContext = getContextMapOrEmpty();
        return circuitBreaker.run(() -> {
                    MDC.setContextMap(mdcContext);
                    LoginUrlAndStateDTO result = sepaPaymentProvider.initiatePeriodicPayment(initiatePaymentRequest);
                    PaymentResultLogUtils.logPaymentResult(result::getPaymentExecutionContextMetadata, PaymentResultLogUtils.PaymentFlowType.SEPA_PERIODIC_INIT);
                    return result;
                },
                throwable -> {
                    throw new ProvidersCircuitBreakerException("SEPA periodic payment initiation failed.", throwable);
                });
    }

    public SepaPaymentStatusResponseDTO submitSinglePayment(ClientToken clientToken,
                                                            UUID siteId,
                                                            String provider,
                                                            UUID redirectUrlId,
                                                            SepaPaymentProvider sepaPaymentProvider,
                                                            SubmitPaymentRequest submitPaymentRequestDTO) {
        ProvidersCircuitBreaker circuitBreaker = circuitBreakerFactory.create(clientToken, siteId, provider, PIS_SEPA_SUBMIT_PAYMENT, redirectUrlId);
        Map<String, String> mdcContext = getContextMapOrEmpty();
        return circuitBreaker.run(() -> {
                    MDC.setContextMap(mdcContext);
                    SepaPaymentStatusResponseDTO result = sepaPaymentProvider.submitPayment(submitPaymentRequestDTO);
                    PaymentResultLogUtils.logPaymentResult(result::getPaymentExecutionContextMetadata, PaymentResultLogUtils.PaymentFlowType.SEPA_SUBMIT);
                    return result;
                },
                throwable -> {
                    throw new ProvidersCircuitBreakerException("SEPA payment submit failed.", throwable);
                });
    }

    public SepaPaymentStatusResponseDTO submitPeriodicPayment(ClientToken clientToken,
                                                              UUID siteId,
                                                              String provider,
                                                              UUID redirectUrlId,
                                                              SepaPaymentProvider sepaPaymentProvider,
                                                              SubmitPaymentRequest submitPaymentRequestDTO) {
        ProvidersCircuitBreaker circuitBreaker = circuitBreakerFactory.create(clientToken, siteId, provider, PIS_SEPA_SUBMIT_PERIODIC_PAYMENT, redirectUrlId);
        Map<String, String> mdcContext = getContextMapOrEmpty();
        return circuitBreaker.run(() -> {
                    MDC.setContextMap(mdcContext);
                    SepaPaymentStatusResponseDTO result = sepaPaymentProvider.submitPayment(submitPaymentRequestDTO);
                    PaymentResultLogUtils.logPaymentResult(result::getPaymentExecutionContextMetadata, PaymentResultLogUtils.PaymentFlowType.SEPA_PERIODIC_SUBMIT);
                    return result;
                },
                throwable -> {
                    throw new ProvidersCircuitBreakerException("SEPA periodic payment submit failed.", throwable);
                });
    }

    public SepaPaymentStatusResponseDTO getPaymentStatus(ClientToken clientToken,
                                                         UUID siteId,
                                                         String provider,
                                                         UUID redirectUrlId,
                                                         SepaPaymentProvider sepaPaymentProvider,
                                                         GetStatusRequest getStatusRequest) {
        ProvidersCircuitBreaker circuitBreaker = circuitBreakerFactory.create(clientToken, siteId, provider, PIS_SEPA_GET_PAYMENT_STATUS, redirectUrlId);
        Map<String, String> mdcContext = getContextMapOrEmpty();
        return circuitBreaker.run(() -> {
                    MDC.setContextMap(mdcContext);
                    SepaPaymentStatusResponseDTO result = sepaPaymentProvider.getStatus(getStatusRequest);
                    PaymentResultLogUtils.logPaymentResult(result::getPaymentExecutionContextMetadata, PaymentResultLogUtils.PaymentFlowType.SEPA_STATUS);
                    return result;
                },
                throwable -> {
                    throw new ProvidersCircuitBreakerException("Getting SEPA payment status failed.", throwable);
                });
    }

    private Map<String, String> getContextMapOrEmpty() {
        Map<String, String> copyOfContextMap = MDC.getCopyOfContextMap();
        return copyOfContextMap != null ? copyOfContextMap : new HashMap<>();
    }
}
