package com.yolt.providers.web.service.consenttesting;

import com.yolt.providers.common.exception.MessageSuppressingException;
import com.yolt.providers.common.providerinterface.Provider;
import com.yolt.providers.common.rest.YoltProxySelectorBuilder;
import com.yolt.providers.web.cryptography.transport.CipherSuite;
import com.yolt.providers.web.cryptography.trust.TrustManagerSupplier;
import com.yolt.providers.web.intercept.ConsentTestingPublishingInterceptor;
import com.yolt.providers.web.intercept.RawDataProducer;
import com.yolt.providers.web.service.ProviderService;
import com.yolt.providers.web.service.consenttesting.consenturlretriever.ConsentUrlRetriever;
import com.yolt.providers.web.service.consenttesting.consenturlretriever.ConsentUrlRetrieverFactory;
import com.yolt.providers.web.service.domain.ConsentTestingMessage;
import com.yolt.providers.web.service.domain.ConsentTestingResult;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.providerdomain.ServiceType;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;
import org.slf4j.MDC;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;

import static com.yolt.providers.web.configuration.ApplicationConfiguration.ASYNC_PROVIDER_CONSENT_TESTER_EXECUTOR;
import static nl.ing.lovebird.logging.MDCContextCreator.CLIENT_ID_HEADER_NAME;

@Slf4j
@Service
@RequiredArgsConstructor
class ProviderConsentTester {

    private final ConsentUrlRetrieverFactory consentUrlRetrieverFactory;
    private final ConsentPageValidator consentPageValidator;
    private final ConsentTestingResultProcessor consentTestingResultProcessor;
    private final YoltProxySelectorBuilder yoltProxySelectorBuilder;
    private final RawDataProducer rawDataProducer;
    private final TrustManagerSupplier trustManagerSupplier;

    @Async(ASYNC_PROVIDER_CONSENT_TESTER_EXECUTOR)
    public void testProviderConsent(Provider registeredProviderBean, ClientToken clientToken, AuthenticationMeansReference authMeansReference, String baseRedirectUrl) {
        MDC.put(ProviderService.PROVIDER_MDC_KEY, String.valueOf(registeredProviderBean.getProviderIdentifier()));
        MDC.put("consent_testing", String.valueOf(true));
        MDC.put(CLIENT_ID_HEADER_NAME, String.valueOf(authMeansReference.getClientId()));
        String providerIdentifier = registeredProviderBean.getProviderIdentifier();
        ServiceType serviceType = registeredProviderBean.getServiceType();

        log.info("CONSENT TESTING - Invoked consent testing for {} {}", providerIdentifier, serviceType);
        ConsentTestingResult consentTestingResult = getConsentTestingResult(registeredProviderBean, clientToken, authMeansReference, baseRedirectUrl, providerIdentifier);
        consentTestingResultProcessor.processConsentTestingResult(registeredProviderBean, consentTestingResult, authMeansReference);
    }

    private ConsentTestingResult getConsentTestingResult(Provider registeredProviderBean,
                                                         ClientToken clientToken,
                                                         AuthenticationMeansReference authenticationMeansReference,
                                                         String baseRedirectUrl,
                                                         String providerIdentifier) {
        ConsentUrlRetriever consentUrlRetriever = consentUrlRetrieverFactory.getConsentUrlRetriever(registeredProviderBean);
        String consentUrl;
        try {
            consentUrl = consentUrlRetriever.retrieveConsentUrlForProvider(registeredProviderBean.getProviderIdentifier(), authenticationMeansReference, baseRedirectUrl, clientToken);
        } catch (Exception e) {
            log.info("Exception occurred during consent page retrieval", new MessageSuppressingException(e));
            return new ConsentTestingResult(ConsentTestingMessage.NOT_GENERATED, null);
        }
        ConsentTestingMessage consentTestingMessage = consentPageValidator.retrieveAndValidateConsentPage(consentUrl, getRestTemplate(providerIdentifier, CookieSpecs.STANDARD, clientToken), registeredProviderBean);
        if (consentTestingMessage == ConsentTestingMessage.GENERATED) {
            consentTestingMessage = consentPageValidator.retrieveAndValidateConsentPage(consentUrl, getRestTemplate(providerIdentifier, CookieSpecs.DEFAULT, clientToken), registeredProviderBean);
        }
        return new ConsentTestingResult(consentTestingMessage, consentUrl);
    }

    @SneakyThrows
    private RestTemplate getRestTemplate(String providerIdentifier, String cookieSpecs, ClientToken clientToken) {
        var sslContext = SSLContext.getInstance("TLSv1.3");
        var trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init((KeyStore) null); // this instantiates with system keystore
        var systemDefaultTrustManager = (X509TrustManager) trustManagerFactory.getTrustManagers()[0];
        sslContext.init(null, new TrustManager[]{new CompositeX509TrustManager(systemDefaultTrustManager, trustManagerSupplier.getTrustManager())}, null);
        var socketFactory = new SSLConnectionSocketFactory(
                sslContext,
                new String[]{"TLSv1.2", "TLSv1.3"},
                CipherSuite.getYoltSupportedCipherSuites(),
                new DefaultHostnameVerifier()
        );
        var httpClient = HttpClientBuilder
                .create()
                .setSSLSocketFactory(socketFactory)
                .setRoutePlanner(new SystemDefaultRoutePlanner(yoltProxySelectorBuilder.build(providerIdentifier)))
                .addInterceptorLast(new ConsentTestingPublishingInterceptor(providerIdentifier, rawDataProducer, clientToken))
                .setDefaultRequestConfig(RequestConfig.custom()
                        // The DEFAULT cookie specification doesn't understand latest RFC-compliant headers and causes logging lots of warnings
                        .setCookieSpec(cookieSpecs)
                        .build())
                .build();
        var factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        return new RestTemplate(factory);
    }
}
