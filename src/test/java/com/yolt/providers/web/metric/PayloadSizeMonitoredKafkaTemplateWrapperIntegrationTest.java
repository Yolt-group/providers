package com.yolt.providers.web.metric;

import com.yolt.providers.web.configuration.IntegrationTestContext;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.assertj.core.api.ThrowableAssert;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.concurrent.ListenableFuture;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTestContext
class PayloadSizeMonitoredKafkaTemplateWrapperIntegrationTest {

    @Autowired
    private PayloadSizeMonitoredKafkaTemplateWrapper<String, String> payloadSizeMonitoredKafkaTemplateWrapper;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void shouldRecordPayloadSizeFromSendResultWhenMessageSuccessfullySent() {
        // given
        KafkaMessageMetricsContext context = KafkaMessageMetricsContext.from("test-topic",
                UUID.randomUUID().toString(),
                "FAKE_PROVIDER");
        Message<String> message = MessageBuilder
                .withPayload("some message")
                .setHeader(KafkaHeaders.TOPIC, context.getTopic())
                .build();

        // when
        ListenableFuture<SendResult<String, String>> result = payloadSizeMonitoredKafkaTemplateWrapper.send(message,
                context);

        // then
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .until(result::isDone);
        DistributionSummary summary = meterRegistry.get("providers.kafka.payload.size")
                .tags(context.asTags())
                .summary();
        assertThat(summary.max())
                .isEqualTo(14.0);
    }

    @Test
    void shouldRecordPayloadSizeFromExceptionWhenMessageIsTooLarge() {
        // given
        KafkaMessageMetricsContext context = KafkaMessageMetricsContext.from("test-topic",
                UUID.randomUUID().toString(),
                "FAKE_PROVIDER");
        Message<String> message = MessageBuilder
                .withPayload(new String(new char[200000]))
                .setHeader(KafkaHeaders.TOPIC, context.getTopic())
                .build();

        // when
        ThrowableAssert.ThrowingCallable callable = () -> payloadSizeMonitoredKafkaTemplateWrapper.send(message, context);

        // then
        assertThatThrownBy(callable)
                .hasCauseExactlyInstanceOf(RecordTooLargeException.class)
                .hasRootCauseMessage("The message is 1200157 bytes when serialized which is larger than 1048576, which is the value of the max.request.size configuration.");
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .await();
        DistributionSummary summary = meterRegistry.get("providers.kafka.payload.size")
                .tags(context.asTags())
                .summary();
        assertThat(summary.max())
                .isEqualTo(1200157);
    }
}