package com.yolt.providers.web.service;

import com.yolt.providers.common.ais.DataProviderResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.providerdomain.ProviderAccountDTO;
import nl.ing.lovebird.providerdomain.ProviderTransactionDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.partitioningBy;
import static java.util.stream.Collectors.toList;

@Component
@Slf4j
public class TransactionsDataLimiter {

    private final boolean logEnabled;

    public TransactionsDataLimiter(@Value("${yolt.providers.transactions.limiter.log.enabled:false}") final boolean logEnabled) {
        this.logEnabled = logEnabled;
    }

    public DataProviderResponse limitResponseData(final DataProviderResponse dataProviderResponse, final Instant transactionFetchStartTime) {
        return new DataProviderResponse(dataProviderResponse.getAccounts()
                .stream()
                .map(accountDTO -> limitTransactionsDataInAccount(accountDTO, transactionFetchStartTime))
                .collect(Collectors.toList()));
    }

    private ProviderAccountDTO limitTransactionsDataInAccount(final ProviderAccountDTO providerAccountDTO, final Instant transactionFetchStartTime) {
        return providerAccountDTO.toBuilder()
                .transactions(limitTransactionsDataInAccount(providerAccountDTO.getTransactions(), transactionFetchStartTime))
                .build();
    }

    private List<ProviderTransactionDTO> limitTransactionsDataInAccount(final List<ProviderTransactionDTO> providerTransactions, final Instant transactionFetchStartTime) {
        // because transaction dateTime may have only 'date' part, we need to shift startTime to last minute of previous day
        final ZonedDateTime startDateTime = ZonedDateTime.ofInstant(transactionFetchStartTime, ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.DAYS)
                .minusMinutes(1L);

        var transactionsIsAfterFetchStartDateTime = providerTransactions
                .stream()
                .collect(partitioningBy(isAfterDate(startDateTime)));

        var transactionsAfterStartDateTime = transactionsIsAfterFetchStartDateTime.getOrDefault(true, emptyList());
        var transactionsNotAfterStartDateTime = transactionsIsAfterFetchStartDateTime.getOrDefault(false, emptyList());

        if (logEnabled) {
            outputInTabular(transactionsNotAfterStartDateTime, startDateTime,
                    (header, group) -> log.warn("Transactions with datetime not after transaction fetch start datetime or transaction datetime not available:\n{}\n{}", header, String.join("\n", group)));
        }

        return transactionsAfterStartDateTime;
    }

    private Predicate<ProviderTransactionDTO> isAfterDate(final ZonedDateTime startDateTime) {
        return providerTransactionDTO -> {
            final ZonedDateTime transactionDateTime = providerTransactionDTO.getDateTime();

            if (transactionDateTime == null) {
                // dateTime is required field, however, this check may happen before validation, so we need to filter out possible 'null' cases
                return false;
            }

            return transactionDateTime.isAfter(startDateTime);
        };
    }

    static void outputInTabular(final @NonNull List<ProviderTransactionDTO> providerTransactions,
                                final @NonNull ZonedDateTime startDateTime,
                                final @NonNull BiConsumer<String, List<String>> groupOut) {

        var format = "|%1$-32s|%2$-48s|%3$-24s|";

        var counter = new AtomicInteger();
        providerTransactions.stream()
                .collect(groupingBy(ignored -> counter.getAndIncrement() / 50)) // output in chunks of 50
                .values()
                .forEach(group -> {
                    var tuples = group.stream()
                            .map(trx -> String.format(format,
                                    Optional.ofNullable(trx.getExternalId()).orElse("n/a"),
                                    Optional.ofNullable(trx.getDateTime()),
                                    startDateTime
                            ))
                            .collect(toList());

                    var header = String.format(format,
                            "external-id",
                            "transaction datetime",
                            "start datetime");

                    groupOut.accept(header, tuples);
                });
    }

}
