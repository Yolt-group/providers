package com.yolt.providers.web.authenticationmeans;

import com.yolt.providers.web.exception.ClientAuthenticationMeansEventNotPropagatedException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.providerdomain.ServiceType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFuture;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@Slf4j
class ClientAuthenticationMeansEventDispatcherService {

    private static final String PAYLOAD_TYPE_HEADER = "payload-type";

    private final KafkaTemplate<String, ClientAuthenticationMeansDTO> kafkaTemplate;
    private final String kafkaTopicName;

    public ClientAuthenticationMeansEventDispatcherService(final KafkaTemplate<String, ClientAuthenticationMeansDTO> kafkaTemplate,
                                                           @Value("${yolt.kafka.topics.clientAuthenticationMeans.topic-name}") final String kafkaTopicName) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaTopicName = kafkaTopicName;
    }

    /**
     * Scraping providers only.
     */
    void publishAuthenticationMeansUpdatedEvent(final ClientProviderAuthenticationMeans authenticationMeans) {
        final ClientAuthenticationMeansDTO dto = new ClientAuthenticationMeansDTO(
                null,
                authenticationMeans.getClientId(),
                authenticationMeans.getProvider(),
                null,
                null
        );
        sendMessage(dto, ClientAuthenticationMeansMessageType.CLIENT_AUTHENTICATION_MEANS_UPDATED);
    }

    /**
     * Scraping providers only.
     */
    void publishAuthenticationMeansDeletedEvent(final UUID clientId,
                                                final String provider) {
        final ClientAuthenticationMeansDTO dto = new ClientAuthenticationMeansDTO(
                null,
                clientId,
                provider,
                null,
                null
        );
        sendMessage(dto, ClientAuthenticationMeansMessageType.CLIENT_AUTHENTICATION_MEANS_DELETED);
    }

    /**
     * Client level.
     */
    void publishAuthenticationMeansUpdatedEvent(final ClientRedirectUrlProviderClientConfiguration authenticationMeans) {
        final ClientAuthenticationMeansDTO dto = new ClientAuthenticationMeansDTO(
                null,
                authenticationMeans.getClientId(),
                authenticationMeans.getProvider(),
                authenticationMeans.getServiceType(),
                authenticationMeans.getRedirectUrlId()
        );
        sendMessage(dto, ClientAuthenticationMeansMessageType.CLIENT_AUTHENTICATION_MEANS_UPDATED);
    }

    /**
     * Client level.
     */
    void publishAuthenticationMeansDeletedEvent(@NonNull final UUID clientId,
                                                @NonNull final UUID redirectUrlId,
                                                @NonNull final ServiceType serviceType,
                                                @NonNull final String provider) {
        final ClientAuthenticationMeansDTO dto = new ClientAuthenticationMeansDTO(
                null,
                clientId,
                provider,
                serviceType,
                redirectUrlId
        );
        sendMessage(dto, ClientAuthenticationMeansMessageType.CLIENT_AUTHENTICATION_MEANS_DELETED);
    }

    /**
     * Client group level
     */
    void publishAuthenticationMeansUpdatedEvent(ClientGroupRedirectUrlProviderClientConfiguration authenticationMeans) {
        final ClientAuthenticationMeansDTO dto = new ClientAuthenticationMeansDTO(
                authenticationMeans.getClientGroupId(),
                /* clientId should be null, filled with clientGroupId for backward compat reasons */ authenticationMeans.getClientGroupId(),
                authenticationMeans.getProvider(),
                authenticationMeans.getServiceType(),
                authenticationMeans.getRedirectUrlId()
        );
        sendMessage(dto, ClientAuthenticationMeansMessageType.CLIENT_GROUP_AUTHENTICATION_MEANS_UPDATED);
    }

    /**
     * Client group level
     */
    void publishAuthenticationMeansDeletedGroupEvent(@NonNull final UUID clientGroupId,
                                                     @NonNull final UUID redirectUrlId,
                                                     @NonNull final ServiceType serviceType,
                                                     @NonNull final String provider) {
        final ClientAuthenticationMeansDTO dto = new ClientAuthenticationMeansDTO(
                clientGroupId,
                /* clientId should be null, filled with clientGroupId for backward compat reasons */ clientGroupId,
                provider,
                serviceType,
                redirectUrlId
        );
        sendMessage(dto, ClientAuthenticationMeansMessageType.CLIENT_GROUP_AUTHENTICATION_MEANS_DELETED);
    }

    private void sendMessage(final ClientAuthenticationMeansDTO authMeansDTO,
                             final ClientAuthenticationMeansMessageType type) {

        Message<ClientAuthenticationMeansDTO> message = MessageBuilder
                .withPayload(authMeansDTO)
                .setHeader(KafkaHeaders.TOPIC, kafkaTopicName)
                .setHeader(KafkaHeaders.MESSAGE_KEY, authMeansDTO.getClientId().toString())
                .setHeader(PAYLOAD_TYPE_HEADER, type.toString())
                .build();

        ListenableFuture<SendResult<String, ClientAuthenticationMeansDTO>> future = kafkaTemplate.send(message);
        future.addCallback(
                result -> log.info("Sent client configuration changes."),
                ex -> log.error("Failed sending client configuration changes.", ex)
        );
        try {
            future.get(1, TimeUnit.MINUTES);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new ClientAuthenticationMeansEventNotPropagatedException("Propagating to kafka failed due to exception ", e);
        }
    }
}
