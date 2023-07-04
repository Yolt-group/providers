package com.yolt.providers.web.service.consenttesting;

import com.yolt.providers.common.providerinterface.Provider;
import com.yolt.providers.common.rest.YoltProxySelectorBuilder;
import com.yolt.providers.web.cryptography.trust.TrustManagerSupplier;
import com.yolt.providers.web.service.consenttesting.consenturlretriever.ConsentUrlRetriever;
import com.yolt.providers.web.service.consenttesting.consenturlretriever.ConsentUrlRetrieverFactory;
import com.yolt.providers.web.service.domain.ConsentTestingMessage;
import com.yolt.providers.web.service.domain.ConsentTestingResult;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.providerdomain.ServiceType;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import org.jose4j.jwt.JwtClaims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.X509TrustManager;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProviderConsentTesterTest {

    private static final UUID SOME_CLIENT_ID = UUID.randomUUID();
    private static final UUID SOME_CLIENT_REDIRECT_ID = UUID.randomUUID();
    private static final String SOME_REDIRECT_URL = "http://redirecturl.com";
    private static final ClientToken SOME_CLIENT_TOKEN = new ClientToken("some_serialized_token", new JwtClaims());
    private static final String SOME_PROVIDER_NAME = "SOME_PROVIDER";
    private static final String SOME_LOGIN_PAGE_URL = "LOGIN_PAGE_URL";

    @Mock
    private ConsentUrlRetrieverFactory urlRetrieverFactory;
    @Mock
    private ConsentTestingResultProcessor consentTestingResultProcessor;
    @Mock
    private ConsentPageValidator consentPageValidator;
    @Mock
    private Provider provider;
    @Mock
    private YoltProxySelectorBuilder yoltProxySelectorBuilder;
    @Mock
    private TrustManagerSupplier trustManagerSupplier;

    @InjectMocks
    private ProviderConsentTester providerConsentTester;

    @Test
    void shouldProcessTestingResultsForCorrectlyGeneratedConsentPage() {
        //given
        AuthenticationMeansReference authenticationMeansReference = new AuthenticationMeansReference(SOME_CLIENT_ID, SOME_CLIENT_REDIRECT_ID);
        ConsentUrlRetriever consentUrlRetriever = mock(ConsentUrlRetriever.class);
        when(provider.getProviderIdentifier()).thenReturn(SOME_PROVIDER_NAME);
        when(provider.getServiceType()).thenReturn(ServiceType.AIS);
        when(urlRetrieverFactory.getConsentUrlRetriever(provider))
                .thenReturn(consentUrlRetriever);
        when(consentUrlRetriever.retrieveConsentUrlForProvider(SOME_PROVIDER_NAME, authenticationMeansReference, SOME_REDIRECT_URL, SOME_CLIENT_TOKEN))
                .thenReturn(SOME_LOGIN_PAGE_URL);
        when(consentPageValidator.retrieveAndValidateConsentPage(eq(SOME_LOGIN_PAGE_URL), any(RestTemplate.class), eq(provider)))
                .thenReturn(ConsentTestingMessage.GENERATED);
        when(trustManagerSupplier.getTrustManager()).thenReturn(mock(X509TrustManager.class));

        //when
        providerConsentTester.testProviderConsent(
                provider,
                SOME_CLIENT_TOKEN,
                authenticationMeansReference,
                SOME_REDIRECT_URL);

        //then
        verify(urlRetrieverFactory).getConsentUrlRetriever(provider);
        verify(yoltProxySelectorBuilder, atLeastOnce()).build(SOME_PROVIDER_NAME);
        verify(consentUrlRetriever).retrieveConsentUrlForProvider(SOME_PROVIDER_NAME, authenticationMeansReference, SOME_REDIRECT_URL, SOME_CLIENT_TOKEN);
        verify(consentPageValidator, atLeastOnce()).retrieveAndValidateConsentPage(eq(SOME_LOGIN_PAGE_URL), any(RestTemplate.class), eq(provider));
        verify(consentTestingResultProcessor).processConsentTestingResult(provider, new ConsentTestingResult(ConsentTestingMessage.GENERATED, SOME_LOGIN_PAGE_URL), authenticationMeansReference);
    }

    @Test
    void shouldProcessTestingResultsForNotGeneratedConsentPage() {
        //given
        AuthenticationMeansReference authenticationMeansReference = new AuthenticationMeansReference(SOME_CLIENT_ID, SOME_CLIENT_REDIRECT_ID);
        ConsentUrlRetriever consentUrlRetriever = mock(ConsentUrlRetriever.class);
        when(provider.getProviderIdentifier()).thenReturn(SOME_PROVIDER_NAME);
        when(provider.getServiceType()).thenReturn(ServiceType.AIS);
        when(urlRetrieverFactory.getConsentUrlRetriever(provider))
                .thenReturn(consentUrlRetriever);
        when(consentUrlRetriever.retrieveConsentUrlForProvider(SOME_PROVIDER_NAME, authenticationMeansReference, SOME_REDIRECT_URL, SOME_CLIENT_TOKEN))
                .thenThrow(new RuntimeException());

        //when
        providerConsentTester.testProviderConsent(
                provider,
                SOME_CLIENT_TOKEN,
                authenticationMeansReference,
                SOME_REDIRECT_URL);

        //then
        verify(consentTestingResultProcessor).processConsentTestingResult(provider, new ConsentTestingResult(ConsentTestingMessage.NOT_GENERATED, null), authenticationMeansReference);
    }
}