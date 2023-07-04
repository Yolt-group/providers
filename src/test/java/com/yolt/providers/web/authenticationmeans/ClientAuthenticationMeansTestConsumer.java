package com.yolt.providers.web.authenticationmeans;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@AllArgsConstructor
class ClientAuthenticationMeansTestConsumer {

    private final List<Consumed> consumed = new ArrayList<>();

    @KafkaListener(
            topics = "${yolt.kafka.topics.clientAuthenticationMeans.topic-name}",
            concurrency = "${yolt.kafka.topics.clientAuthenticationMeans.listener-concurrency}"
    )
    public void consume(@Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key, @Payload ClientAuthenticationMeansDTO payload,
                        @Header("payload-type") String payloadType) {
        consumed.add(new Consumed(key, payloadType, payload));
    }

    public List<Consumed> getConsumed() {
        return consumed;
    }

    public void reset() {
        consumed.clear();
    }

    @Data
    @AllArgsConstructor
    static class Consumed {
        private final String key;
        private final String payloadType;
        private final ClientAuthenticationMeansDTO value;
    }
}