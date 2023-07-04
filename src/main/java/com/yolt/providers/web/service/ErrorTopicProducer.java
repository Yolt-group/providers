package com.yolt.providers.web.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;

import java.util.UUID;

@Component
@Slf4j
public class ErrorTopicProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String kafkaTopicName;

    public ErrorTopicProducer(final KafkaTemplate<String, String> kafkaTemplate,
                              @Value("${lovebird.kafka.accountAndTransactionsErrorsTopic}") final String kafkaTopicName) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaTopicName = kafkaTopicName;
    }

    /**
     * Send a message to the errortopic, so we can retrieve the plain json for troubleshooting/debugging.
     *
     * @param json       The json you want to read from the errortopic.
     * @param messageKey The message key (should be userId according to our current 'standards')
     */
    public void sendMessage(final String json,
                            final UUID messageKey) {
        Message<String> message = MessageBuilder
                .withPayload(json)
                .setHeader(KafkaHeaders.TOPIC, kafkaTopicName)
                .setHeader(KafkaHeaders.MESSAGE_KEY, messageKey.toString())
                .build();

        ListenableFuture<SendResult<String, String>> future = kafkaTemplate.send(message);
        future.addCallback(
                result -> { // NO-OP: we don't want to do any action here on successful outcome
                },
                ex -> log.error("Failed to send provider service response.", ex)
        );
    }

    /**
     * NOTE: prefer {@link ErrorTopicProducer#sendMessage(String, UUID)}
     * This message should only be used if you absolutely don't have a 'user-id' to put on the message key (partition).
     * This could happen, for example during 'callbacks' where we don't know the user yet.
     */
    public void sendMessage(final String body) {
        // Just some random messageKey
        this.sendMessage(body, UUID.randomUUID());
    }
}
