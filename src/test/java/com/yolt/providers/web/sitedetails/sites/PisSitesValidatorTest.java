package com.yolt.providers.web.sitedetails.sites;

import com.yolt.providers.common.providerdetail.dto.PisSiteDetails;
import com.yolt.providers.web.sitedetails.exceptions.SiteInvalidException;
import org.assertj.core.api.AssertionsForClassTypes;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.yolt.providers.web.sitedetails.sites.PisSiteDetailsFixture.*;

class PisSitesValidatorTest {
    private final PisSitesValidator pisSitesValidator = new PisSitesValidator();

    @Test
    public void shouldThrowExceptionWhenSameSiteIdsHaveSamePaymentTypeAndPaymentMethod() {
        //given
        UUID uuid = UUID.randomUUID();
        PisSiteDetails pisSiteDetails0 = getPisSiteDetails(uuid);
        PisSiteDetails pisSiteDetails1 = getPisSiteDetails(uuid);
        //when
        ThrowableAssert.ThrowingCallable validatePisSites = () ->
                pisSitesValidator.validatePisSites(List.of(pisSiteDetails0, pisSiteDetails1));
        //then
        AssertionsForClassTypes.assertThatThrownBy(validatePisSites)
                .isExactlyInstanceOf(SiteInvalidException.class)
                .hasMessage("PisSiteDetails sites should have unique <id, paymentMethod, paymentType> tuple :\n" +
                        "ID: " + uuid + " ProviderKey: ProviderKey\n" +
                        "ID: " + uuid + " ProviderKey: ProviderKey");
    }

    @Test
    public void shouldThrowExceptionWhenSameSiteHasDifferentProviderName() {
        //given
        UUID uuid = UUID.randomUUID();
        PisSiteDetails pisSiteDetails0 = getPisSiteDetailsSepa(uuid, "ProviderKey");
        PisSiteDetails pisSiteDetails1 = getPisSiteDetailsUKDomestic(uuid, "AnotherProviderKeyValue");
        //when
        ThrowableAssert.ThrowingCallable validatePisSites = () ->
                pisSitesValidator.validatePisSites(List.of(pisSiteDetails0, pisSiteDetails1));
        //then
        AssertionsForClassTypes.assertThatThrownBy(validatePisSites)
                .isExactlyInstanceOf(SiteInvalidException.class)
                .hasMessage("PisSiteDetails not all sites have the same name:\n" +
                        "ID: " + uuid + " ProviderKey: ProviderKey\n" +
                        "ID: " + uuid + " ProviderKey: AnotherProviderKeyValue");
    }
}