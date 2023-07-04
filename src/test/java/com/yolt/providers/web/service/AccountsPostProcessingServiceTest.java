package com.yolt.providers.web.service;

import com.yolt.providers.common.ais.DataProviderResponse;
import nl.ing.lovebird.providerdomain.ProviderAccountNumberDTO;
import nl.ing.lovebird.providershared.ProviderServiceResponseStatus;
import nl.ing.lovebird.providershared.callback.UserDataCallbackResponse;
import nl.ing.lovebird.providershared.callback.UserSiteData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class AccountsPostProcessingServiceTest {

    private AccountsPostProcessingService sut;

    @BeforeEach
    public void beforeEach() {
        sut = new AccountsPostProcessingService(new CreditCardAccountPostProcessor());
    }

    @Test
    public void shouldReturnUnchangedDataProviderResponseForResponseThatIsNotSuitableForPostProcessing() {
        // given
        var expectedAccounts = List.of(
                ProviderAccountDTOMother.newValidCurrentAccountDTOBuilder().build()
        );

        var responseWithoutCreditCardAccount = new DataProviderResponse(expectedAccounts);

        // when
        var result = sut.postProcessDataProviderResponse(responseWithoutCreditCardAccount);

        // then
        assertThat(result).usingRecursiveComparison()
                .isEqualTo(responseWithoutCreditCardAccount);
    }

    @Test
    public void shouldReturnUnchangedCallbackResponseForResponseThatIsNotSuitableForPostProcessing() {
        // given
        var expectedAccounts = List.of(
                ProviderAccountDTOMother.newValidCurrentAccountDTOBuilder().build()
        );
        var expectedUserSiteData = new UserSiteData("", expectedAccounts, Collections.emptyList(), ProviderServiceResponseStatus.FINISHED);

        var responseWithoutCreditCardAccount = new UserDataCallbackResponse("", "", expectedUserSiteData);

        // when
        var result = sut.postProcessUserDataCallbackResponse(responseWithoutCreditCardAccount);

        // then
        assertThat(result).usingRecursiveComparison()
                .isEqualTo(responseWithoutCreditCardAccount);
    }
}