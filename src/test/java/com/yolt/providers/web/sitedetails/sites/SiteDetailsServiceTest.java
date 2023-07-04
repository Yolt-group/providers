package com.yolt.providers.web.sitedetails.sites;

import com.yolt.providers.common.pis.common.PaymentType;
import com.yolt.providers.common.providerdetail.AisDetailsProvider;
import com.yolt.providers.common.providerdetail.PisDetailsProvider;
import com.yolt.providers.common.providerdetail.dto.*;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.yolt.providers.common.providerdetail.dto.ProviderBehaviour.STATE;
import static com.yolt.providers.common.providerdetail.dto.ProviderType.DIRECT_CONNECTION;
import static nl.ing.lovebird.providerdomain.AccountType.CURRENT_ACCOUNT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
public class SiteDetailsServiceTest {

    private SiteDetailsService subject;

    @Mock
    private AisSitesValidator aisSitesValidator;

    @Mock
    private PisSitesValidator pisSitesValidator;

    @Mock
    private CrossSiteValidator crossSiteValidator;

    @BeforeEach
    public void setup() {

    }

    @Test
    public void shouldThrowExceptionWhenValidatorThrowsException() {
        //given
        IllegalArgumentException exceptionThrown = new IllegalArgumentException("message");
        doThrow(exceptionThrown).when(aisSitesValidator).validateAisSites(any());

        //when
        ThrowableAssert.ThrowingCallable constructorCallable = () -> new SiteDetailsService(
                List.of(new ProviderDetailsProviderWithOneSite(), new ProviderDetailsProviderWithTwoSites()),
                List.of(new PisProviderDetailsWithOneSite()),
                aisSitesValidator,
                pisSitesValidator,
                crossSiteValidator
        );

        //then
        assertThatThrownBy(constructorCallable)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("message");
    }

    @Test
    public void shouldValidateWithoutProblems() {
        //given
        doNothing().when(aisSitesValidator).validateAisSites(any());

        //when
        subject = new SiteDetailsService(
                List.of(new ProviderDetailsProviderWithOneSite(), new ProviderDetailsProviderWithTwoSites()),
                List.of(new PisProviderDetailsWithOneSite()),
                aisSitesValidator,
                pisSitesValidator,
                crossSiteValidator
        );

        //then
        List<AisSiteDetails> allSites = subject.getAisProviderSitesDataBySiteId().values().stream().flatMap(value -> value.getAisSiteDetails().stream()).collect(Collectors.toList());
        assertThat(subject.getAisSiteDetails()).hasSize(3);
        assertThat(subject.getPisSiteDetails()).hasSize(1);
        assertThat(subject.getAisProviderSitesDataBySiteId()).hasSize(2);
        assertThat(allSites).hasSize(3);
        assertThat(subject.getPisProviderSitesDataBySiteId()).hasSize(1);
    }

    @Test
    public void shouldProperlySplitScrapers() {
        //given
        doNothing().when(aisSitesValidator).validateAisSites(any());

        //when
        subject = new SiteDetailsService(
                List.of(new ProviderDetailsProviderWithOneSite(), new ScraperProviderDetailsProviderWithTwoSites()),
                List.of(new PisProviderDetailsWithOneSite()),
                aisSitesValidator,
                pisSitesValidator,
                crossSiteValidator
        );

        //then
        List<AisSiteDetails> allSites = subject.getAisProviderSitesDataBySiteId().values().stream().flatMap(value -> value.getAisSiteDetails().stream()).collect(Collectors.toList());
        assertThat(subject.getAisSiteDetails()).hasSize(3);
        assertThat(subject.getPisSiteDetails()).hasSize(1);
        assertThat(subject.getAisProviderSitesDataBySiteId()).hasSize(3);
        assertThat(allSites).hasSize(3);
        assertThat(subject.getPisProviderSitesDataBySiteId()).hasSize(1);
    }

    private AisSiteDetails exampleAisSiteWithProviderKey(String providerKey) {
        return AisSiteDetails.site(
                UUID.randomUUID().toString(),
                "name" + providerKey,
                providerKey,
                DIRECT_CONNECTION,
                List.of(STATE),
                List.of(CURRENT_ACCOUNT),
                List.of(CountryCode.PL)
        ).build();
    }

    private AisSiteDetails exampleAisSite(String sufix) {
        return AisSiteDetails.site(
                UUID.randomUUID().toString(),
                "name" + sufix,
                "providerKey" + sufix,
                DIRECT_CONNECTION,
                List.of(STATE),
                List.of(CURRENT_ACCOUNT),
                List.of(CountryCode.PL)
        ).build();
    }

    private PisSiteDetails examplePisSite(String suffix) {
        return PisSiteDetails.builder()
                .id(UUID.randomUUID())
                .providerKey("name" + suffix)
                .supported(true)
                .paymentType(PaymentType.SINGLE)
                .dynamicFields(Collections.emptyMap())
                .requiresSubmitStep(true)
                .paymentMethod(PaymentMethod.SEPA)
                .loginRequirements(List.of(LoginRequirement.REDIRECT))
                .build();
    }

    private class ProviderDetailsProviderWithOneSite implements AisDetailsProvider {

        @Override
        public List<AisSiteDetails> getAisSiteDetails() {
            return List.of(exampleAisSite("1"));
        }
    }

    private class ProviderDetailsProviderWithTwoSites implements AisDetailsProvider {

        @Override
        public List<AisSiteDetails> getAisSiteDetails() {
            return List.of(exampleAisSite("2"),
                    exampleAisSite("3"));
        }
    }

    private class ScraperProviderDetailsProviderWithTwoSites implements AisDetailsProvider {

        @Override
        public List<AisSiteDetails> getAisSiteDetails() {
            return List.of(exampleAisSiteWithProviderKey("BUDGET_INSIGHT"),
                    exampleAisSiteWithProviderKey("BUDGET_INSIGHT"));
        }
    }

    private class PisProviderDetailsWithOneSite implements PisDetailsProvider {

        @Override
        public List<PisSiteDetails> getPisSiteDetails() {
            return List.of(examplePisSite("1"));
        }
    }

}

