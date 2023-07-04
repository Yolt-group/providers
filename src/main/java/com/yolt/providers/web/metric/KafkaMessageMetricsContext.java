package com.yolt.providers.web.metric;

import io.micrometer.core.instrument.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import nl.ing.lovebird.clienttokens.AbstractClientToken;
import nl.ing.lovebird.clienttokens.ClientToken;

import java.util.List;

@lombok.Value
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class KafkaMessageMetricsContext {
    String topic;
    String clientId;
    String provider;

    public static KafkaMessageMetricsContext from(final String topic,
                                                  final String clientId,
                                                  final String provider) {
        return new KafkaMessageMetricsContext(topic, clientId, provider);
    }

    public static KafkaMessageMetricsContext from(final String topic,
                                                  final AbstractClientToken clientToken,
                                                  final String provider) {
        return from(topic,
                clientToken instanceof ClientToken ? ((ClientToken) clientToken).getClientIdClaim().toString() : clientToken.getClientGroupIdClaim().toString(),
                provider);
    }

    public List<Tag> asTags() {
        return List.of(
                Tag.of("clientId", clientId),
                Tag.of("provider", provider),
                Tag.of("topic", topic)
        );
    }
}
