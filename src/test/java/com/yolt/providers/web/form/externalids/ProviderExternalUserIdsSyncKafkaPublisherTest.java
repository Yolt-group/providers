package com.yolt.providers.web.form.externalids;

import com.yolt.providers.common.domain.externalids.ProviderExternalUserIds;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;
import org.springframework.util.concurrent.ListenableFuture;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static com.yolt.providers.common.ProviderKey.BUDGET_INSIGHT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ProviderExternalUserIdsSyncKafkaPublisherTest {

    private static final  String EXTERNAL_IDS_TOPIC = "formProviderExternalUserIds";
    private static final  Integer EXTERNAL_USER_IDS_SLICE_LIMIT = 2;

    @Mock
    private KafkaTemplate<String, ProviderExternalUserIds> kafkaTemplate;

    @Mock
    private ListenableFuture<SendResult<String, ProviderExternalUserIds>> mockedFuture;

    @Captor
    private ArgumentCaptor<Message<ProviderExternalUserIds>> kafkaMessage;

    private ProviderExternalUserIdsSyncKafkaPublisher externalUserIdsPublisher;

    @BeforeEach
    public void beforeEach() {
        externalUserIdsPublisher = new ProviderExternalUserIdsSyncKafkaPublisher(EXTERNAL_IDS_TOPIC, EXTERNAL_USER_IDS_SLICE_LIMIT, kafkaTemplate);
    }

    @Test
    public void shouldThrowUserExternalIdsChunkIncorrectSizeExceptionForSendMessageSyncWhenChunkLimitExceeded() {
        // given
        ProviderExternalUserIds externalUserIds = new ProviderExternalUserIds(UUID.randomUUID(), UUID.randomUUID(),
                BUDGET_INSIGHT, Arrays.asList("1", "2", "3", "4", "5"), true);

        // when
        ThrowableAssert.ThrowingCallable sendMessageSyncCallable = () -> externalUserIdsPublisher.sendMessageSync(externalUserIds);

        // then
        assertThatThrownBy(sendMessageSyncCallable)
                .isInstanceOf(UserExternalIdsChunkIncorrectSizeException.class);
    }

    @Test
    public void shouldPublishChunkToTopicForSendMessageSyncWhenChunkLimitNotExceeded() {
        // given
        ProviderExternalUserIds externalUserIds = new ProviderExternalUserIds(UUID.randomUUID(), UUID.randomUUID(),
                BUDGET_INSIGHT, Arrays.asList("1", "2"), true);
        when(kafkaTemplate.send(kafkaMessage.capture())).thenReturn(mockedFuture);

        // when
        externalUserIdsPublisher.sendMessageSync(externalUserIds);

        // then
        List<Message<ProviderExternalUserIds>> capturedMessages = kafkaMessage.getAllValues();
        assertThat(capturedMessages).hasSize(1);
        ProviderExternalUserIds providerExternalUserIds = capturedMessages.get(0).getPayload();
        assertThat(providerExternalUserIds.getProvider()).isEqualTo(BUDGET_INSIGHT);
        assertThat(providerExternalUserIds.getExternalUserIds()).isEqualTo(externalUserIds.getExternalUserIds());
        assertThat(providerExternalUserIds.getBatchId()).isEqualTo(externalUserIds.getBatchId());
    }
}
