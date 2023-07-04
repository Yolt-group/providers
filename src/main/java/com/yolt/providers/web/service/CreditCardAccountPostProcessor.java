package com.yolt.providers.web.service;

import com.yolt.providers.web.service.domain.CreditCardValidator;
import nl.ing.lovebird.extendeddata.account.ExtendedAccountDTO;
import nl.ing.lovebird.extendeddata.common.AccountReferenceDTO;
import nl.ing.lovebird.extendeddata.transaction.AccountReferenceType;
import nl.ing.lovebird.providerdomain.AccountType;
import nl.ing.lovebird.providerdomain.ProviderAccountDTO;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CreditCardAccountPostProcessor {

    private static final int NUMBER_OF_UNMASKED_CHARS = 4;
    private static final String MASKING_SYMBOL = "X";

    public ProviderAccountDTO postProcessAccount(final ProviderAccountDTO account) {
        if (!isEligibleForPostProcessing(account)) {
            return account;
        }

        ProviderAccountDTO resultingAccount = postProcessAccountMaskedIdentification(account);
        resultingAccount = postProcessAccountReferences(resultingAccount);

        return resultingAccount;
    }

    private boolean isEligibleForPostProcessing(final ProviderAccountDTO account) {
        return account != null && AccountType.CREDIT_CARD.equals(account.getYoltAccountType());
    }

    private ProviderAccountDTO postProcessAccountMaskedIdentification(final ProviderAccountDTO account) {
        var resultingAccount = account;
        String ccNumber = account.getAccountMaskedIdentification();
        if (CreditCardValidator.isValidUnmaskedCreditCardNumber(ccNumber)) {
            String maskedCcNumber = maskCreditCardNumber(ccNumber);
            resultingAccount = toInstanceWithMaskedAccountIdentification(account, maskedCcNumber);
        }

        return resultingAccount;
    }

    private ProviderAccountDTO postProcessAccountReferences(final ProviderAccountDTO account) {
        var resultingAccount = account;
        AccountReferenceDTO panAccountReference = Optional.of(account)
                .map(ProviderAccountDTO::getExtendedAccount)
                .map(ExtendedAccountDTO::getAccountReferences)
                .stream()
                .flatMap(Collection::stream)
                .filter(accountRef -> AccountReferenceType.PAN.equals(accountRef.getType()))
                .findFirst()
                .orElse(null);

        if (panAccountReference != null && CreditCardValidator.isValidUnmaskedCreditCardNumber(panAccountReference.getValue())) {
            String maskedCcNumber = maskCreditCardNumber(panAccountReference.getValue());

            AccountReferenceDTO maskedAccountReference = panAccountReference.toBuilder()
                    .value(maskedCcNumber)
                    .build();

            List<AccountReferenceDTO> filteredAccountReferences = account.getExtendedAccount()
                    .getAccountReferences()
                    .stream()
                    .filter(ar -> !AccountReferenceType.PAN.equals(ar.getType()) && !panAccountReference.getValue().equals(ar.getValue()))
                    .collect(Collectors.toList());
            filteredAccountReferences.add(maskedAccountReference);
            resultingAccount = toInstanceWithMaskedAccountReferences(resultingAccount, filteredAccountReferences);
        }

        return resultingAccount;
    }


    private ProviderAccountDTO toInstanceWithMaskedAccountIdentification(final ProviderAccountDTO account, final String maskedCcNumber) {
        return account.toBuilder()
                .accountMaskedIdentification(maskedCcNumber)
                .build();
    }

    private ProviderAccountDTO toInstanceWithMaskedAccountReferences(final ProviderAccountDTO account,
                                                                     final List<AccountReferenceDTO> accountReferences) {
        return account.toBuilder()
                .extendedAccount(account.getExtendedAccount()
                        .toBuilder()
                        .accountReferences(accountReferences)
                        .build())
                .build();
    }

    private String maskCreditCardNumber(final String ccNumber) {
        if (ccNumber == null || ccNumber.length() < NUMBER_OF_UNMASKED_CHARS) {
            return ccNumber;
        }
        var suffix = ccNumber.substring(ccNumber.length() - NUMBER_OF_UNMASKED_CHARS);
        var prefix = MASKING_SYMBOL.repeat(ccNumber.length() - NUMBER_OF_UNMASKED_CHARS);

        return prefix + suffix;
    }
}
