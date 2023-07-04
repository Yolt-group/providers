package com.yolt.providers.web.sitedetails.sites;

import com.yolt.providers.common.providerdetail.dto.*;
import com.yolt.providers.web.sitedetails.exceptions.SiteInvalidException;
import nl.ing.lovebird.providerdomain.AccountType;
import nl.ing.lovebird.providerdomain.ServiceType;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.yolt.providers.common.providerdetail.dto.AisSiteDetails.site;
import static com.yolt.providers.common.providerdetail.dto.ConsentBehavior.CONSENT_PER_ACCOUNT;
import static com.yolt.providers.common.providerdetail.dto.LoginRequirement.FORM;
import static com.yolt.providers.common.providerdetail.dto.LoginRequirement.REDIRECT;
import static com.yolt.providers.common.providerdetail.dto.ProviderType.DIRECT_CONNECTION;
import static java.util.List.of;
import static nl.ing.lovebird.providerdomain.ServiceType.AIS;
import static nl.ing.lovebird.providerdomain.ServiceType.PIS;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CrossSiteValidatorTest {
    CrossSiteValidator crossSiteValidator = new CrossSiteValidator();

    @Test
    public void shouldThrowExceptionWhenAisSiteMissesSomethingInLoginRequirements() {
        //given
        UUID uuid = UUID.randomUUID();
        AisSiteDetails aisSiteDetails = createExampleSite(uuid, REDIRECT)
                .loginRequirements(List.of()).build();
        //when
        ThrowableAssert.ThrowingCallable validateUsesStepTypes = () -> crossSiteValidator.validateUsesStepTypes(List.of(aisSiteDetails), List.of());
        //then
        assertThatThrownBy(validateUsesStepTypes)
                .isExactlyInstanceOf(SiteInvalidException.class)
                .hasMessage("aisSiteDetail loginRequirements does not match stepTypes: " + uuid);
    }

    @Test
    public void shouldThrowExceptionWhenAisSiteMissesSomethingInUsesLoginSteps() {
        //given
        UUID uuid = UUID.randomUUID();
        AisSiteDetails aisSiteDetails = createExampleSite(uuid, REDIRECT)
                .loginRequirements(List.of(FORM)).build();
        //when
        ThrowableAssert.ThrowingCallable validateUsesStepTypes = () -> crossSiteValidator.validateUsesStepTypes(List.of(aisSiteDetails), List.of());
        //then
        assertThatThrownBy(validateUsesStepTypes)
                .isExactlyInstanceOf(SiteInvalidException.class)
                .hasMessage("aisSiteDetail loginRequirements does not match stepTypes: " + uuid);
    }

    @Test
    public void shouldThrowExceptionWhenAisSiteMissesLoginSteps() {
        //given
        UUID uuid = UUID.randomUUID();
        AisSiteDetails aisSiteDetails = createExampleSite(uuid, REDIRECT)
                .loginRequirements(null).build();
        //when
        ThrowableAssert.ThrowingCallable validateUsesStepTypes = () -> crossSiteValidator.validateUsesStepTypes(List.of(aisSiteDetails), List.of());
        //then
        assertThatThrownBy(validateUsesStepTypes)
                .isExactlyInstanceOf(SiteInvalidException.class)
                .hasMessage("aisSiteDetail loginRequirements does not exist: " + uuid);
    }

    @Test
    public void shouldThrowExceptionWhenPisMismatchesAis() {
        //given
        UUID uuid = UUID.randomUUID();
        PisSiteDetails pisSiteDetails = PisSiteDetailsFixture.getPisSiteDetails(uuid, FORM);
        AisSiteDetails aisSiteDetails = createExampleSite(uuid, REDIRECT).build();
        //when
        ThrowableAssert.ThrowingCallable validateUsesStepTypes = () -> crossSiteValidator.validateUsesStepTypes(List.of(aisSiteDetails), List.of(pisSiteDetails));
        //then
        assertThatThrownBy(validateUsesStepTypes)
                .isExactlyInstanceOf(SiteInvalidException.class)
                .hasMessage("pisSiteDetail is missing entry in usesStepTypes in aisSiteDetails site ids: " + uuid);
    }

    @Test
    public void shouldThrowExceptionWhenPisMismatchesAisButUsesStepTypesContainsPis() {
        //given
        UUID uuid = UUID.randomUUID();
        PisSiteDetails pisSiteDetails = PisSiteDetailsFixture.getPisSiteDetails(uuid, FORM);
        AisSiteDetails aisSiteDetails = createExampleSite(uuid, REDIRECT)
                .usesStepTypes(Map.of(AIS, List.of(REDIRECT), PIS, List.of(REDIRECT))).build();
        //when
        ThrowableAssert.ThrowingCallable validateUsesStepTypes = () -> crossSiteValidator.validateUsesStepTypes(List.of(aisSiteDetails), List.of(pisSiteDetails));
        //then
        assertThatThrownBy(validateUsesStepTypes)
                .isExactlyInstanceOf(SiteInvalidException.class)
                .hasMessage("pisSiteDetail loginRequirements do not match stepTypes in aisDetails: " + uuid);
    }

    @Test
    public void shouldNotThrowException() {
        //given
        UUID uuid = UUID.randomUUID();
        PisSiteDetails pisSiteDetails = PisSiteDetailsFixture.getPisSiteDetails(uuid, FORM);
        AisSiteDetails aisSiteDetails = createExampleSite(uuid, REDIRECT)
                .usesStepTypes(Map.of(AIS, List.of(REDIRECT), PIS, List.of(FORM))).build();
        //when
        ThrowableAssert.ThrowingCallable validateUsesStepTypes = () -> crossSiteValidator.validateUsesStepTypes(List.of(aisSiteDetails), List.of(pisSiteDetails));
        //then
        assertThatCode(validateUsesStepTypes)
                .doesNotThrowAnyException();
    }

    private AisSiteDetails.AisSiteDetailsBuilder createExampleSite(UUID id, LoginRequirement loginRequirement) {
        return site(id.toString(),
                "name",
                "providerKey",
                DIRECT_CONNECTION,
                List.of(ProviderBehaviour.STATE),
                List.of(AccountType.CURRENT_ACCOUNT),
                List.of(CountryCode.PL))
                .consentExpiryInDays(90)
                .consentBehavior(Set.of(CONSENT_PER_ACCOUNT))
                .usesStepTypes(Map.of(ServiceType.AIS, List.of(loginRequirement)))
                .loginRequirements(of(loginRequirement))
                .groupingBy("groupingBy");
    }
}
