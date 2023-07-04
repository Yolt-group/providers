package com.yolt.providers.web.service;

import nl.ing.lovebird.extendeddata.account.ExtendedAccountDTO;
import nl.ing.lovebird.extendeddata.common.AccountReferenceDTO;
import nl.ing.lovebird.extendeddata.transaction.AccountReferenceType;
import nl.ing.lovebird.providerdomain.AccountType;
import nl.ing.lovebird.providerdomain.ProviderAccountDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CreditCardAccountPostProcessorTest {

    private static final int NUMBER_OF_UNMASKED_CHARS = 4;
    private static final String MASKING_SYMBOL = "X";

    private CreditCardAccountPostProcessor sut;

    @BeforeEach
    public void beforeEach() {
        sut = new CreditCardAccountPostProcessor();
    }

    @Test
    public void shouldLeaveTheInputIntactIfAccountTypeIsNotCreditCard() {
        // given
        var ineligibleAccount = ProviderAccountDTOMother.newValidUnmaskedCreditCardAccountDTOBuilder()
                .yoltAccountType(AccountType.CURRENT_ACCOUNT)
                .build();

        // when
        var result = sut.postProcessAccount(ineligibleAccount);

        // then
        assertThat(result).isEqualToComparingFieldByField(ineligibleAccount);
    }

    @Test
    public void shouldLeaveTheInputIntactIfCcNumberIsMaskedPostProcessing() {
        // given
        var ineligibleAccount = ProviderAccountDTOMother.newValidMaskedCreditCardAccountDTOBuilder()
                .build();

        // when
        var result = sut.postProcessAccount(ineligibleAccount);

        // then
        assertThat(result).isEqualToComparingFieldByField(ineligibleAccount);
    }

    @Test
    public void shouldMaskCreditCardNumberInAccountReferencesIfNumberIsValid() {
        // given
        var eligibleAccount = ProviderAccountDTOMother.newValidUnmaskedCreditCardAccountDTOBuilder()
                .build();

        var maskedIdentification = eligibleAccount.getAccountMaskedIdentification();
        var ccNumber = eligibleAccount.getExtendedAccount()
                .getAccountReferences()
                .stream()
                .filter(ar -> AccountReferenceType.PAN.equals(ar.getType()))
                .map(AccountReferenceDTO::getValue)
                .findFirst()
                .orElse(null);

        // when
        var result = sut.postProcessAccount(eligibleAccount);

        // then
        assertThat(result).usingRecursiveComparison()
                .ignoringFields("accountMaskedIdentification", "extendedAccount")
                .withStrictTypeChecking()
                .isEqualTo(eligibleAccount);

        assertThat(result.getExtendedAccount()).usingRecursiveComparison()
                .ignoringFields("accountReferences")
                .withStrictTypeChecking()
                .isEqualTo(eligibleAccount.getExtendedAccount());

        assertThat(result.getExtendedAccount().getAccountReferences())
                .size()
                .isEqualTo(eligibleAccount.getExtendedAccount().getAccountReferences().size());

        assertThat(result.getAccountMaskedIdentification()).endsWith(maskedIdentification.substring(maskedIdentification.length() - NUMBER_OF_UNMASKED_CHARS))
                .startsWith(MASKING_SYMBOL.repeat(maskedIdentification.length() - NUMBER_OF_UNMASKED_CHARS));

        assertThat(result).extracting(ProviderAccountDTO::getExtendedAccount)
                .extracting(ExtendedAccountDTO::getAccountReferences)
                .extracting(ars -> ars.stream()
                        .filter(ar -> AccountReferenceType.PAN.equals(ar.getType()))
                        .findFirst()
                        .map(AccountReferenceDTO::getValue)
                        .orElse(null))
                .asString()
                .endsWith(ccNumber.substring(ccNumber.length() - NUMBER_OF_UNMASKED_CHARS))
                .startsWith(MASKING_SYMBOL.repeat(ccNumber.length() - NUMBER_OF_UNMASKED_CHARS));
    }
}