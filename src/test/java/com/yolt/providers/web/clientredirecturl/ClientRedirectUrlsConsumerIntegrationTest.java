package com.yolt.providers.web.clientredirecturl;

import com.yolt.providers.web.configuration.IntegrationTestContext;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.clienttokens.test.TestClientTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.TimeUnit.SECONDS;
import static nl.ing.lovebird.clienttokens.constants.ClientTokenConstants.CLIENT_TOKEN_HEADER_NAME;
import static org.awaitility.Awaitility.await;

@IntegrationTestContext
class ClientRedirectUrlsConsumerIntegrationTest {

    private static final String CLIENT_REDIRECT_URLS_TOPIC = "clientRedirectUrlsTestTopic";

    final UUID clientGroupId = UUID.randomUUID();
    final UUID clientId = UUID.randomUUID();

    @Autowired
    private ClientRedirectUrlRepository clientRedirectUrlRepository;

    @Autowired
    private KafkaTemplate<String, ClientRedirectUrlDTO> kafkaTemplate;

    @Autowired
    private TestClientTokens testClientTokens;

    @BeforeEach
    public void beforeEach() {
    }

    @Test
    public void shouldCreateClientRedirectUrlBasedOnKafkaMessageForSendWithClientRedirectUrlCreatedMessageType() {
        // given
        UUID clientRedirectUrl1 = UUID.randomUUID();
        UUID clientRedirectUrl2 = UUID.randomUUID();
        Message<ClientRedirectUrlDTO> message1 = createTestMessageCreateClientRedirectUrl(clientId, clientRedirectUrl1);
        Message<ClientRedirectUrlDTO> message2 = createTestMessageCreateClientRedirectUrl(clientId, clientRedirectUrl2);

        // when
        var send = kafkaTemplate.send(message1);
        var send1 = kafkaTemplate.send(message2);
        CompletableFuture.allOf(send.completable(), send1.completable())
                .orTimeout(30, SECONDS);

        // then
        await().atMost(5, SECONDS).until(() ->
                clientRedirectUrlRepository.get(clientId, clientRedirectUrl1).isPresent() &&
                clientRedirectUrlRepository.get(clientId, clientRedirectUrl2).isPresent()
        );

    }

    private Message<ClientRedirectUrlDTO> createTestMessageCreateClientRedirectUrl(final UUID clientId, final UUID clientRedirectUrl) {
        ClientToken clientToken = testClientTokens.createClientToken(clientGroupId, clientId);
        return MessageBuilder
                .withPayload(ClientRedirectUrlDTO.builder()
                        .clientId(clientId)
                        .redirectUrlId(clientRedirectUrl)
                        .url("some super beautiful url")
                        .build())
                .setHeader(CLIENT_TOKEN_HEADER_NAME, clientToken.getSerialized())
                .setHeader(KafkaHeaders.TOPIC, CLIENT_REDIRECT_URLS_TOPIC)
                .setHeader(KafkaHeaders.MESSAGE_KEY, "mykey")
                .setHeader("message_type", ClientRedirectUrlMessageType.CLIENT_REDIRECT_URL_CREATED.name())
                .build();
    }

}
