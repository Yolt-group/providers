package com.yolt.providers.web.intercept;

import com.yolt.providers.web.metric.KafkaMessageMetricsContext;
import com.yolt.providers.web.metric.PayloadSizeMonitoredKafkaTemplateWrapper;
import nl.ing.lovebird.clienttokens.AbstractClientToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.util.concurrent.ListenableFuture;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RawDataProducerTest {

    private static final String TOPIC_NAME = "topic-name";

    private RawDataProducer producer;

    @Mock
    private PayloadSizeMonitoredKafkaTemplateWrapper<String, String> payloadSizeMonitoredKafkaTemplateWrapperMock;

    @Captor
    private ArgumentCaptor<Message<String>> messageCaptor;

    @Mock
    private AbstractClientToken clientToken;

    @BeforeEach
    public void beforeEach() {
        when(clientToken.getClientGroupIdClaim()).thenReturn(UUID.randomUUID());
        when(payloadSizeMonitoredKafkaTemplateWrapperMock.send(any(Message.class), any(KafkaMessageMetricsContext.class))).thenReturn(mock(ListenableFuture.class));
        producer = new RawDataProducer(TOPIC_NAME, payloadSizeMonitoredKafkaTemplateWrapperMock);
    }

    @Test
    public void shouldPassAllTheParamsProperlyWhenSendingData() {
        // given
        String rawData = "rawData";
        String providerKey = "ABN_AMRO";
        String httpMethod = "GET";
        String httpUrl = "http://localhost/accounts";
        RawDataSource source = RawDataSource.FETCH_DATA;

        // when
        producer.sendDataAsync(source, rawData, providerKey, clientToken, httpMethod, httpUrl);

        // then
        verify(payloadSizeMonitoredKafkaTemplateWrapperMock).send(messageCaptor.capture(), any(KafkaMessageMetricsContext.class));
        Message<String> actual = messageCaptor.getValue();
        assertThat(actual.getPayload()).isEqualTo(rawData);
        MessageHeaders actualHeaders = actual.getHeaders();
        assertThat(actualHeaders.get(KafkaHeaders.TOPIC)).isEqualTo(TOPIC_NAME);
        assertThat(actualHeaders.get("raw-data-source")).isEqualTo("FETCH_DATA");
        assertThat(actualHeaders.get("provider")).isEqualTo(providerKey);
        assertThat(actualHeaders.get("http-request-method")).isEqualTo(httpMethod);
        assertThat(actualHeaders.get("http-request-uri")).isEqualTo(httpUrl);
    }
}
