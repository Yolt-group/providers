package com.yolt.providers.web.service.domain;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.providerdomain.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/* TODO This class should be removed during epic: (Decouple URL providers from yolt-shared-dtos)  https://yolt.atlassian.net/browse/C4PO-3738
    It's content should be moved to proper class
 */
@Slf4j
@NoArgsConstructor
@Component
public class ProviderAccountUtil {

    // This is modified validate method from ProviderAccountDTO
    // But instead of throwing exceptions on validating
    // it simply removes standing orders and directDebits that don't pass validation
    public ProviderAccountDTO validateAndFilterOptionalDetails(ProviderAccountDTO account) {
        Objects.requireNonNull(account.getYoltAccountType(), "yoltAccountType");
        Objects.requireNonNull(account.getLastRefreshed(), "lastRefreshed");
        Objects.requireNonNull(account.getCurrency(), "currency");
        Objects.requireNonNull(account.getAccountId(), "accountId");
        Objects.requireNonNull(account.getName(), "name");
        Objects.requireNonNull(account.getTransactions(), "transactions");

        if (account.getAvailableBalance() == null && account.getCurrentBalance() == null) {
            throw new IllegalArgumentException("At least one of the two fields 'availableBalance' and 'currentBalance' can't be null");
        }

        if (account.getYoltAccountType() == AccountType.CREDIT_CARD) {
            Objects.requireNonNull(account.getCreditCardData(), "creditCardData");
        } else if (account.getCreditCardData() != null) {
            throw new IllegalArgumentException("Field 'creditCardData' must be null when 'yoltAccountType != AccountType.CREDIT_CARD'");
        }

        ProviderAccountDTO.ProviderAccountDTOBuilder builder = account.toBuilder();

        for (ProviderTransactionDTO transaction : account.getTransactions()) {
            transaction.validate();
        }

        builder.directDebits(filterDirectDebits(account));
        builder.standingOrders(filterStandingOrders(account));

        if (account.getAccountNumber() != null) {
            account.getAccountNumber().validate();
        }

        return builder.build();
    }

    private static List<StandingOrderDTO> filterStandingOrders(ProviderAccountDTO account) {
        List<StandingOrderDTO> filteredStandingOrders = new ArrayList<>();
        if (account.getStandingOrders() != null) {
            for (StandingOrderDTO standingOrder : account.getStandingOrders()) {
                try {
                    standingOrder.validate();
                    filteredStandingOrders.add(standingOrder);
                } catch (Exception ignored) {
                    //Ignore
                }
            }
        }
        return filteredStandingOrders;
    }

    private static List<DirectDebitDTO> filterDirectDebits(ProviderAccountDTO account) {
        List<DirectDebitDTO> filteredDirectDebits = new ArrayList<>();
        if (account.getDirectDebits() != null) {
            for (DirectDebitDTO directDebit : account.getDirectDebits()) {
                try {
                    directDebit.validate();
                    filteredDirectDebits.add(directDebit);
                } catch (Exception ignored) {
                    //Ignore
                }
            }
        }
        return filteredDirectDebits;
    }

}
