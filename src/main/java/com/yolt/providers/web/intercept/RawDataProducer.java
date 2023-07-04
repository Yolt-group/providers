package com.yolt.providers.web.intercept;

import com.yolt.providers.web.metric.KafkaMessageMetricsContext;
import com.yolt.providers.web.metric.PayloadSizeMonitoredKafkaTemplateWrapper;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.clienttokens.AbstractClientToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;

@Slf4j
@Component
public class RawDataProducer {

    private static final String PROVIDER_MDC_KEY = "provider";
    private static final String HTTP_REQUEST_METHOD = "http-request-method";
    private static final String HTTP_REQUEST_URI = "http-request-uri";
    private static final String RAW_DATA_SOURCE = "raw-data-source";

    private final PayloadSizeMonitoredKafkaTemplateWrapper<String, String> payloadSizeMonitoredKafkaTemplateWrapper;
    private final String rawDataTopicName;

    public RawDataProducer(@Value("${yolt.kafka.topics.providerRawData.topic-name}") final String rawDataTopicName,
                           final PayloadSizeMonitoredKafkaTemplateWrapper<String, String> payloadSizeMonitoredKafkaTemplateWrapper
    ) {
        this.rawDataTopicName = rawDataTopicName;
        this.payloadSizeMonitoredKafkaTemplateWrapper = payloadSizeMonitoredKafkaTemplateWrapper;
    }

    @Async
    public void sendDataAsync(final RawDataSource source, final String rawData, final String providerKey, final AbstractClientToken clientToken, final String httpRequestMethod, final String httpRequestUri) {
        final Message<String> message = MessageBuilder
                .withPayload(rawData)
                .setHeader(KafkaHeaders.TOPIC, rawDataTopicName)
                .setHeader(RAW_DATA_SOURCE, source.name())
                .setHeader(PROVIDER_MDC_KEY, providerKey)
                .setHeader(HTTP_REQUEST_METHOD, httpRequestMethod)
                .setHeader(HTTP_REQUEST_URI, httpRequestUri)
                .build();

        final ListenableFuture<SendResult<String, String>> future = payloadSizeMonitoredKafkaTemplateWrapper.send(message,
                KafkaMessageMetricsContext.from(rawDataTopicName, clientToken, providerKey));
        future.addCallback(
                result -> { // NO-OP: we don't want to do any action here on successful outcome
                },
                ex -> log.error("Failed to send raw data for provider {} to topic {}", providerKey, rawDataTopicName)
        );
    }
}
