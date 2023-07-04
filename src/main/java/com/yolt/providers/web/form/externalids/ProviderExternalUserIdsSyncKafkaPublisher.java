package com.yolt.providers.web.form.externalids;

import com.yolt.providers.common.domain.externalids.ProviderExternalUserIds;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotNull;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@Slf4j
public class ProviderExternalUserIdsSyncKafkaPublisher {

    private final String externalIdsTopic;
    private final Integer externalUserIdsSliceLimit;
    private final KafkaTemplate<String, ProviderExternalUserIds> kafkaTemplate;

    public ProviderExternalUserIdsSyncKafkaPublisher(
            @Value("${yolt.kafka.topics.formProviderExternalUserIds.topic-name}") final String externalIdsTopic,
            @Value("${lovebird.formProvider.externalUserIdsSliceLimit}") final Integer externalUserIdsSliceLimit,
            final KafkaTemplate<String, ProviderExternalUserIds> kafkaTemplate) {
        this.externalIdsTopic = externalIdsTopic;
        this.externalUserIdsSliceLimit = externalUserIdsSliceLimit;
        this.kafkaTemplate = kafkaTemplate;
    }

    public Void sendMessageSync(@NotNull final ProviderExternalUserIds providerExternalUserIds) {
        int idsListSize = providerExternalUserIds.getExternalUserIds().size();
        if (idsListSize > externalUserIdsSliceLimit) {
            String errorMessage = String.format("External user ids chunk for batch %s exceeded allowed limit of %s, actual size was %s",
                    providerExternalUserIds.getBatchId(), externalUserIdsSliceLimit, idsListSize);
            throw new UserExternalIdsChunkIncorrectSizeException(errorMessage);
        }

        Message<ProviderExternalUserIds> message = MessageBuilder
                .withPayload(providerExternalUserIds)
                .setHeader(KafkaHeaders.TOPIC, externalIdsTopic)
                .setHeader(KafkaHeaders.MESSAGE_KEY, providerExternalUserIds.getBatchId().toString())
                .build();

        try {
            // awaiting sync response for ordering guarantee
            kafkaTemplate.send(message).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error("Got interrupted while publishing external user ids chunk with batchId {}",
                    providerExternalUserIds.getBatchId(), e);
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            throw new PublishExternalUserIdsChunkFailedException("Failed to publish external user ids chunk with batchId " +
                    providerExternalUserIds.getBatchId(), e);
        } catch (TimeoutException e) {
            throw new PublishExternalUserIdsChunkFailedException("Timed out when publishing external user ids chunk with batchId " +
                    providerExternalUserIds.getBatchId(), e);
        }
        return null; // Reference Void type for closures (passing function as a method argument)
    }
}
