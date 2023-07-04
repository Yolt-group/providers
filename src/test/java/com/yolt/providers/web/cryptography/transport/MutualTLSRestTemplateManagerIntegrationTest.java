package com.yolt.providers.web.cryptography.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yolt.providers.common.domain.RestTemplateManagerConfiguration;
import com.yolt.providers.common.rest.YoltProxySelectorBuilder;
import com.yolt.providers.web.configuration.IntegrationTestContext;
import com.yolt.providers.web.cryptography.VaultService;
import com.yolt.providers.web.intercept.RawDataProducer;
import com.yolt.providers.web.service.configuration.ProviderConnectionProperties;
import com.yolt.providers.web.service.domain.ProviderInfo;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.clienttokens.ClientToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.Map;
import java.util.UUID;

import static com.yolt.providers.web.configuration.TestConfiguration.JACKSON_OBJECT_MAPPER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Slf4j
@IntegrationTestContext
public class MutualTLSRestTemplateManagerIntegrationTest {

    private static final ClientToken CLIENT_TOKEN = mock(ClientToken.class);
    private static final String CERTIFICATES_PATH = "/certificates/fake/";
    private static final String KEYSTORE_FILE = "fake-keystore.p12";
    private static final String PASSWORD = "changeit"; //NOSONAR: This password is used for fake keystore and just for testing.
    private KeyStore keyStore;
    private MutualTLSRestTemplateManager restTemplateManager;

    @Autowired
    private ProviderConnectionProperties providerConnectionProperties;
    @Autowired
    private VaultService vaultService;
    @Autowired
    private YoltProxySelectorBuilder yoltProxySelectorBuilder;
    @Autowired
    @Qualifier(JACKSON_OBJECT_MAPPER)
    private ObjectMapper objectMapper;
    @Autowired
    private MeterRegistry meterRegistry;
    @Autowired
    private RawDataProducer rawDataProducer;
    @Autowired
    private ApplicationContext applicationContext;

    @LocalServerPort
    private int localPort;

    @BeforeEach
    public void beforeEach() {
        keyStore = loadKeyStore();
        when(CLIENT_TOKEN.getClientIdClaim()).thenReturn(UUID.randomUUID());
        restTemplateManager = new DataAwareMtlsRestTemplateManager(
                providerConnectionProperties, applicationContext, vaultService, CLIENT_TOKEN, getX509TrustManager(keyStore), yoltProxySelectorBuilder, meterRegistry, "TEST_IMPL_OPENBANKING", rawDataProducer);
    }

    @Test
    public void shouldReturnMetricsForClientHttpRequestInterceptorWhenSendingGetRequestToExistingEndpoint() {
        // given
        RestTemplate restTemplate = restTemplateManager.manage(new RestTemplateManagerConfiguration(externalRestTemplateBuilderFactory ->
                externalRestTemplateBuilderFactory
                        .messageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                        .build()));
        ParameterizedTypeReference<Map<String, ProviderInfo>> parameterizedTypeReference = new ParameterizedTypeReference<Map<String, ProviderInfo>>() {
        };

        // when
        restTemplate.exchange("http://localhost:" + localPort + "/providers/provider-info/", HttpMethod.GET, null, parameterizedTypeReference);

        // then
        assertThat(meterRegistry.find("restclient.providers.request.duration")
                .tags(
                        "method", "GET",
                        "http_status", "200 OK",
                        "http_status_desc", "SUCCESS",
                        "provider", "TEST_IMPL_OPENBANKING").meters()).isNotEmpty();
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
}
