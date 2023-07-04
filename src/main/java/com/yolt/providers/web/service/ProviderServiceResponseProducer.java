package com.yolt.providers.web.service;

import com.yolt.providers.web.form.ProviderMessageType;
import com.yolt.providers.web.service.dto.FetchDataResultDTO;
import com.yolt.providers.web.service.dto.NoSupportedAccountDTO;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.clienttokens.ClientUserToken;
import nl.ing.lovebird.clienttokens.constants.ClientTokenConstants;
import nl.ing.lovebird.providershared.callback.CallbackResponseDTO;
import nl.ing.lovebird.providershared.form.LoginSucceededDTO;
import nl.ing.lovebird.providershared.form.ProviderServiceMAFResponseDTO;
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
public class ProviderServiceResponseProducer {

    private static final String PAYLOAD_TYPE_HEADER = "payload-type";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String kafkaTopicName;

    public ProviderServiceResponseProducer(final KafkaTemplate<String, Object> kafkaTemplate,
                                           @Value("${yolt.kafka.topics.providerAccounts.topic-name}") final String providerAccountsTopicName) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaTopicName = providerAccountsTopicName;
    }

    public void sendMessage(final FetchDataResultDTO fetchDataResultDTO,
                            final ClientUserToken clientUserToken) {
        sendMessage(fetchDataResultDTO, ProviderMessageType.PROVIDER_SERVICE_RESPONSE, clientUserToken);
    }

    public void sendMessage(final ProviderServiceMAFResponseDTO providerServiceMAFResponseDTO,
                            final ClientUserToken clientUserToken) {
        sendMessage(providerServiceMAFResponseDTO, ProviderMessageType.MFA, clientUserToken);
    }

    public void sendMessage(final CallbackResponseDTO callbackResponseDTO,
                            final ClientUserToken clientUserToken) {
        sendMessage(callbackResponseDTO, ProviderMessageType.CALLBACK_RESPONSE, clientUserToken);
    }

    public void sendMessage(final LoginSucceededDTO loginSucceededDTO, final ClientUserToken clientUserToken) {
        sendMessage(loginSucceededDTO, ProviderMessageType.LOGIN_SUCCEEDED, clientUserToken);
    }

    public void sendNoSupportedAccountsMessage(final UUID userSiteId, final ClientUserToken clientUserToken) {
        sendMessage(new NoSupportedAccountDTO(clientUserToken.getUserIdClaim(), userSiteId), ProviderMessageType.NO_SUPPORTED_ACCOUNTS, clientUserToken);
    }

    private void sendMessage(final Object object,
                             final ProviderMessageType providerMessageType,
                             final ClientUserToken clientUserToken) {
        MessageBuilder<Object> messageBuilder = MessageBuilder
                .withPayload(object)
                .setHeader(KafkaHeaders.MESSAGE_KEY, clientUserToken.getUserIdClaim().toString())
                .setHeader(PAYLOAD_TYPE_HEADER, providerMessageType.name());
        messageBuilder.setHeader(ClientTokenConstants.CLIENT_TOKEN_HEADER_NAME, clientUserToken.getSerialized());
        Message<Object> message = messageBuilder
                .setHeader(KafkaHeaders.TOPIC, kafkaTopicName)
                .build();

        ListenableFuture<SendResult<String, Object>> future = kafkaTemplate.send(message);
        future.addCallback(
                result -> { // NO-OP: we don't want to do any action here on successful outcome
                },
                ex -> log.error("Failed to send provider service response.", ex)
        );
    }
}
