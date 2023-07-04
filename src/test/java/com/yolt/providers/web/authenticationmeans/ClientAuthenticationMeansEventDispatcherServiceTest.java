package com.yolt.providers.web.authenticationmeans;

import nl.ing.lovebird.providerdomain.ServiceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;
import org.springframework.util.concurrent.ListenableFuture;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ClientAuthenticationMeansEventDispatcherServiceTest {

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID CLIENT_GROUP_ID = UUID.randomUUID();
    private static final String PROVIDER_KEY = "YOLT_PROVIDER";

    @Mock
    private KafkaTemplate<String, ClientAuthenticationMeansDTO> kafkaTemplate;

    @Mock
    private ListenableFuture<SendResult<String, ClientAuthenticationMeansDTO>> mockedFuture;

    @InjectMocks
    private ClientAuthenticationMeansEventDispatcherService clientAuthenticationMeansEventDispatcherService;

    @Captor
    private ArgumentCaptor<Message<ClientAuthenticationMeansDTO>> kafkaMessage;

    @Test
    public void shouldSendClientAuthenticationMeansUpdatedMessageForPublishAuthenticationMeansUpdatedEventWithClientProviderAuthenticationMeans() {
        // given
        ClientProviderAuthenticationMeans authenticationMeans = new ClientProviderAuthenticationMeans(CLIENT_ID, PROVIDER_KEY, null, null);
        when(kafkaTemplate.send(kafkaMessage.capture())).thenReturn(mockedFuture);

        // when
        clientAuthenticationMeansEventDispatcherService.publishAuthenticationMeansUpdatedEvent(authenticationMeans);

        // then
        Message<ClientAuthenticationMeansDTO> capturedMessage = kafkaMessage.getValue();
        assertKafkaMessage(capturedMessage, "CLIENT_AUTHENTICATION_MEANS_UPDATED", null, null, CLIENT_ID);
    }

    @Test
    public void shouldSendClientAuthenticationMeansDeletedMessageForPublishAuthenticationMeansDeletedEventWithClientIdAndProviderKey() {
        // given
        when(kafkaTemplate.send(kafkaMessage.capture())).thenReturn(mockedFuture);

        // when
        clientAuthenticationMeansEventDispatcherService.publishAuthenticationMeansDeletedEvent(CLIENT_ID, PROVIDER_KEY);

        // then
        Message<ClientAuthenticationMeansDTO> capturedMessage = kafkaMessage.getValue();
        assertKafkaMessage(capturedMessage, "CLIENT_AUTHENTICATION_MEANS_DELETED", null, null, CLIENT_ID);
    }

    @Test
    public void shouldSendClientAuthenticationMeansUpdatedMessageForPublishAuthenticationMeansUpdatedEventWithClientRedirectUrlProviderClientConfiguration() {
        // given
        UUID redirectUrlId = UUID.randomUUID();
        ServiceType serviceType = ServiceType.AIS;
        when(kafkaTemplate.send(kafkaMessage.capture())).thenReturn(mockedFuture);
        ClientRedirectUrlProviderClientConfiguration authenticationMeans = new ClientRedirectUrlProviderClientConfiguration(CLIENT_ID, redirectUrlId, ServiceType.AIS, PROVIDER_KEY, null, null);

        // when
        clientAuthenticationMeansEventDispatcherService.publishAuthenticationMeansUpdatedEvent(authenticationMeans);

        // then
        Message<ClientAuthenticationMeansDTO> capturedMessage = kafkaMessage.getValue();
        assertKafkaMessage(capturedMessage, "CLIENT_AUTHENTICATION_MEANS_UPDATED", redirectUrlId, serviceType, CLIENT_ID);
    }

    @Test
    public void shouldSendClientAuthenticationMeansDeletedMessageForPublishAuthenticationMeansDeletedEventWithClientIdRedirectUrlIdServiceTypeAndProviderKey() {
        // given
        UUID redirectUrlId = UUID.randomUUID();
        ServiceType serviceType = ServiceType.AIS;
        when(kafkaTemplate.send(kafkaMessage.capture())).thenReturn(mockedFuture);

        // when
        clientAuthenticationMeansEventDispatcherService.publishAuthenticationMeansDeletedEvent(CLIENT_ID, redirectUrlId, serviceType, PROVIDER_KEY);

        // then
        Message<ClientAuthenticationMeansDTO> capturedMessage = kafkaMessage.getValue();
        assertKafkaMessage(capturedMessage, "CLIENT_AUTHENTICATION_MEANS_DELETED", redirectUrlId, serviceType, CLIENT_ID);
    }

    @Test
    public void shouldSendClientGroupAuthenticationMeansUpdatedMessageForPublishAuthenticationMeansUpdateEventWithClientGroupRedirectUrlProviderClientConfiguration() {
        // given
        UUID redirectUrlId = UUID.randomUUID();
        ServiceType serviceType = ServiceType.AIS;
        when(kafkaTemplate.send(kafkaMessage.capture())).thenReturn(mockedFuture);
        ClientGroupRedirectUrlProviderClientConfiguration authenticationMeans = new ClientGroupRedirectUrlProviderClientConfiguration(CLIENT_GROUP_ID, redirectUrlId, ServiceType.AIS, PROVIDER_KEY, null, null);

        // when
        clientAuthenticationMeansEventDispatcherService.publishAuthenticationMeansUpdatedEvent(authenticationMeans);

        // then
        Message<ClientAuthenticationMeansDTO> capturedMessage = kafkaMessage.getValue();
        assertKafkaMessage(capturedMessage, "CLIENT_GROUP_AUTHENTICATION_MEANS_UPDATED", redirectUrlId, serviceType, CLIENT_GROUP_ID);
    }

    @Test
    public void shouldSendClientGroupAuthenticationMeansDeletedMessageForPublishAuthenticationMeansDeletedGroupEventWithClientGroupIdRedirectUrlIdServiceTypeAndProviderKey() {
        // given
        UUID redirectUrlId = UUID.randomUUID();
        ServiceType serviceType = ServiceType.AIS;
        when(kafkaTemplate.send(kafkaMessage.capture())).thenReturn(mockedFuture);

        // when
        clientAuthenticationMeansEventDispatcherService.publishAuthenticationMeansDeletedGroupEvent(CLIENT_GROUP_ID, redirectUrlId, serviceType, PROVIDER_KEY);

        // then
        Message<ClientAuthenticationMeansDTO> capturedMessage = kafkaMessage.getValue();
        assertKafkaMessage(capturedMessage, "CLIENT_GROUP_AUTHENTICATION_MEANS_DELETED", redirectUrlId, serviceType, CLIENT_GROUP_ID);
    }

    private void assertKafkaMessage(Message<ClientAuthenticationMeansDTO> capturedMessage, String payloadMessageType, UUID redirectUrlId, ServiceType serviceType, UUID clientId) {
        if (payloadMessageType.contains("GROUP")) {
            assertThat(capturedMessage.getPayload().getClientGroupId()).isEqualTo(clientId);
        }
        assertThat(capturedMessage.getHeaders()).containsEntry("payload-type", payloadMessageType);
        assertThat(capturedMessage.getPayload().getClientId()).isEqualTo(clientId);
        assertThat(capturedMessage.getPayload().getProvider()).isEqualTo(PROVIDER_KEY);
        assertThat(capturedMessage.getPayload().getRedirectUrlId()).isEqualTo(redirectUrlId);
        assertThat(capturedMessage.getPayload().getServiceType()).isEqualTo(serviceType);
    }
}