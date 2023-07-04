package com.yolt.providers.web.service.consenttesting.consenturlretriever;

import com.yolt.providers.common.providerinterface.SepaPaymentProvider;
import com.yolt.providers.common.providerinterface.UrlDataProvider;
import com.yolt.providers.web.service.ProviderService;
import com.yolt.providers.web.service.ProviderUkDomesticPaymentService;
import com.yolt.providers.web.service.consenttesting.consenturlretriever.ais.UrlDataProviderConsentUrlRetriever;
import com.yolt.providers.web.service.consenttesting.consenturlretriever.pis.UkDomesticPaymentProviderConsentUrlRetriever;
import com.yolt.providers.web.sitedetails.sites.SiteDetailsService;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsentUrlRetrieverFactoryTest {

    private ConsentUrlRetrieverFactory consentUrlRetrieverFactory;

    private UrlDataProviderConsentUrlRetriever urlDataProviderConsentUrlRetriever;
    private UkDomesticPaymentProviderConsentUrlRetriever ukDomesticPaymentProviderConsentUrlRetriever;

    @BeforeEach
    void setup() {
        urlDataProviderConsentUrlRetriever = new UrlDataProviderConsentUrlRetriever(mock(ProviderService.class), mock(SiteDetailsService.class), "");
        ukDomesticPaymentProviderConsentUrlRetriever = new UkDomesticPaymentProviderConsentUrlRetriever(mock(ProviderUkDomesticPaymentService.class), mock(SiteDetailsService.class), "");
        consentUrlRetrieverFactory = new ConsentUrlRetrieverFactory(
                Arrays.asList(urlDataProviderConsentUrlRetriever, ukDomesticPaymentProviderConsentUrlRetriever));
    }

    @Test
    void shouldReturnProperConsentUrlRetrieverImplementationForProviderWithSupportedType() {
        // when
        ConsentUrlRetriever result = consentUrlRetrieverFactory.getConsentUrlRetriever(mock(UrlDataProvider.class));

        // then
        assertThat(result).isEqualTo(urlDataProviderConsentUrlRetriever);
    }

    @Test
    void shouldThrowIllegalStateExceptionWhenNoImplementationFoundSupportingGivenProvider() {
        // given
        SepaPaymentProvider sepaPaymentProvider = mock(SepaPaymentProvider.class);
        when(sepaPaymentProvider.getProviderIdentifier())
                .thenReturn("SOME_SEPA_PAYMENT_PROVIDER");

        // when
        ThrowableAssert.ThrowingCallable getConsentUrlRetrieverCallable = () -> consentUrlRetrieverFactory.getConsentUrlRetriever(sepaPaymentProvider);

        // then
        assertThatThrownBy(getConsentUrlRetrieverCallable)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("CONSENT TESTING - Could not find consent url retriever supporting provider SOME_SEPA_PAYMENT_PROVIDER");
    }
}
