package com.yolt.providers.web.cryptography.transport;

import com.yolt.providers.common.cryptography.RestTemplateManager;
import com.yolt.providers.common.domain.RestTemplateManagerConfiguration;
import com.yolt.providers.common.rest.ExternalRestTemplateBuilderFactory;
import com.yolt.providers.common.rest.YoltProxySelectorBuilder;
import com.yolt.providers.web.cryptography.KeyNotFoundException;
import com.yolt.providers.web.cryptography.KeyService;
import com.yolt.providers.web.cryptography.trust.EmitSEMaEventOnCertificateRotationHostnameVerifier;
import com.yolt.providers.web.metric.DefaultRestTemplateExchangeTagsProvider;
import com.yolt.providers.web.metric.MetricsClientHttpRequestInterceptor;
import com.yolt.providers.web.service.configuration.ProviderConnectionProperties;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.clienttokens.AbstractClientToken;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultClientConnectionReuseStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;
import org.springframework.context.ApplicationContext;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.lang.Nullable;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.*;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@RequiredArgsConstructor
@Slf4j
abstract class MutualTLSRestTemplateManager implements RestTemplateManager {

    private static final long KEEP_ALIVE_INDEFINITE_TIMEOUT = 180000L; // Set 3 minutes. Otherwise close the connection.

    private final ProviderConnectionProperties providerConnectionProperties;
    private final ApplicationContext applicationContext;
    private final KeyService keyService;
    private final AbstractClientToken clientToken;
    private final X509TrustManager trustManager;
    private final YoltProxySelectorBuilder yoltProxySelectorBuilder;
    private final MeterRegistry meterRegistry;
    private final String providerKey;

    /**
     * This RestTemplate is one setup for TLS, but without a client keypair.
     */
    private RestTemplate managedRestTemplate;

    /**
     * RestTemplate setup for mTLS, one *per* client keypair.
     */
    private final Map<MutualTLSReference, RestTemplate> managedMutualTLSRestTemplates = new ConcurrentHashMap<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public RestTemplate manage(UUID privateTransportKid,
                               X509Certificate[] clientCertificatesChain,
                               Function<ExternalRestTemplateBuilderFactory, RestTemplate> customizationFunction) {
        return manageForRestTemplateWithMutualTls(privateTransportKid, clientCertificatesChain, customizationFunction, false, KEEP_ALIVE_INDEFINITE_TIMEOUT);
    }

    @Override
    public RestTemplate manage(RestTemplateManagerConfiguration restTemplateManagerConfiguration) {
        if (restTemplateManagerConfiguration.isMutualTlsConfiguration()) {
            return manageForRestTemplateWithMutualTls(
                    restTemplateManagerConfiguration.getPrivateTransportKid(),
                    restTemplateManagerConfiguration.getClientCertificateChain(),
                    restTemplateManagerConfiguration.getCustomizationFunction(),
                    restTemplateManagerConfiguration.isDisableRedirectHandling(),
                    restTemplateManagerConfiguration.getDefaultKeepAliveTimeoutInMillis());
        } else {
            return manageForRestTemplateWithoutMutualTls(restTemplateManagerConfiguration);
        }
    }

    private RestTemplate manageForRestTemplateWithoutMutualTls(RestTemplateManagerConfiguration restTemplateManagerConfiguration) {
        if (managedRestTemplate == null) {
            ExternalRestTemplateBuilderFactory externalRestTemplateBuilderFactory = setupExternalRestTemplateBuilderFactory(
                    null,
                    null,
                    restTemplateManagerConfiguration.isDisableRedirectHandling(),
                    restTemplateManagerConfiguration.getDefaultKeepAliveTimeoutInMillis());
            managedRestTemplate = restTemplateManagerConfiguration.getCustomizationFunction().apply(externalRestTemplateBuilderFactory);
            log.info("Created a new RestTemplate in the cache for client-token: {}", clientToken.getSubject()); // NOSHERIFF
        }
        return managedRestTemplate;
    }

    private RestTemplate manageForRestTemplateWithMutualTls(UUID privateTransportKid,
                                                            X509Certificate[] clientCertificatesChain,
                                                            Function<ExternalRestTemplateBuilderFactory, RestTemplate> customizationFunction,
                                                            boolean disableRedirectHandling,
                                                            long defaultKeepAliveTimeoutInMillis) {
        MutualTLSReference keyPairReference = new MutualTLSReference(privateTransportKid, clientCertificatesChain);
        if (!managedMutualTLSRestTemplates.containsKey(keyPairReference)) {
            PrivateKey privateKey = determinePrivateKey(clientToken, privateTransportKid);
            ExternalRestTemplateBuilderFactory externalRestTemplateBuilderFactory =
                    setupExternalRestTemplateBuilderFactory(privateKey, clientCertificatesChain, disableRedirectHandling, defaultKeepAliveTimeoutInMillis);

            RestTemplate restTemplate = customizationFunction.apply(externalRestTemplateBuilderFactory);
            managedMutualTLSRestTemplates.put(keyPairReference, restTemplate);

            String clientCertInfo = clientCertificatesChain.length > 1 ? "clientCertificate" : "clientCertificatesChain";
            log.info("Created a new RestTemplate for mutual TLS in the cache for client-token: {}, privateTransportKid: {} and {} hashcode: {}",
                    clientToken.getSubject(), privateTransportKid, clientCertInfo, Arrays.hashCode(clientCertificatesChain)); // NOSHERIFF we want to see for which subject restTemplate was created and what transport certificate was used.
        }
        return managedMutualTLSRestTemplates.get(keyPairReference);
    }

    /**
     * For callers: you must provide either both privateKey and clientCertificate or neither.
     * <p>
     * If you provide both privateKey and clientCertificate this method will setup the SSLContext for mTLS.  Otherwise it will not.
     */
    @SneakyThrows
    private ExternalRestTemplateBuilderFactory setupExternalRestTemplateBuilderFactory(@Nullable PrivateKey privateKey,
                                                                                       @Nullable X509Certificate[] clientCertificatesChain,
                                                                                       boolean disableRedirectHandling,
                                                                                       long defaultKeepAliveTimeoutInMillis) {
        KeyManager[] keyManager = null;
        if (privateKey != null) {
            KeyStore keyStore = KeyStore.getInstance("Yolt");
            keyStore.load(null);
            keyStore.setKeyEntry("key", privateKey, null, clientCertificatesChain);
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, null);
            keyManager = kmf.getKeyManagers();
        }

        final SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
        sslContext.init(keyManager, new TrustManager[]{trustManager}, null);

        final LayeredConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(
                sslContext,
                new String[]{"TLSv1.2", "TLSv1.3"},
                CipherSuite.getYoltSupportedCipherSuites(),
                new EmitSEMaEventOnCertificateRotationHostnameVerifier(new DefaultHostnameVerifier())
        );

        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("https", socketFactory)
                .register("http", new PlainConnectionSocketFactory())
                .build();

        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
        connectionManager.setMaxTotal(providerConnectionProperties.getMaxTotal());
        connectionManager.setDefaultMaxPerRoute(providerConnectionProperties.getMaxPerRoute());
        connectionManager.setValidateAfterInactivity(providerConnectionProperties.getValidateAfterInactivityInMillis());

        final HttpClientBuilder httpClientBuilder = HttpClients.custom();
        if (disableRedirectHandling) {
            httpClientBuilder.disableRedirectHandling();
        }

        final CloseableHttpClient httpClient = httpClientBuilder
                .setConnectionManager(connectionManager)
                .setSSLSocketFactory(socketFactory)
                .setRoutePlanner(new SystemDefaultRoutePlanner(yoltProxySelectorBuilder.build(providerKey)))
                .setDefaultRequestConfig(RequestConfig.custom()
                        // We are ignoring cookies because it logs a large number of warnings.
                        // The DEFAULT cookie specification don't understand latest RFC-compliant headers.
                        .setCookieSpec(CookieSpecs.IGNORE_COOKIES)
                        .setConnectionRequestTimeout(providerConnectionProperties.getRequestTimeoutInMillis())
                        .setConnectTimeout(providerConnectionProperties.getConnectTimeoutInMillis())
                        .setSocketTimeout(providerConnectionProperties.getSocketTimeoutInMillis())
                        .build())
                .setConnectionReuseStrategy(DefaultClientConnectionReuseStrategy.INSTANCE)
                .setKeepAliveStrategy(TimeLimitedConnectionKeepAliveStrategy.withDefaultKeepAliveTimeoutInMillis(defaultKeepAliveTimeoutInMillis))
                .build();

        return applicationContext.getBean(ExternalRestTemplateBuilderFactory.class)
                .requestFactory(() -> new HttpComponentsClientHttpRequestFactory(httpClient))
                .additionalInterceptors(getInterceptors());
    }

    protected List<ClientHttpRequestInterceptor> getInterceptors() {
        return Collections.singletonList(
                new MetricsClientHttpRequestInterceptor(providerKey, meterRegistry, new DefaultRestTemplateExchangeTagsProvider())
        );
    }

    private PrivateKey determinePrivateKey(final AbstractClientToken clientToken, final UUID privateTransportKid) {
        try {
            return keyService.getPrivateTransportKey(clientToken, privateTransportKid);
        } catch (KeyNotFoundException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Note that just the private key is not uniquely describing a rest template/TLS connection. When a certificate is rotated, when the same
     * key is used.. we have to identify a rest template / TLS connection by both the key and certificate.
     */
    @Value
    private static class MutualTLSReference {

        UUID privateTransportKid;
        X509Certificate[] clientCertificatesChain;
    }
}
