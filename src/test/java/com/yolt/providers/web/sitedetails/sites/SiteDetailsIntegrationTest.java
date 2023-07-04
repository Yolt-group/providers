package com.yolt.providers.web.sitedetails.sites;

import com.yolt.providers.common.providerdetail.AisDetailsProvider;
import com.yolt.providers.common.providerdetail.PisDetailsProvider;
import com.yolt.providers.common.providerdetail.dto.PaymentMethod;
import com.yolt.providers.common.providerdetail.dto.PisSiteDetails;
import com.yolt.providers.common.providerinterface.Provider;
import com.yolt.providers.common.providerinterface.SepaPaymentProvider;
import com.yolt.providers.common.providerinterface.UkDomesticPaymentProvider;
import com.yolt.providers.common.providerinterface.UrlDataProvider;
import com.yolt.providers.web.configuration.IntegrationTestContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTestContext
public class SiteDetailsIntegrationTest {

    @Autowired
    private List<AisDetailsProvider> aisDetailsProviders;

    @Autowired
    private List<PisDetailsProvider> pisDetailsProviders;

    @Autowired
    private List<UkDomesticPaymentProvider> ukPisProviders;

    @Autowired
    private List<SepaPaymentProvider> sepaPisProviders;

    @Autowired
    private List<UrlDataProvider> aisProviders;

    @Autowired
    private SiteDetailsService siteDetailsService;

    private Set<String> fakeTestProviderKeysToSkipp = Set.of("FAKE_TEST_PROVIDER", "TEST_IMPL_OPENBANKING_MOCK", "ING_NL_MOCK", "POLISH_API_MOCK");

    @Test
    public void shouldAllAisProvidersHaveSiteDetails() {
        var providersWithoutSiteDetails = aisProviders.stream()
                .map(Provider::getProviderIdentifier)
                .filter(providerIdentifier -> !fakeTestProviderKeysToSkipp.contains(providerIdentifier) &&
                        aisDetailsProviders.stream()
                                .filter(aisDetailsProvider -> aisDetailsProvider
                                        .getAisSiteDetails()
                                        .stream()
                                        .anyMatch(aisSiteDetails -> aisSiteDetails
                                                .getProviderKey()
                                                .equals(providerIdentifier))
                                )
                                .findAny()
                                .isEmpty())
                .collect(Collectors.toSet());
        assertThat(providersWithoutSiteDetails).isEmpty();
    }

    @Test
    public void shouldAllUkPisProvidersHaveSiteDetails() {
        var ukDetailProviders = pisDetailsProviders
                .stream()
                .map(PisDetailsProvider::getPisSiteDetails)
                .flatMap(Collection::stream)
                .filter((pisSiteDetails -> pisSiteDetails.getPaymentMethod().equals(PaymentMethod.UKDOMESTIC)))
                .map(PisSiteDetails::getProviderKey)
                .collect(Collectors.toSet());

        var providersWithoutSiteDetails = ukPisProviders.stream()
                .filter(providerIdentifier -> !fakeTestProviderKeysToSkipp.contains(providerIdentifier.getProviderIdentifier()) &&
                        !ukDetailProviders.contains(providerIdentifier.getProviderIdentifier()))
                .map(UkDomesticPaymentProvider::getProviderIdentifier)
                .collect(Collectors.toSet());
        assertThat(providersWithoutSiteDetails).isEmpty();
    }

    @Test
    public void shouldAllSepaPisProvidersHaveSiteDetails() {
        var sepaDetailProviders = pisDetailsProviders
                .stream()
                .map(PisDetailsProvider::getPisSiteDetails)
                .flatMap(Collection::stream)
                .filter((pisSiteDetails -> pisSiteDetails.getPaymentMethod().equals(PaymentMethod.SEPA)))
                .map(PisSiteDetails::getProviderKey)
                .collect(Collectors.toSet());

        var providersWithoutSiteDetails = sepaPisProviders.stream()
                .filter(providerIdentifier -> !fakeTestProviderKeysToSkipp.contains(providerIdentifier.getProviderIdentifier()) &&
                        !sepaDetailProviders.contains(providerIdentifier.getProviderIdentifier()))
                .map(SepaPaymentProvider::getProviderIdentifier)
                .collect(Collectors.toSet());
        assertThat(providersWithoutSiteDetails).isEmpty();
    }

    @Test
    public void shouldAllAisProviderSiteDataHaveNotNullSiteDetails() {
        var sitesWithNulls = siteDetailsService.getAisProviderSitesDataBySiteId().values().stream()
                .filter(Objects::isNull)
                .collect(Collectors.toList());
        assertThat(sitesWithNulls).isEmpty();
    }

    @Test
    public void shouldAllPisProviderSiteDataHaveNotNullSiteDetails() {
        var sitesWithNulls = siteDetailsService.getPisProviderSitesDataBySiteId().values().stream()
                .filter(Objects::isNull)
                .collect(Collectors.toList());
        assertThat(sitesWithNulls).isEmpty();
    }
}
