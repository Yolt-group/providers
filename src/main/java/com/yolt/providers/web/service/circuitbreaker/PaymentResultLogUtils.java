package com.yolt.providers.web.service.circuitbreaker;

import com.yolt.providers.common.pis.paymentexecutioncontext.model.EnhancedPaymentStatus;
import com.yolt.providers.common.pis.paymentexecutioncontext.model.PaymentExecutionContextMetadata;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Marker;

import java.util.function.Supplier;

import static net.logstash.logback.marker.Markers.append;

@UtilityClass
@Slf4j
public class PaymentResultLogUtils {

    private static final Marker HAPPY_FLOW_MARKER = append("happy-flow", "true");
    private static final Marker UNHAPPY_FLOW_MARKER = append("happy-flow", "false");

    public void logPaymentResult(Supplier<PaymentExecutionContextMetadata> pecMetadataSupplier, PaymentFlowType paymentFlowType) {
        PaymentExecutionContextMetadata pecMetadata = pecMetadataSupplier.get();
        if (pecMetadata != null && isPaymentStatusFailed(pecMetadata)) {
            log.warn(UNHAPPY_FLOW_MARKER, paymentFlowType.getUnhappyFlowMessage());
        } else {
            log.info(HAPPY_FLOW_MARKER, paymentFlowType.getHappyFlowMessage());
        }
    }

    private boolean isPaymentStatusFailed(PaymentExecutionContextMetadata pecMetadata) {
        EnhancedPaymentStatus paymentStatus = pecMetadata.getPaymentStatuses().getPaymentStatus();
        return paymentStatus.isTerminalState() && paymentStatus != EnhancedPaymentStatus.COMPLETED;
    }

    @RequiredArgsConstructor
    @Getter
    public enum PaymentFlowType {
        SEPA_INIT("Successfully initiated SEPA payment.", "Failed initiating SEPA payment."),
        SEPA_SUBMIT("Successfully submitted SEPA payment.", "Failed submitting SEPA payment."),
        SEPA_STATUS("Successfully received SEPA payment status.", "Failed checking SEPA payment status."),
        UK_DOMESTIC_INIT("Successfully initiated UK domestic payment.", "Failed initiating UK domestic payment."),
        UK_DOMESTIC_SUBMIT("Successfully submitted UK domestic payment.", "Failed submitting UK domestic payment."),
        UK_DOMESTIC_STATUS("Successfully received UK domestic payment status.", "Failed checking SEPA payment status."),
        UK_DOMESTIC_PERIODIC_INIT("Successfully initiated UK domestic periodic payment.", "Failed initiating UK domestic periodic payment."),
        UK_DOMESTIC_PERIODIC_SUBMIT("Successfully submitted  UK domestic periodic payment.", "Failed submitting UK domestic periodic payment."),
        UK_DOMESTIC_SCHEDULED_INIT("Successfully initiated UK domestic scheduled payment.", "Failed initiating UK domestic scheduled payment."),
        SEPA_PERIODIC_INIT("Successfully initiated SEPA periodic payment.", "Failed initiating SEPA periodic payment."),
        SEPA_PERIODIC_SUBMIT("Successfully submitted SEPA periodic payment.", "Failed submitting SEPA periodic payment."),
        SEPA_SCHEDULED_INIT("Successfully initiated SEPA scheduled payment.", "Failed initiating SEPA scheduled payment.");

        private final String happyFlowMessage;
        private final String unhappyFlowMessage;
    }
}
