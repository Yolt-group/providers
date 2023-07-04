package com.yolt.providers.web.service;

import com.yolt.providers.web.metric.KafkaMessageMetricsContext;
import com.yolt.providers.web.metric.PayloadSizeMonitoredKafkaTemplateWrapper;
import com.yolt.providers.web.service.dto.IngestionAccountDTO;
import com.yolt.providers.web.service.dto.IngestionRequestDTO;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.clienttokens.ClientUserToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static nl.ing.lovebird.clienttokens.constants.ClientTokenConstants.CLIENT_TOKEN_HEADER_NAME;

@Slf4j
@Service
public class AccountsProducer {

    private static final Integer MAX_TRANSACTIONS_FOR_SINGLE_MESSAGE = 5_000;

    private final String accountAndTransactionsTopic;
    private final PayloadSizeMonitoredKafkaTemplateWrapper<String, IngestionRequestDTO> payloadSizeMonitoredKafkaTemplateWrapper;
    private final AccountsAndTransactionsClient accountsAndTransactionsClient;

    public AccountsProducer(@Value("${yolt.kafka.topics.ingestion-requests.topic-name}") String accountAndTransactionsTopic,
                            final PayloadSizeMonitoredKafkaTemplateWrapper<String, IngestionRequestDTO> payloadSizeMonitoredKafkaTemplateWrapper,
                            final AccountsAndTransactionsClient accountsAndTransactionsClient) {
        this.accountAndTransactionsTopic = accountAndTransactionsTopic;
        this.payloadSizeMonitoredKafkaTemplateWrapper = payloadSizeMonitoredKafkaTemplateWrapper;
        this.accountsAndTransactionsClient = accountsAndTransactionsClient;
    }

    public void publishAccountAndTransactions(
            final UUID activityId,
            final UUID userSiteId,
            final UUID siteId,
            final List<IngestionAccountDTO> ingestionAccountDTOs,
            final ClientUserToken clientUserToken,
            final String provider) {

        IngestionRequestDTO ingestionRequestDTO = IngestionRequestDTO.builder()
                .activityId(activityId)
                .userSiteId(userSiteId)
                .siteId(siteId)
                .ingestionAccounts(ingestionAccountDTOs)
                .build();

        long count = ingestionAccountDTOs.stream().mapToInt(it -> it.getTransactions().size()).sum();
        if (count > MAX_TRANSACTIONS_FOR_SINGLE_MESSAGE) {
            // See YTRN-1313. On kafka  we can only push 10Mb of data. The limit is 10Mb data in it's uncompressed form (even though we do zip it).
            // See https://issues.apache.org/jira/browse/KAFKA-4169 or org.apache.kafka.clients.producer.KafkaProducer#ensureValidRecordSize
            // Another option is to do pagination. However, a transaction does not have a reference to an account. In fact, an account can not be
            // uniquely identified due to data quality issues at the bank. This, in fact, is the job of A&T to 'reconcile' it. This would require
            // us to numerate the accounts by index, and paginate the transactions with a reference to the numeral index of that account.
            // Because this happens very little (add-banks of customers with a lot of data), we chose to push it over HTTPS. Note that this
            // 'fallback' over https comes with a slight degradation of resilience as the uptime of A&T is required to send this data.
            log.info("Posting {} accounts and transactions over HTTP. Kafka threshold is set to {} transactions.", count, MAX_TRANSACTIONS_FOR_SINGLE_MESSAGE);
            accountsAndTransactionsClient.postProviderAccounts(clientUserToken, ingestionRequestDTO);
            return;
        }

        Message<IngestionRequestDTO> message = MessageBuilder
                .withPayload(ingestionRequestDTO)
                .setHeader(KafkaHeaders.TOPIC, accountAndTransactionsTopic)
                .setHeader(KafkaHeaders.MESSAGE_KEY, clientUserToken.getUserIdClaim().toString())
                .setHeader(CLIENT_TOKEN_HEADER_NAME, clientUserToken.getSerialized())
                .build();

        payloadSizeMonitoredKafkaTemplateWrapper.send(message,
                KafkaMessageMetricsContext.from(accountAndTransactionsTopic, clientUserToken.getClientIdClaim().toString(), provider));
    }
}
