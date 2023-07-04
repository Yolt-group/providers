package com.yolt.providers.web.cryptography.transport;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.common.JettySettings;
import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.http.AdminRequestHandler;
import com.github.tomakehurst.wiremock.http.HttpServer;
import com.github.tomakehurst.wiremock.http.HttpServerFactory;
import com.github.tomakehurst.wiremock.http.StubRequestHandler;
import com.github.tomakehurst.wiremock.jetty9.JettyHttpServer;
import com.yolt.providers.common.domain.RestTemplateManagerConfiguration;
import com.yolt.providers.common.domain.RestTemplateManagerConfigurationBuilder;
import com.yolt.providers.common.rest.ExternalRestTemplateBuilderFactory;
import com.yolt.providers.common.rest.YoltProxySelectorBuilder;
import com.yolt.providers.common.util.KeyUtil;
import com.yolt.providers.web.configuration.ApplicationConfiguration;
import com.yolt.providers.web.cryptography.KeyNotFoundException;
import com.yolt.providers.web.cryptography.VaultService;
import com.yolt.providers.web.intercept.RawDataProducer;
import com.yolt.providers.web.service.configuration.ProviderConnectionProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.SneakyThrows;
import nl.ing.lovebird.clienttokens.ClientToken;
import org.apache.http.client.protocol.ResponseProcessCookies;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import wiremock.org.eclipse.jetty.io.NetworkTrafficListener;
import wiremock.org.eclipse.jetty.server.ConnectionFactory;
import wiremock.org.eclipse.jetty.server.HttpConnectionFactory;
import wiremock.org.eclipse.jetty.server.ServerConnector;
import wiremock.org.eclipse.jetty.server.SslConnectionFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MutualTLSRestTemplateManagerTest {

    static {
        ApplicationConfiguration.ensureSecurityProvidersLoaded();
    }

    private static final Logger COOKIES_LOGGER = (Logger) LoggerFactory.getLogger(ResponseProcessCookies.class);
    private static final ClientToken CLIENT_TOKEN = mock(ClientToken.class);

    private static final String CERTIFICATES_PATH = "/certificates/fake/";
    private static final String CERTIFICATE_FILE = "fake-certificate-with-san.pem";
    private static final String KEYSTORE_FILE = "fake-san-keystore.p12";
    private static final String KEY_ALIAS = "1";
    private static final String PASSWORD = "changeit"; //NOSONAR: This password is used for fake keystore and just for testing.

    private static final String URL_FORMAT = "https://localhost:%d/test";
    private static final String JSON_RESPONSE = "{\"message\":\"success\"}";

    private static ProviderConnectionProperties providerConnectionProperties;

    @Mock
    private VaultService vaultService;

    @Mock
    private YoltProxySelectorBuilder yoltProxySelectorBuilder;

    @Mock
    private RawDataProducer rawDataProducer;

    @Mock
    private ApplicationContext applicationContext;

    private MutualTLSRestTemplateManager restTemplateManager;
    private WireMockServer singleTLSServer;
    private WireMockServer mutualTLSServer;
    private KeyStore keyStore;
    private ListAppender<ILoggingEvent> cookieListAppender;
    private MeterRegistry meterRegistry;

    @BeforeAll
    static void beforeAll() {
        providerConnectionProperties = new ProviderConnectionProperties();
        providerConnectionProperties.setMaxTotal(10);
        providerConnectionProperties.setMaxPerRoute(2);
        providerConnectionProperties.setValidateAfterInactivityInMillis(60000);
        providerConnectionProperties.setRequestTimeoutInMillis(-1);
        providerConnectionProperties.setConnectTimeoutInMillis(-1);
        providerConnectionProperties.setSocketTimeoutInMillis(-1);
    }

    @BeforeEach
    void beforeEach() {

        mutualTLSServer = new WireMockServer(mutualTLSWireMockConfiguration());
        mutualTLSServer.stubFor(createMappingBuilderForTestResponse());
        mutualTLSServer.start();

        singleTLSServer = new WireMockServer(singleTLSWireMockConfiguration());
        singleTLSServer.stubFor(createMappingBuilderForTestResponse());
        singleTLSServer.start();

        meterRegistry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());

        keyStore = loadKeyStore();
        when(CLIENT_TOKEN.getClientIdClaim()).thenReturn(UUID.randomUUID());
        restTemplateManager = new DataAwareMtlsRestTemplateManager(
                providerConnectionProperties, applicationContext, vaultService, CLIENT_TOKEN, getX509TrustManager(keyStore), yoltProxySelectorBuilder, meterRegistry, "TEST_IMPL_OPENBANKING", rawDataProducer);

        cookieListAppender = new ListAppender<>();
        cookieListAppender.start();

        COOKIES_LOGGER.addAppender(cookieListAppender);
    }

    @AfterEach
    public void afterEach() {
        COOKIES_LOGGER.detachAndStopAllAppenders();
        mutualTLSServer.stop();
        singleTLSServer.stop();
    }

    private WireMockConfiguration mutualTLSWireMockConfiguration() {
        return singleTLSWireMockConfiguration()
                .needClientAuth(true)
                .trustStorePath("src/test/resources/certificates/fake/fake-san-keystore.p12")
                .trustStorePassword(PASSWORD);
    }

    private WireMockConfiguration singleTLSWireMockConfiguration() {
        return new WireMockConfiguration()
                .dynamicPort()
                .dynamicHttpsPort()
                .httpServerFactory(new Pkcs12HttpsServerFactory())
                .extensions(new ResponseTemplateTransformer(false))
                .keystoreType("pkcs12")
                .keystorePath("src/test/resources/certificates/fake/fake-san-keystore.p12")
                .keystorePassword(PASSWORD)
                .keyManagerPassword(PASSWORD);
    }

    private MappingBuilder createMappingBuilderForTestResponse() {
        return get(urlEqualTo("/test"))
                .willReturn(aResponse()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withHeader(HttpHeaders.SET_COOKIE, "MRHSession=deleted;secure;expires=Thu, 01 Jan 1970 00:00:00 GMT")
                        .withBody(JSON_RESPONSE));
    }

    @Test
    @Deprecated
    public void shouldReturnTheSameRestTemplateInstanceForManageForSubsequentInvocations() {
        // given
        when(applicationContext.getBean(ExternalRestTemplateBuilderFactory.class)).thenReturn(new ExternalRestTemplateBuilderFactory());
        RestTemplate expected = restTemplateManager.manage(new RestTemplateManagerConfiguration(ExternalRestTemplateBuilderFactory::build));

        // when
        RestTemplate result1 = restTemplateManager.manage(new RestTemplateManagerConfiguration(ExternalRestTemplateBuilderFactory::build));
        RestTemplate result2 = restTemplateManager.manage(new RestTemplateManagerConfiguration(ExternalRestTemplateBuilderFactory::build));

        // then
        assertThat(result1).isNotNull();
        assertThat(result2).isNotNull();
        assertThat(result1)
                .isEqualTo(expected)
                .isEqualTo(result2);
    }

    @Test
    @Deprecated
    public void shouldReturnDifferentRestTemplateInstancesForManageWithDifferentParameters() throws KeyNotFoundException {
        // given
        UUID kid = UUID.randomUUID();
        X509Certificate clientCertificate = mock(X509Certificate.class);
        PrivateKey privateKey = mock(PrivateKey.class);
        when(vaultService.getPrivateTransportKey(CLIENT_TOKEN, kid)).thenReturn(privateKey);
        when(applicationContext.getBean(ExternalRestTemplateBuilderFactory.class)).thenReturn(new ExternalRestTemplateBuilderFactory());
        RestTemplate expected = restTemplateManager.manage(new RestTemplateManagerConfiguration(kid, clientCertificate, ExternalRestTemplateBuilderFactory::build));

        // when
        RestTemplate actual1 = restTemplateManager.manage(new RestTemplateManagerConfiguration(kid, clientCertificate, ExternalRestTemplateBuilderFactory::build));
        RestTemplate actual2 = restTemplateManager.manage(new RestTemplateManagerConfiguration(kid, mock(X509Certificate.class), ExternalRestTemplateBuilderFactory::build));
        UUID otherKid = UUID.randomUUID();
        when(vaultService.getPrivateTransportKey(CLIENT_TOKEN, otherKid)).thenReturn(privateKey);
        RestTemplate actual3 = restTemplateManager.manage(new RestTemplateManagerConfiguration(otherKid, clientCertificate, ExternalRestTemplateBuilderFactory::build));

        // then
        assertThat(actual1).isEqualTo(expected);
        assertThat(actual2).isNotEqualTo(expected);
        assertThat(actual3).isNotEqualTo(expected);
        verify(vaultService, times(2)).getPrivateTransportKey(CLIENT_TOKEN, kid);
    }

    @Test
    public void shouldReturnCorrectResponseWhenUsingTLSRestTemplateReturnedByManage() {
        // given
        String url = String.format(URL_FORMAT, singleTLSServer.httpsPort());
        when(applicationContext.getBean(ExternalRestTemplateBuilderFactory.class)).thenReturn(new ExternalRestTemplateBuilderFactory());

        // when
        RestTemplate restTemplate = restTemplateManager.manage(new RestTemplateManagerConfiguration(externalRestTemplateBuilderFactory ->
                externalRestTemplateBuilderFactory
                        .messageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                        .build()));

        // then
        JsonNode response = restTemplate.getForEntity(url, JsonNode.class).getBody();
        assertThat(response).isNotNull();
        assertThat(response.toString()).isEqualTo(JSON_RESPONSE);
        assertCookiesAreIgnored();
    }

    @Test
    public void shouldReturnTheSameRestTemplateInstanceAndCallVaultServiceOnlyOnceForManageWithTheSameParameters() throws KeyNotFoundException {
        // given
        UUID keyId = UUID.randomUUID();
        X509Certificate certificate = mock(X509Certificate.class);
        when(applicationContext.getBean(ExternalRestTemplateBuilderFactory.class)).thenReturn(new ExternalRestTemplateBuilderFactory());

        // when - Verifying that the restTemplateBiFunction is only called once, and that the private key is only retrieved once.
        RestTemplate restTemplate1 = restTemplateManager.manage(new RestTemplateManagerConfiguration(keyId, certificate, ExternalRestTemplateBuilderFactory::build));
        RestTemplate restTemplate2 = restTemplateManager.manage(new RestTemplateManagerConfiguration(keyId, certificate, ExternalRestTemplateBuilderFactory::build));

        // then
        assertThat(restTemplate1).isEqualTo(restTemplate2);
        verify(vaultService, times(1))
                .getPrivateTransportKey(CLIENT_TOKEN, keyId);
    }

    @Test
    public void shouldReturnDifferentRestTemplatesInstancesForManageWithDifferentParameters() throws KeyNotFoundException {
        // given
        UUID keyId1 = UUID.randomUUID();
        UUID keyId2 = UUID.randomUUID();
        X509Certificate certificate = mock(X509Certificate.class);
        PrivateKey privateKey = mock(PrivateKey.class);
        when(vaultService.getPrivateTransportKey(CLIENT_TOKEN, keyId1))
                .thenReturn(privateKey);
        when(applicationContext.getBean(ExternalRestTemplateBuilderFactory.class)).thenReturn(new ExternalRestTemplateBuilderFactory());

        // when - Verifying that we get new RestTemplates when the arguments are different
        RestTemplate restTemplate1 = restTemplateManager.manage(new RestTemplateManagerConfiguration(keyId1, certificate, ExternalRestTemplateBuilderFactory::build));
        RestTemplate restTemplate2 = restTemplateManager.manage(new RestTemplateManagerConfiguration(keyId2, certificate, ExternalRestTemplateBuilderFactory::build));

        // then
        assertThat(Set.of(restTemplate1, restTemplate2)).hasSize(2);
    }

    @Test
    public void shouldReturnCorrectResponseWhenUsingTLSRestTemplateForManageWithCorrectParameters() throws KeyNotFoundException {
        // given
        UUID keyId = UUID.randomUUID();
        X509Certificate certificate = loadCertificate();
        String url = String.format(URL_FORMAT, mutualTLSServer.httpsPort());
        when(vaultService.getPrivateTransportKey(CLIENT_TOKEN, keyId))
                .thenReturn(retrievePrivateKey(keyStore));
        when(applicationContext.getBean(ExternalRestTemplateBuilderFactory.class)).thenReturn(new ExternalRestTemplateBuilderFactory());

        // when
        RestTemplate restTemplate = restTemplateManager.manage(new RestTemplateManagerConfiguration(keyId, certificate, externalRestTemplateBuilderFactory ->
                externalRestTemplateBuilderFactory
                        .messageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                        .build()));

        // then
        JsonNode response = restTemplate.getForEntity(url, JsonNode.class).getBody();
        assertThat(response).isNotNull();
        assertThat(response.toString()).isEqualTo(JSON_RESPONSE);
        assertCookiesAreIgnored();
    }

    @Test
    public void shouldReturnTheSameRestTemplateInstanceAndCallVaultServiceOnlyOnceForManageWhenCalledAgainWithTheSameParameters() throws KeyNotFoundException {
        // given
        UUID keyId = UUID.randomUUID();
        X509Certificate[] certificateChain = new X509Certificate[]{mock(X509Certificate.class)};
        when(applicationContext.getBean(ExternalRestTemplateBuilderFactory.class)).thenReturn(new ExternalRestTemplateBuilderFactory());

        // when - Verifying that the restTemplateBiFunction is only called once, and that the private key is only retrieved once.
        RestTemplate restTemplate1 = restTemplateManager.manage(keyId, certificateChain, ExternalRestTemplateBuilderFactory::build);
        RestTemplate restTemplate2 = restTemplateManager.manage(keyId, certificateChain, ExternalRestTemplateBuilderFactory::build);

        // then
        assertThat(restTemplate1).isEqualTo(restTemplate2);
        verify(vaultService, times(1))
                .getPrivateTransportKey(CLIENT_TOKEN, keyId);
    }

    @Test
    public void shouldReturnTheSameRestTemplateInstancesForManageWhenCalledAgainWithTheSameParametersWithCertificateChain() throws KeyNotFoundException {
        // given
        UUID keyId1 = UUID.randomUUID();
        UUID keyId2 = UUID.randomUUID();
        X509Certificate[] certificateChain = new X509Certificate[]{mock(X509Certificate.class)};
        PrivateKey privateKey = mock(PrivateKey.class);
        when(vaultService.getPrivateTransportKey(CLIENT_TOKEN, keyId1))
                .thenReturn(privateKey);
        when(applicationContext.getBean(ExternalRestTemplateBuilderFactory.class)).thenReturn(new ExternalRestTemplateBuilderFactory());

        // when - Verifying that we get new RestTemplates when the arguments are different
        RestTemplate restTemplate1 = restTemplateManager.manage(keyId1, certificateChain, ExternalRestTemplateBuilderFactory::build);
        RestTemplate restTemplate2 = restTemplateManager.manage(keyId2, certificateChain, ExternalRestTemplateBuilderFactory::build);

        // then
        assertThat(Set.of(restTemplate1, restTemplate2)).hasSize(2);
    }

    @Test
    public void shouldReturnCorrectResponseWhenUsingTLSRestTemplateForManageWithCorrectParametersWithCertificateChain() throws KeyNotFoundException {
        // given
        UUID keyId = UUID.randomUUID();
        X509Certificate[] certificateChain = new X509Certificate[]{loadCertificate()};
        String url = String.format(URL_FORMAT, mutualTLSServer.httpsPort());

        when(vaultService.getPrivateTransportKey(CLIENT_TOKEN, keyId))
                .thenReturn(retrievePrivateKey(keyStore));
        when(applicationContext.getBean(ExternalRestTemplateBuilderFactory.class)).thenReturn(new ExternalRestTemplateBuilderFactory());

        // when
        RestTemplate restTemplate = restTemplateManager.manage(keyId, certificateChain, externalRestTemplateBuilderFactory ->
                externalRestTemplateBuilderFactory
                        .messageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                        .build());

        // then
        JsonNode response = restTemplate.getForEntity(url, JsonNode.class).getBody();
        assertThat(response).isNotNull();
        assertThat(response.toString()).isEqualTo(JSON_RESPONSE);
        assertCookiesAreIgnored();
    }

    @Test
    public void shouldReturnTheSameRestTemplateInstancesForManageWhenCalledAgainWithTheSameParametersWithDisabledRedirectHandlingAndDefaultKeepAliveTimeoutInMillis() throws KeyNotFoundException {
        // given
        UUID keyId1 = UUID.randomUUID();
        UUID keyId2 = UUID.randomUUID();
        X509Certificate certificate = loadCertificate();
        PrivateKey privateKey = mock(PrivateKey.class);
        when(vaultService.getPrivateTransportKey(CLIENT_TOKEN, keyId1))
                .thenReturn(privateKey);
        when(applicationContext.getBean(ExternalRestTemplateBuilderFactory.class)).thenReturn(new ExternalRestTemplateBuilderFactory());

        // when - Verifying that we get new RestTemplates when the arguments are different
        RestTemplate restTemplate1 = restTemplateManager.manage(RestTemplateManagerConfigurationBuilder.mutualTlsBuilder(keyId1, certificate, ExternalRestTemplateBuilderFactory::build)
                .disableRedirectHandling()
                .defaultKeepAliveTimeoutInMillis(5000L)
                .build());
        RestTemplate restTemplate2 = restTemplateManager.manage(RestTemplateManagerConfigurationBuilder.mutualTlsBuilder(keyId2, certificate, ExternalRestTemplateBuilderFactory::build)
                .disableRedirectHandling()
                .defaultKeepAliveTimeoutInMillis(5000L)
                .build());

        // then
        assertThat(Set.of(restTemplate1, restTemplate2)).hasSize(2);
    }

    @Test
    public void shouldReturnTheSameRestTemplateInstanceAndCallVaultServiceOnlyOnceForManageWhenCalledAgainWithTheSameParametersForDisabledRedirectHandlingAndDefaultKeepAliveTimeoutInMillis() throws KeyNotFoundException {
        // given
        UUID keyId = UUID.randomUUID();
        X509Certificate certificate = loadCertificate();
        when(applicationContext.getBean(ExternalRestTemplateBuilderFactory.class)).thenReturn(new ExternalRestTemplateBuilderFactory());

        // when - Verifying that the restTemplateBiFunction is only called once, and that the private key is only retrieved once.
        RestTemplate restTemplate1 = restTemplateManager.manage(RestTemplateManagerConfigurationBuilder.mutualTlsBuilder(keyId, certificate, ExternalRestTemplateBuilderFactory::build)
                .disableRedirectHandling()
                .defaultKeepAliveTimeoutInMillis(5000L)
                .build());
        RestTemplate restTemplate2 = restTemplateManager.manage(RestTemplateManagerConfigurationBuilder.mutualTlsBuilder(keyId, certificate, ExternalRestTemplateBuilderFactory::build)
                .disableRedirectHandling()
                .defaultKeepAliveTimeoutInMillis(5000L)
                .build());

        // then
        assertThat(restTemplate1).isEqualTo(restTemplate2);
        verify(vaultService, times(1))
                .getPrivateTransportKey(CLIENT_TOKEN, keyId);
    }

    private void assertCookiesAreIgnored() {
        for (ILoggingEvent loggingEvent : cookieListAppender.list) {
            String message = loggingEvent.getMessage();
            if (message.contains("Invalid cookie header: \"Set-Cookie") && message.contains("Invalid 'expires' attribute")) {
                fail("Cookies are not ignored. Found error about RFC-compliant headers: " + message);
            }
        }
    }

    @SneakyThrows
    private X509TrustManager getX509TrustManager(KeyStore keyStore) {
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);
        return (X509TrustManager) trustManagerFactory.getTrustManagers()[0];
    }

    @SneakyThrows
    private KeyStore loadKeyStore() {
        InputStream keyStoreFile = getClass().getResourceAsStream(CERTIFICATES_PATH + KEYSTORE_FILE);
        KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        keystore.load(keyStoreFile, PASSWORD.toCharArray());
        return keystore;
    }

    @SneakyThrows
    private X509Certificate loadCertificate() {
        URI certificateUri = Objects.requireNonNull(getClass().getResource(CERTIFICATES_PATH + CERTIFICATE_FILE)).toURI();
        String certificatePem = new String(Files.readAllBytes(Paths.get(certificateUri)));
        return KeyUtil.createCertificateFromPemFormat(certificatePem);
    }

    @SneakyThrows
    private PrivateKey retrievePrivateKey(KeyStore keystore) {
        return (PrivateKey) keystore.getKey(KEY_ALIAS, PASSWORD.toCharArray());
    }

    private class Pkcs12HttpsServerFactory implements HttpServerFactory {

        @Override
        public HttpServer buildHttpServer(Options options, AdminRequestHandler adminRequestHandler, StubRequestHandler stubRequestHandler) {
            return new JettyHttpServer(options, adminRequestHandler, stubRequestHandler) {
                @Override
                protected ServerConnector createServerConnector(String address, JettySettings settings, int port, NetworkTrafficListener listener, ConnectionFactory... factories) {
                    if (port == options.httpsSettings().port() && !(factories[0] instanceof HttpConnectionFactory)) {
                        SslConnectionFactory sslConnectionFactory = (SslConnectionFactory) factories[0];
                        sslConnectionFactory.getSslContextFactory().setKeyStorePassword(options.httpsSettings().keyStorePassword());
                        factories = new ConnectionFactory[]{sslConnectionFactory, factories[1]};
                    }
                    return super.createServerConnector(address, settings, port, listener, factories);
                }
            };
        }
    }
}