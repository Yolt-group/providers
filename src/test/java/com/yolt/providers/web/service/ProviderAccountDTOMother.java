package com.yolt.providers.web.service;

import nl.ing.lovebird.extendeddata.account.*;
import nl.ing.lovebird.extendeddata.common.AccountReferenceDTO;
import nl.ing.lovebird.extendeddata.common.BalanceAmountDTO;
import nl.ing.lovebird.extendeddata.common.CurrencyCode;
import nl.ing.lovebird.extendeddata.transaction.AccountReferenceType;
import nl.ing.lovebird.providerdomain.AccountType;
import nl.ing.lovebird.providerdomain.ProviderAccountDTO;
import nl.ing.lovebird.providerdomain.ProviderAccountNumberDTO;
import nl.ing.lovebird.providerdomain.ProviderCreditCardDTO;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

public class ProviderAccountDTOMother {

    private static final String VALID_LUHN_NUMBER = "9713744327450286";
    private static final String VALID_SORTCODEACCOUNTNUMBER = "";

    public static ProviderAccountDTO.ProviderAccountDTOBuilder newValidCurrentAccountDTOBuilder() {
        String accountId = UUID.randomUUID().toString();
        String accountName = "Account - " + UUID.randomUUID().toString();
        return new ProviderAccountDTO(
                AccountType.CURRENT_ACCOUNT,
                ZonedDateTime.now(),
                BigDecimal.ONE,
                BigDecimal.ZERO,
                accountId,
                null,
                new ProviderAccountNumberDTO(
                        ProviderAccountNumberDTO.Scheme.SORTCODEACCOUNTNUMBER,
                        VALID_SORTCODEACCOUNTNUMBER
                ),
                null,
                accountName,
                CurrencyCode.GBP,
                Boolean.FALSE,
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                newValidExtendedAccountDTOBuilder()
                        .resourceId(accountId)
                        .name(accountName)
                        .accountReferences(Collections.singletonList(new AccountReferenceDTO(AccountReferenceType.SORTCODEACCOUNTNUMBER, VALID_SORTCODEACCOUNTNUMBER)))
                        .build()
                ,
                Collections.emptyMap(),
                null
        ).toBuilder();
    }

    public static ProviderAccountDTO.ProviderAccountDTOBuilder newValidUnmaskedCreditCardAccountDTOBuilder() {
        String accountId = UUID.randomUUID().toString();
        String accountName = "Account name";
        return newValidCurrentAccountDTOBuilder()
                .accountId(accountId)
                .name(accountName)
                .creditCardData(newValidCreditCardData())
                .accountMaskedIdentification(VALID_LUHN_NUMBER)
                .yoltAccountType(AccountType.CREDIT_CARD)
                .extendedAccount(
                        newValidExtendedAccountDTOBuilder()
                                .resourceId(accountId)
                                .name(accountName)
                                .accountReferences(Arrays.asList(
                                        new AccountReferenceDTO(AccountReferenceType.PAN, VALID_LUHN_NUMBER),
                                        new AccountReferenceDTO(AccountReferenceType.SORTCODEACCOUNTNUMBER, VALID_SORTCODEACCOUNTNUMBER)
                                )).build()
                );
    }

    public static ProviderAccountDTO.ProviderAccountDTOBuilder newValidMaskedCreditCardAccountDTOBuilder() {
        String accountId = UUID.randomUUID().toString();
        String accountName = "Account name";
        return newValidUnmaskedCreditCardAccountDTOBuilder()
                .accountId(accountId)
                .name(accountName)
                .accountMaskedIdentification("XXXXXXXXXXX1234")
                .extendedAccount(
                        newValidExtendedAccountDTOBuilder()
                                .resourceId(accountId)
                                .name(accountName)
                                .accountReferences(Arrays.asList(
                                        new AccountReferenceDTO(AccountReferenceType.PAN, "XXXXXXXXXXXX1234"),
                                        new AccountReferenceDTO(AccountReferenceType.SORTCODEACCOUNTNUMBER, VALID_SORTCODEACCOUNTNUMBER)
                                )).build()
                );
    }

    public static ExtendedAccountDTO.ExtendedAccountDTOBuilder newValidExtendedAccountDTOBuilder() {
        return new ExtendedAccountDTO(
                UUID.randomUUID().toString(),
                Arrays.asList(
                        new AccountReferenceDTO(AccountReferenceType.SORTCODEACCOUNTNUMBER, UUID.randomUUID().toString())
                ),
                CurrencyCode.GBP,
                UUID.randomUUID().toString(),
                null,
                null,
                Status.ENABLED,
                null,
                null,
                UsageType.PRIVATE,
                null,
                Arrays.asList(
                        new BalanceDTO(
                                new BalanceAmountDTO(CurrencyCode.GBP, BigDecimal.ZERO),
                                BalanceType.AVAILABLE,
                                ZonedDateTime.now(),
                                ZonedDateTime.now(),
                                null),
                        new BalanceDTO(
                                new BalanceAmountDTO(CurrencyCode.GBP, BigDecimal.ONE),
                                BalanceType.CLOSING_BOOKED,
                                ZonedDateTime.now(),
                                ZonedDateTime.now(),
                                null
                        )
                )
        ).toBuilder();
    }

    public static ProviderCreditCardDTO newValidCreditCardData() {
        return new ProviderCreditCardDTO(null, null, null, null, BigDecimal.TEN,
                null, null, null, null,
                null, null, null
        );
    }

}
