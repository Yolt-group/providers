package com.yolt.providers.web.clientredirecturl;

import com.yolt.providers.web.authenticationmeans.ClientAuthenticationMeansService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.clienttokens.ClientToken;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static nl.ing.lovebird.clienttokens.constants.ClientTokenConstants.CLIENT_TOKEN_HEADER_NAME;

@Component
@AllArgsConstructor
@Slf4j
class ClientRedirectUrlConsumer {

    private final Clock clock;
    private final ClientRedirectUrlRepository clientRedirectUrlRepository;
    private final ClientAuthenticationMeansService clientAuthenticationMeansService;

    @KafkaListener(topics = "${yolt.kafka.topics.clientRedirectUrls.topic-name}",
            concurrency = "${yolt.kafka.topics.clientRedirectUrls.listener-concurrency}")
    public void clientRedirectUrlUpdate(@Header(value = CLIENT_TOKEN_HEADER_NAME) final ClientToken clientToken,
                                        @Payload ClientRedirectUrlDTO clientRedirectUrlUpdateDTO,
                                        @Header("message_type") String messageType) {
        try {

            log.debug("Got update for client redirect url for client {} and redirect url id {}",
                    clientRedirectUrlUpdateDTO.getClientId(),
                    clientRedirectUrlUpdateDTO.getRedirectUrlId());
            processEvent(clientToken, clientRedirectUrlUpdateDTO, parse(messageType));
        } catch (Exception e) {
            log.error("Unexpected exception reading client applications update: {}", e.getMessage(), e);
        }
    }

    private ClientRedirectUrlMessageType parse(String messageType) {
        // Handle JSON encoded strings. Remove when all pods on > 13.0.25
        // See: https://yolt.atlassian.net/browse/CHAP-145
        if (messageType.length() > 1 && messageType.startsWith("\"") && messageType.endsWith("\"")) {
            messageType = messageType.substring(1, messageType.length() - 1);
        }
        return ClientRedirectUrlMessageType.valueOf(messageType);
    }

    private void processEvent(final ClientToken clientToken, final ClientRedirectUrlDTO clientRedirectUrlDTO, final ClientRedirectUrlMessageType messageType) {
        if (ClientRedirectUrlMessageType.CLIENT_REDIRECT_URL_CREATED == messageType || ClientRedirectUrlMessageType.CLIENT_REDIRECT_URL_UPDATED == messageType) {
            clientRedirectUrlRepository.upsertClientRedirectUrl(map(clientRedirectUrlDTO));
            log.info("Client redirect url save or updated.");
        } else if (ClientRedirectUrlMessageType.CLIENT_REDIRECT_URL_DELETED == messageType) {
            UUID clientId = clientRedirectUrlDTO.getClientId();
            UUID redirectUrlId = clientRedirectUrlDTO.getRedirectUrlId();
            clientRedirectUrlRepository.delete(clientId, redirectUrlId);
            log.info("Client redirect url deleted.");
        }
    }

    private ClientRedirectUrl map(final ClientRedirectUrlDTO clientRedirectUrlDTO) {
        return new ClientRedirectUrl(clientRedirectUrlDTO.getClientId(),
                clientRedirectUrlDTO.getRedirectUrlId(),
                clientRedirectUrlDTO.getUrl(),
                Instant.now(clock));
    }

}
