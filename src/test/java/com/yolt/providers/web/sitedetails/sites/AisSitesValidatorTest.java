package com.yolt.providers.web.sitedetails.sites;

import com.yolt.providers.common.providerdetail.dto.AisSiteDetails;
import com.yolt.providers.common.providerdetail.dto.CountryCode;
import com.yolt.providers.common.providerdetail.dto.LoginRequirement;
import com.yolt.providers.common.providerdetail.dto.ProviderBehaviour;
import com.yolt.providers.web.sitedetails.exceptions.SiteInvalidException;
import nl.ing.lovebird.providerdomain.AccountType;
import nl.ing.lovebird.providerdomain.ServiceType;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.yolt.providers.common.providerdetail.dto.AisSiteDetails.site;
import static com.yolt.providers.common.providerdetail.dto.ConsentBehavior.CONSENT_PER_ACCOUNT;
import static com.yolt.providers.common.providerdetail.dto.ProviderType.DIRECT_CONNECTION;
import static com.yolt.providers.common.providerdetail.dto.ProviderType.SCRAPING;
import static java.util.List.of;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AisSitesValidatorTest {

    private static final UUID UUID_1 = UUID.randomUUID();
    private static final UUID UUID_2 = UUID.randomUUID();
    private static final UUID UUID_3 = UUID.randomUUID();
    private static final UUID UUID_4 = UUID.randomUUID();
    private static final UUID UUID_5 = UUID.randomUUID();
    private static final UUID UUID_6 = UUID.randomUUID();

    private static final String PROVIDER_KEY_1 = "provider_key_1";
    private static final String PROVIDER_KEY_2 = "provider_key_2";
    private static final String PROVIDER_KEY_3 = "provider_key_3";
    private static final String PROVIDER_KEY_4 = "provider_key_4";
    private static final String PROVIDER_KEY_5 = "provider_key_5";

    private AisSitesValidator aisSitesValidator;

    @BeforeEach
    public void setup() {
        aisSitesValidator = new AisSitesValidator();
    }

    @Test
    public void shouldThrowExceptionWhenValidatingAndIdsAreNotUnique() {
        //given
        List<AisSiteDetails> sites = List.of(
                createExampleSite(UUID_1, PROVIDER_KEY_1).build(),
                createExampleSite(UUID_1, PROVIDER_KEY_2).build(),
                createExampleSite(UUID_2, PROVIDER_KEY_5).build()
        );

        //when
        ThrowableAssert.ThrowingCallable validateCallable = () -> aisSitesValidator.validateAisSites(sites);

        //then
        assertThatThrownBy(validateCallable)
                .isInstanceOf(SiteInvalidException.class)
                .hasMessage(String.format("""
                                Duplicated id in site(s):
                                ID: %s ProviderKey: provider_key_1
                                ID: %s ProviderKey: provider_key_2""",
                        UUID_1, UUID_1));
    }

    @Test
    public void shouldThrowExceptionWhenValidatingAndProviderKeysAreNotUnique() {
        //given
        List<AisSiteDetails> sites = List.of(
                createExampleSite(UUID_1, PROVIDER_KEY_1).providerType(DIRECT_CONNECTION).build(),
                createExampleSite(UUID_2, PROVIDER_KEY_2).providerType(DIRECT_CONNECTION).build(),
                createExampleSite(UUID_4, PROVIDER_KEY_2).providerType(DIRECT_CONNECTION).build()
        );

        //when
        ThrowableAssert.ThrowingCallable validateCallable = () -> aisSitesValidator.validateAisSites(sites);

        //then
        assertThatThrownBy(validateCallable)
                .isInstanceOf(SiteInvalidException.class)
                .hasMessage(String.format("""
                                Duplicated provider key in site(s):
                                ID: %s ProviderKey: provider_key_2
                                ID: %s ProviderKey: provider_key_2""",
                        UUID_2, UUID_4));
    }

    @Test
    public void shouldThrowExceptionWhenValidatingAndScrapingProviderDoesNotHaveExternalId() {
        //given
        List<AisSiteDetails> sites = List.of(
                createExampleSite(UUID_1, PROVIDER_KEY_1).providerType(SCRAPING).externalId(null).build(),
                createExampleSite(UUID_2, PROVIDER_KEY_2).providerType(SCRAPING).externalId("externalId").build()
        );

        //when
        ThrowableAssert.ThrowingCallable validateCallable = () -> aisSitesValidator.validateAisSites(sites);

        //then
        assertThatThrownBy(validateCallable)
                .isInstanceOf(SiteInvalidException.class)
                .hasMessage("Scrapers without externalIds: " + UUID_1);
    }

    @Test
    public void shouldThrowExceptionWhenValidatingAndNonScrapingProviderHasExternalId() {
        //given
        List<AisSiteDetails> sites = List.of(
                createExampleSite(UUID_1, PROVIDER_KEY_1).providerType(DIRECT_CONNECTION).externalId("externalId").build(),
                createExampleSite(UUID_2, PROVIDER_KEY_2).providerType(DIRECT_CONNECTION).externalId(null).build()
        );

        //when
        ThrowableAssert.ThrowingCallable validateCallable = () -> aisSitesValidator.validateAisSites(sites);

        //then
        assertThatThrownBy(validateCallable)
                .isInstanceOf(SiteInvalidException.class)
                .hasMessage("Non scrapers with externalIds: " + UUID_1);
    }

    @Test
    public void shouldThrowExceptionWhenValidatingAndSiteDoesNotHaveConsentBehavior() {
        //given
        List<AisSiteDetails> sites = List.of(
                createExampleSite(UUID_1, PROVIDER_KEY_1).consentBehavior(null).build(),
                createExampleSite(UUID_2, PROVIDER_KEY_2).consentBehavior(Collections.emptySet()).build(),
                createExampleSite(UUID_3, PROVIDER_KEY_3).consentBehavior(Set.of(CONSENT_PER_ACCOUNT)).build()
        );

        //when
        ThrowableAssert.ThrowingCallable validateCallable = () -> aisSitesValidator.validateAisSites(sites);

        //then
        assertThatThrownBy(validateCallable)
                .isInstanceOf(SiteInvalidException.class)
                .hasMessage("Sites without consent behavior: " + UUID_1);
    }

    @Test
    public void shouldThrowExceptionWhenValidatingAndManyUrlProvidersHaveTheSameGroupingBy() {
        //given
        List<AisSiteDetails> sites = List.of(
                createExampleSite(UUID_1, PROVIDER_KEY_1).groupingBy("groupingBy1").build(),
                createExampleSite(UUID_2, PROVIDER_KEY_2).groupingBy("groupingBy1").build(),
                createExampleSite(UUID_3, PROVIDER_KEY_3).groupingBy("groupingBy2").build(),
                createExampleSite(UUID_4, PROVIDER_KEY_4).groupingBy("groupingBy3").build(),
                createExampleSite(UUID_5, PROVIDER_KEY_5).groupingBy("groupingBy2").build(),
                createExampleSite(UUID_6, "AMEX_EU").groupingBy("groupingBy2").build() // one of exceptions ensure it will not be listed as error
        );

        //when
        ThrowableAssert.ThrowingCallable validateCallable = () -> aisSitesValidator.validateAisSites(sites);

        //then
        assertThatThrownBy(validateCallable)
                .isInstanceOf(SiteInvalidException.class)
                .hasMessage(String.format("""
                                Duplicated grouping by in site(s):
                                ID: %s ProviderKey: provider_key_1
                                ID: %s ProviderKey: provider_key_2
                                ID: %s ProviderKey: provider_key_3
                                ID: %s ProviderKey: provider_key_5""",
                        UUID_1, UUID_2, UUID_3, UUID_5));
    }

    @Test
    public void shouldValidateWithoutErrorsWhenWhenGroupingByNullValuesExist() {
        //given
        List<AisSiteDetails> sites = List.of(
                createExampleSite(UUID_1, PROVIDER_KEY_1).groupingBy("groupingBy1").build(),
                createExampleSite(UUID_2, PROVIDER_KEY_2).groupingBy(null).build(),
                createExampleSite(UUID_3, PROVIDER_KEY_3).groupingBy(null).build()
        );

        //when
        ThrowableAssert.ThrowingCallable validateCallable = () -> aisSitesValidator.validateAisSites(sites);

        //then
        assertThatCode(validateCallable).doesNotThrowAnyException();
    }

    private AisSiteDetails.AisSiteDetailsBuilder createExampleSite(UUID id, String providerKey) {
        return site(id.toString(),
                "name",
                providerKey,
                DIRECT_CONNECTION,
                List.of(ProviderBehaviour.STATE),
                List.of(AccountType.CURRENT_ACCOUNT),
                List.of(CountryCode.PL))
                .consentExpiryInDays(90)
                .consentBehavior(Set.of(CONSENT_PER_ACCOUNT))
                .usesStepTypes(Map.of(ServiceType.AIS, List.of(LoginRequirement.REDIRECT)))
                .loginRequirements(of(LoginRequirement.REDIRECT))
                .groupingBy("groupingBy");
    }
}