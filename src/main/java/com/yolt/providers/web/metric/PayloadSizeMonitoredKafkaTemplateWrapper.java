package com.yolt.providers.web.metric;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@RequiredArgsConstructor
@Component
public class PayloadSizeMonitoredKafkaTemplateWrapper<K, V> {

    private static final Pattern NUMBERS_PATTERN = Pattern.compile("\\d+");
    private static final String KAFKA_PAYLOAD_SIZE_METRIC_NAME = "providers.kafka.payload.size";
    private static final String TOO_LARGE_MESSAGE_SIZE_ERROR_PART = "bytes when serialized which is larger than";
    private static final String BASE_UNITS_BYTES = "bytes";
    private static final String KAFKA_PAYLOAD_SIZE_METRIC_DESCRIPTION = "Serialized kafka payload size";

    private final MeterRegistry meterRegistry;
    private final KafkaTemplate<K, V> kafkaTemplate;

    public ListenableFuture<SendResult<K, V>> send(Message<V> message, KafkaMessageMetricsContext context) {
        try {
            ListenableFuture<SendResult<K, V>> future = kafkaTemplate.send(message);
            future.addCallback(result -> extractPayloadSizeFromResult(result)
                            .ifPresent(payloadSize -> recordKafkaPayloadSize(context, payloadSize)),
                    throwable -> {
                    });
            return future;
        } catch (KafkaException kafkaException) {
            extractPayloadSizeFromException(kafkaException)
                    .ifPresent(payloadSize -> recordKafkaPayloadSize(context, payloadSize));
            // rethrow exception not to break the flow
            throw kafkaException;
        }
    }

    private Optional<Integer> extractPayloadSizeFromResult(final SendResult<K, V> result) {
        if (result != null && result.getRecordMetadata() != null) {
            return Optional.of(result.getRecordMetadata().serializedValueSize());
        } else {
            return Optional.empty();
        }
    }

    private Optional<Integer> extractPayloadSizeFromException(final KafkaException kafkaException) {
        // if message is too large, Kafka emits RecordTooLargeException that is wrapped by KafkaException with the following error message:
        // 'The message is {messageSizeInBytes} bytes when serialized which is larger than {configuredMaxMessageSize}, which is the value of the max.request.size configuration.'
        if (kafkaException.getCause() instanceof RecordTooLargeException) {
            String errorMessage = kafkaException.getCause().getMessage();
            if (containsMessagePayloadSizeInformation(errorMessage)) {
                return extractPayloadSizeFromError(errorMessage);
            }
        }

        return Optional.empty();
    }

    private boolean containsMessagePayloadSizeInformation(final String errorMessage) {
        return StringUtils.hasText(errorMessage) &&
                errorMessage.contains(TOO_LARGE_MESSAGE_SIZE_ERROR_PART);
    }

    private Optional<Integer> extractPayloadSizeFromError(final String errorMessage) {
        return NUMBERS_PATTERN.matcher(errorMessage)
                .results()
                .map(result -> result.group(0))
                .map(Integer::parseInt)
                .findFirst();
    }

    private void recordKafkaPayloadSize(final KafkaMessageMetricsContext context,
                                        final Integer kafkaPayloadSizeInBytes) {
        List<Tag> tags = context.asTags();
        DistributionSummary summary = findDistributionSummary(tags)
                .orElseGet(() -> createDistributionSummary(tags));
        summary.record(kafkaPayloadSizeInBytes.doubleValue());
    }

    private Optional<DistributionSummary> findDistributionSummary(final List<Tag> tags) {
        return Optional.ofNullable(meterRegistry.find(KAFKA_PAYLOAD_SIZE_METRIC_NAME)
                .tags(tags)
                .summary());
    }

    private DistributionSummary createDistributionSummary(final List<Tag> tags) {
        return DistributionSummary.builder(KAFKA_PAYLOAD_SIZE_METRIC_NAME)
                .baseUnit(BASE_UNITS_BYTES)
                .tags(tags)
                .description(KAFKA_PAYLOAD_SIZE_METRIC_DESCRIPTION)
                .register(meterRegistry);
    }
}
