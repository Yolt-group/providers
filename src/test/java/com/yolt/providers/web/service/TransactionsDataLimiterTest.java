package com.yolt.providers.web.service;

import com.yolt.providers.common.ais.DataProviderResponse;
import nl.ing.lovebird.providerdomain.ProviderAccountDTO;
import nl.ing.lovebird.providerdomain.ProviderTransactionDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TransactionsDataLimiterTest {

    private TransactionsDataLimiter transactionsDataLimiter;

    @BeforeEach
    public void beforeEach() {
        transactionsDataLimiter = new TransactionsDataLimiter(true);
    }

    @Test
    public void shouldReturnDataProviderResponseWithLimitedTransactionsForLimitResponseDataWhenDateTimeIsNullForSingleTransaction() {
        // given
        Instant now = Instant.now();
        Instant dayAfter = Instant.now().plus(1L, ChronoUnit.DAYS);

        // when
        DataProviderResponse response = transactionsDataLimiter.limitResponseData(prepareResponseWithTransactions(null, dayAfter), now);

        // then
        List<ProviderTransactionDTO> transactions = response.getAccounts().get(0).getTransactions();
        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getDateTime()).isEqualTo(ZonedDateTime.ofInstant(dayAfter, ZoneOffset.UTC));
    }

    @Test
    public void shouldReturnDataProviderResponseWithLimitedTransactionsForLimitResponseDataWhenTransactionBeforeStartFetchTime() {
        // given
        Instant now = Instant.now();
        Instant dayBefore = Instant.now().minus(1L, ChronoUnit.DAYS);
        Instant dayAfter = Instant.now().plus(1L, ChronoUnit.DAYS);

        // when
        DataProviderResponse response = transactionsDataLimiter.limitResponseData(prepareResponseWithTransactions(dayBefore, dayAfter), now);

        // then
        List<ProviderTransactionDTO> transactions = response.getAccounts().get(0).getTransactions();
        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getDateTime()).isEqualTo(ZonedDateTime.ofInstant(dayAfter, ZoneOffset.UTC));
    }

    @Test
    public void shouldReturnDataProviderResponseWithTheSameTransactionForLimitResponseDataWhenTransactionAfterStartFetchTime() {
        // given
        Instant now = Instant.now();
        Instant sameDay = Instant.now();
        Instant dayAfter = Instant.now().plus(1L, ChronoUnit.DAYS);

        // when
        DataProviderResponse response = transactionsDataLimiter.limitResponseData(prepareResponseWithTransactions(sameDay, dayAfter), now);

        // then
        List<ProviderTransactionDTO> transactions = response.getAccounts().get(0).getTransactions();
        assertThat(transactions).hasSize(2);
        assertThat(transactions).extracting(ProviderTransactionDTO::getDateTime)
                .containsExactlyInAnyOrder(ZonedDateTime.ofInstant(sameDay, ZoneOffset.UTC), ZonedDateTime.ofInstant(dayAfter, ZoneOffset.UTC));
    }

    private static DataProviderResponse prepareResponseWithTransactions(Instant... trxDateTimes) {
        List<ProviderTransactionDTO> transactions = new ArrayList<>();
        Arrays.stream(trxDateTimes).forEach(dateTime -> transactions.add(ProviderTransactionDTO.builder()
                .dateTime(dateTime == null ? null : ZonedDateTime.ofInstant(dateTime, ZoneOffset.UTC))
                .build()));
        ProviderAccountDTO account = ProviderAccountDTO.builder()
                .transactions(transactions)
                .build();
        return new DataProviderResponse(Collections.singletonList(account));
    }
}
