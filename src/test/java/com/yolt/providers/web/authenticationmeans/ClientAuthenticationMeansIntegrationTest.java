package com.yolt.providers.web.authenticationmeans;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yolt.providers.common.domain.authenticationmeans.BasicAuthenticationMean;
import com.yolt.providers.common.domain.authenticationmeans.types.NoWhiteCharacterStringType;
import com.yolt.providers.common.domain.authenticationmeans.types.PemType;
import com.yolt.providers.web.clientredirecturl.ClientRedirectUrl;
import com.yolt.providers.web.clientredirecturl.ClientRedirectUrlRepository;
import com.yolt.providers.web.configuration.IntegrationTestContext;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.clienttokens.constants.ClientTokenConstants;
import nl.ing.lovebird.clienttokens.test.TestClientTokens;
import nl.ing.lovebird.errorhandling.ErrorDTO;
import nl.ing.lovebird.providerdomain.ServiceType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static com.yolt.providers.web.authenticationmeans.ClientAuthenticationMeansService.ASTRIX_PLACEHOLDER;
import static com.yolt.providers.web.configuration.TestConfiguration.JACKSON_OBJECT_MAPPER;
import static nl.ing.lovebird.providerdomain.ServiceType.AIS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.groups.Tuple.tuple;
import static org.springframework.http.HttpMethod.*;

@IntegrationTestContext
class ClientAuthenticationMeansIntegrationTest {

    private static final String REQUEST_BODY_POST_DUMMY_AUTHENTICATION_MEANS;
    private static final String REQUEST_BODY_POST_DUMMY_AUTHENTICATION_MEANS_UNKNOWN_REDIRECTURLID;
    private static final String REQUEST_BODY_POST_DUMMY_AUTHENTICATION_MEANS_UPDATE;
    private static final String REQUEST_BODY_POST_BOOBY_AUTHENTICATION_MEANS;
    private static final String REQUEST_BODY_POST_BOOBY_AUTHENTICATION_MEANS_UPDATE;
    private static final String REQUEST_BODY_POST_DUMMY_AUTHENTICATION_MEANS_WITH_INVALID_FORMAT;
    private static final String REQUEST_BODY_POST_DUMMY_AUTHENTICATION_MEANS_WITH_NOT_RECOGNIZABLE_KEY;
    private static final String REQUEST_BODY_POST_DUMMY_AUTHENTICATION_MEANS_BATCH_TEST_IMPL_OPENBANKING;

    private static final String CERTIFICATE_KEY = "publicKey";
    private static final String AUDIENCE_KEY = "audience";
    private static final String INSTITUTION_KEY = "institutionId";

    private static final UUID VALID_REDIRECT_URL_ID = UUID.fromString("b23995dd-8e7a-413c-9a0b-b58c795a4b6d");
    private static final String REDIRECT_URL = "https://clientappdomain.com";
    private static final String TEST_IMPL_OPENBANKING = "TEST_IMPL_OPENBANKING_MOCK";
    public static final String POLISH_API_MOCK_NAME = "POLISH_API_MOCK";

    final UUID clientId = UUID.randomUUID();
    final UUID clientGroupId = UUID.randomUUID();

    static {
        try {
            REQUEST_BODY_POST_DUMMY_AUTHENTICATION_MEANS = readFile("sampleRequestProviderAuthenticationMeans.json").replace("\r\n", "\n");
            REQUEST_BODY_POST_DUMMY_AUTHENTICATION_MEANS_UNKNOWN_REDIRECTURLID = readFile("sampleRequestProviderAuthenticationMeansUnknownRedirect.json").replace("\r\n", "\n");
            REQUEST_BODY_POST_DUMMY_AUTHENTICATION_MEANS_UPDATE = readFile("sampleRequestProviderAuthenticationMeansUpdate.json").replace("\r\n", "\n");
            REQUEST_BODY_POST_BOOBY_AUTHENTICATION_MEANS = readFile("sampleRequestProviderAuthenticationMeans_BOOBY.json").replace("\r\n", "\n");
            REQUEST_BODY_POST_BOOBY_AUTHENTICATION_MEANS_UPDATE = readFile("sampleRequestProviderAuthenticationMeansUpdate_BOOBY.json").replace("\r\n", "\n");
            REQUEST_BODY_POST_DUMMY_AUTHENTICATION_MEANS_WITH_INVALID_FORMAT = readFile("sampleRequestProviderAuthenticationMeansWithInvalidFormat.json").replace("\r\n", "\n");
            REQUEST_BODY_POST_DUMMY_AUTHENTICATION_MEANS_WITH_NOT_RECOGNIZABLE_KEY = readFile("sampleRequestProviderAuthenticationMeansWithNotRecognizableKey.json").replace("\r\n", "\n");
            REQUEST_BODY_POST_DUMMY_AUTHENTICATION_MEANS_BATCH_TEST_IMPL_OPENBANKING = readFile("sampleBatchRequestTestImpOpenBankingAuthMeans.json").replace("\r\n", "\n");
        } catch (Exception e) {
            fail("could not read json file.");
            throw new RuntimeException("just a runtime exception for the compiler.. We fail anyways.", e);
        }
    }

    @Autowired
    @Qualifier(JACKSON_OBJECT_MAPPER)
    private ObjectMapper objectMapper;

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private ClientRedirectUrlRepository clientRedirectUrlRepository;

    @Autowired
    private ClientAuthenticationMeansRepository clientAuthenticationMeansRepository;

    @Autowired
    private ClientRedirectUrlClientConfigurationRepository clientRedirectUrlClientConfigurationRepository;

    @Autowired
    private TestClientTokens testClientTokens;

    @BeforeEach
    public void beforeEach() {
        // Attach autowired ObjectMapper, that contains registered configuration needed to simulate and test external party acquiring typed authentication means.
        // By default TestRestTemplate is not using it, blame SpringBoot automagic.
        for (HttpMessageConverter<?> converter : testRestTemplate.getRestTemplate().getMessageConverters()) {
            if (converter instanceof MappingJackson2HttpMessageConverter) {
                ((MappingJackson2HttpMessageConverter) converter).setObjectMapper(objectMapper);
            }
        }
    }

    @AfterEach
    public void after() {
        clientAuthenticationMeansRepository.delete(clientId);
        clientRedirectUrlClientConfigurationRepository.delete(clientId, VALID_REDIRECT_URL_ID);
    }

    @Test
    public void shouldUpdateExistingAndAddNewProviderAuthenticationMeansForSubsequentRequestsWithCorrectData() {
        // given
        String postProviderAuthenticationMeansUrl = preparePostProviderAuthenticationMeansUrl(clientId, AIS);
        String getProviderAuthenticationMeansUrl = prepareProviderAuthenticationMeansUrl(clientId);

        // Prepare expected authentication means
        Map<String, BasicAuthenticationMean> expectedMapForNatWest = new HashMap<>();
        expectedMapForNatWest.put(AUDIENCE_KEY, new BasicAuthenticationMean(NoWhiteCharacterStringType.getInstance(), ASTRIX_PLACEHOLDER));
        expectedMapForNatWest.put(CERTIFICATE_KEY, new BasicAuthenticationMean(PemType.getInstance(), ASTRIX_PLACEHOLDER));
        expectedMapForNatWest.put(INSTITUTION_KEY, new BasicAuthenticationMean(NoWhiteCharacterStringType.getInstance(), ASTRIX_PLACEHOLDER));

        Map<String, BasicAuthenticationMean> expectedMapForOpenBankingTestImpl = new HashMap<>();
        expectedMapForOpenBankingTestImpl.put(AUDIENCE_KEY, new BasicAuthenticationMean(NoWhiteCharacterStringType.getInstance(), ASTRIX_PLACEHOLDER));
        expectedMapForOpenBankingTestImpl.put(CERTIFICATE_KEY, new BasicAuthenticationMean(PemType.getInstance(), ASTRIX_PLACEHOLDER));
        expectedMapForOpenBankingTestImpl.put(INSTITUTION_KEY, new BasicAuthenticationMean(NoWhiteCharacterStringType.getInstance(), ASTRIX_PLACEHOLDER));

        // when
        ResponseEntity<Void> firstPostResponse = testRestTemplate
                .exchange(postProviderAuthenticationMeansUrl, POST, createHttpEntity(REQUEST_BODY_POST_DUMMY_AUTHENTICATION_MEANS), Void.class);
        ResponseEntity<Void> firstPostResponseUpdate = testRestTemplate
                .exchange(postProviderAuthenticationMeansUrl, POST, createHttpEntity(REQUEST_BODY_POST_DUMMY_AUTHENTICATION_MEANS_UPDATE), Void.class);
        ResponseEntity<ProviderTypedAuthenticationMeans[]> getResponse = testRestTemplate
                .exchange(getProviderAuthenticationMeansUrl, GET, createHttpEntity(""), ProviderTypedAuthenticationMeans[].class);
        ResponseEntity<Void> secondPostResponse = testRestTemplate
                .exchange(postProviderAuthenticationMeansUrl, POST, createHttpEntity(REQUEST_BODY_POST_BOOBY_AUTHENTICATION_MEANS_UPDATE), Void.class);
        ResponseEntity<ProviderTypedAuthenticationMeans[]> getAllResponse = testRestTemplate
                .exchange(getProviderAuthenticationMeansUrl, GET, createHttpEntity(""), ProviderTypedAuthenticationMeans[].class);

        // then
        assertThat(firstPostResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstPostResponseUpdate.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondPostResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getAllResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ProviderTypedAuthenticationMeans[] providerAuthenticationMeansBody = getResponse.getBody();
        assertThat(providerAuthenticationMeansBody).isNotNull().hasSize(1);
        ProviderTypedAuthenticationMeans providerAuthenticationMeans = providerAuthenticationMeansBody[0];
        assertThat(providerAuthenticationMeans)
                .extracting(ProviderTypedAuthenticationMeans::getProvider,
                        ProviderTypedAuthenticationMeans::getServiceType,
                        ProviderTypedAuthenticationMeans::getAuthenticationMeans)
                .contains(TEST_IMPL_OPENBANKING, AIS, expectedMapForNatWest);
        assertThat(providerAuthenticationMeans.getAuthenticationMeans()).hasSize(3);

        ProviderTypedAuthenticationMeans[] allProviderAuthenticationMeansBody = getAllResponse.getBody();
        assertThat(allProviderAuthenticationMeansBody).hasSize(2);
        assertThat(allProviderAuthenticationMeansBody)
                .extracting(ProviderTypedAuthenticationMeans::getProvider,
                        ProviderTypedAuthenticationMeans::getServiceType,
                        ProviderTypedAuthenticationMeans::getAuthenticationMeans)
                .contains(tuple(TEST_IMPL_OPENBANKING, AIS, expectedMapForNatWest),
                        tuple(POLISH_API_MOCK_NAME, AIS, expectedMapForOpenBankingTestImpl));
    }

    @Test
    public void shouldUpdateExistingAndAddNewProviderAuthenticationMeansPerRedirectUrlForSubsequentRequestsWithCorrectData() {
        // given
        String postProviderAuthenticationMeansUrlBatch = prepareProviderAuthenticationMeansUrlBatch(clientId);
        String getProviderAuthenticationMeansUrlWithRedirectUrlId = prepareProviderAuthenticationMeansUrlWithRedirectUrlId(clientId, VALID_REDIRECT_URL_ID);
        Map<String, BasicAuthenticationMean> expectedMapForNatwest = new HashMap<>();
        expectedMapForNatwest.put(AUDIENCE_KEY, new BasicAuthenticationMean(NoWhiteCharacterStringType.getInstance(), ASTRIX_PLACEHOLDER));
        expectedMapForNatwest.put(CERTIFICATE_KEY, new BasicAuthenticationMean(PemType.getInstance(), ASTRIX_PLACEHOLDER));
        expectedMapForNatwest.put(INSTITUTION_KEY, new BasicAuthenticationMean(NoWhiteCharacterStringType.getInstance(), ASTRIX_PLACEHOLDER));
        Map<String, BasicAuthenticationMean> expectedMapForOpenBankingTestImpl = new HashMap<>();
        expectedMapForOpenBankingTestImpl.put(AUDIENCE_KEY, new BasicAuthenticationMean(NoWhiteCharacterStringType.getInstance(), ASTRIX_PLACEHOLDER));
        expectedMapForOpenBankingTestImpl.put(CERTIFICATE_KEY, new BasicAuthenticationMean(PemType.getInstance(), ASTRIX_PLACEHOLDER));
        String requestBodyPostPolishApiMockAuthenticationMeans = REQUEST_BODY_POST_BOOBY_AUTHENTICATION_MEANS
                .replace(TEST_IMPL_OPENBANKING, POLISH_API_MOCK_NAME);
        // Create client application.
        clientRedirectUrlRepository
                .upsertClientRedirectUrl(new ClientRedirectUrl(clientId, VALID_REDIRECT_URL_ID, "https://clientappdomain.com", Instant.now()));

        // when
        ResponseEntity<Void> firstPostResponse = testRestTemplate
                .exchange(postProviderAuthenticationMeansUrlBatch, POST, createHttpEntity(REQUEST_BODY_POST_DUMMY_AUTHENTICATION_MEANS), Void.class);
        ResponseEntity<Void> firstPostResponseUpdate = testRestTemplate
                .exchange(postProviderAuthenticationMeansUrlBatch, POST, createHttpEntity(REQUEST_BODY_POST_DUMMY_AUTHENTICATION_MEANS_UPDATE), Void.class);
        ResponseEntity<ProviderTypedAuthenticationMeans[]> getResponse = testRestTemplate
                .exchange(getProviderAuthenticationMeansUrlWithRedirectUrlId, GET, createHttpEntity(""), ProviderTypedAuthenticationMeans[].class);
        ResponseEntity<Void> secondPostResponse = testRestTemplate
                .exchange(postProviderAuthenticationMeansUrlBatch, POST, createHttpEntity(requestBodyPostPolishApiMockAuthenticationMeans), Void.class);
        ResponseEntity<ProviderTypedAuthenticationMeans[]> getAllResponse = testRestTemplate
                .exchange(getProviderAuthenticationMeansUrlWithRedirectUrlId, GET, createHttpEntity(""), ProviderTypedAuthenticationMeans[].class);

        // then
        assertThat(firstPostResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstPostResponseUpdate.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondPostResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getAllResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ProviderTypedAuthenticationMeans[] getResponseBody = getResponse.getBody();
        assertThat(getResponseBody).isNotNull().hasSize(1);
        ProviderTypedAuthenticationMeans providerTypedAuthenticationMeans = getResponseBody[0];
        assertThat(providerTypedAuthenticationMeans.getAuthenticationMeans()).hasSize(3);
        assertThat(providerTypedAuthenticationMeans)
                .extracting(ProviderTypedAuthenticationMeans::getProvider,
                        ProviderTypedAuthenticationMeans::getServiceType,
                        ProviderTypedAuthenticationMeans::getAuthenticationMeans)
                .contains(TEST_IMPL_OPENBANKING, AIS, expectedMapForNatwest);

        ProviderTypedAuthenticationMeans[] getAllResponseBody = getAllResponse.getBody();
        assertThat(getAllResponseBody).isNotNull().hasSize(2);
        assertThat(getAllResponseBody).extracting(ProviderTypedAuthenticationMeans::getProvider,
                ProviderTypedAuthenticationMeans::getServiceType,
                ProviderTypedAuthenticationMeans::getAuthenticationMeans)
                .contains(tuple(TEST_IMPL_OPENBANKING, AIS, expectedMapForNatwest),
                        tuple(POLISH_API_MOCK_NAME, AIS, expectedMapForOpenBankingTestImpl));
    }

    @Test
    public void shouldUpdateExistingAndAddNewProviderAuthenticationMeansPerOneRedirectUrlForSubsequentRequestsWithCorrectData() {
        // given
        String postProviderAuthenticationMeansUrlBatch = prepareProviderAuthenticationMeansUrlBatch(clientId);
        String getProviderAuthenticationMeansUrlWithRedirectUrlId = prepareProviderAuthenticationMeansUrlWithRedirectUrlId(clientId, VALID_REDIRECT_URL_ID);
        Map<String, BasicAuthenticationMean> expectedMapForNatwest = new HashMap<>();
        expectedMapForNatwest.put(AUDIENCE_KEY, new BasicAuthenticationMean(NoWhiteCharacterStringType.getInstance(), ASTRIX_PLACEHOLDER));
        expectedMapForNatwest.put(CERTIFICATE_KEY, new BasicAuthenticationMean(PemType.getInstance(), ASTRIX_PLACEHOLDER));
        expectedMapForNatwest.put(INSTITUTION_KEY, new BasicAuthenticationMean(NoWhiteCharacterStringType.getInstance(), ASTRIX_PLACEHOLDER));
        Map<String, BasicAuthenticationMean> expectedMapForOpenBankingTestImpl = new HashMap<>();
        expectedMapForOpenBankingTestImpl.put(AUDIENCE_KEY, new BasicAuthenticationMean(NoWhiteCharacterStringType.getInstance(), ASTRIX_PLACEHOLDER));
        expectedMapForOpenBankingTestImpl.put(CERTIFICATE_KEY, new BasicAuthenticationMean(PemType.getInstance(), ASTRIX_PLACEHOLDER));
        String requestBodyPostPolishApiMockAuthenticationMeans = REQUEST_BODY_POST_DUMMY_AUTHENTICATION_MEANS
                .replace(TEST_IMPL_OPENBANKING, POLISH_API_MOCK_NAME);
        // Create client application.
        clientRedirectUrlRepository
                .upsertClientRedirectUrl(new ClientRedirectUrl(clientId, VALID_REDIRECT_URL_ID, "https://clientappdomain.com", Instant.now()));

        // when
        ResponseEntity<Void> firstPostResponse = testRestTemplate
                .exchange(postProviderAuthenticationMeansUrlBatch, POST, createHttpEntity(REQUEST_BODY_POST_DUMMY_AUTHENTICATION_MEANS), Void.class);
        ResponseEntity<Void> firstPostResponseUpdate = testRestTemplate
                .exchange(postProviderAuthenticationMeansUrlBatch, POST, createHttpEntity(REQUEST_BODY_POST_DUMMY_AUTHENTICATION_MEANS_UPDATE), Void.class);
        ResponseEntity<ProviderTypedAuthenticationMeans[]> getResponse = testRestTemplate
                .exchange(getProviderAuthenticationMeansUrlWithRedirectUrlId, GET, createHttpEntity(""), ProviderTypedAuthenticationMeans[].class);
        ResponseEntity<Void> secondPostResponse = testRestTemplate
                .exchange(postProviderAuthenticationMeansUrlBatch, POST, createHttpEntity(requestBodyPostPolishApiMockAuthenticationMeans), Void.class);
        ResponseEntity<ProviderTypedAuthenticationMeans[]> getAllResponse = testRestTemplate
                .exchange(getProviderAuthenticationMeansUrlWithRedirectUrlId, GET, createHttpEntity(""), ProviderTypedAuthenticationMeans[].class);

        // then
        assertThat(firstPostResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstPostResponseUpdate.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondPostResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getAllResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ProviderTypedAuthenticationMeans[] getResponseBody = getResponse.getBody();
        assertThat(getResponseBody).isNotNull().hasSize(1);
        ProviderTypedAuthenticationMeans providerTypedAuthenticationMeans = getResponseBody[0];
        assertThat(providerTypedAuthenticationMeans.getAuthenticationMeans()).hasSize(3);
        assertThat(providerTypedAuthenticationMeans)
                .extracting(ProviderTypedAuthenticationMeans::getProvider,
                        ProviderTypedAuthenticationMeans::getServiceType,
                        ProviderTypedAuthenticationMeans::getAuthenticationMeans)
                .contains(TEST_IMPL_OPENBANKING, AIS, expectedMapForNatwest);

        ProviderTypedAuthenticationMeans[] getAllResponseBody = getAllResponse.getBody();
        assertThat(getAllResponseBody).isNotNull().hasSize(2);
        assertThat(getAllResponseBody).extracting(ProviderTypedAuthenticationMeans::getProvider,
                ProviderTypedAuthenticationMeans::getServiceType,
                ProviderTypedAuthenticationMeans::getAuthenticationMeans)
                .contains(tuple(TEST_IMPL_OPENBANKING, AIS, expectedMapForNatwest),
                        tuple(POLISH_API_MOCK_NAME, AIS, expectedMapForOpenBankingTestImpl));
    }

    @Test
    public void shouldUpdateExistingAndAddNewAuthenticationMeansPerServiceTypeForSubsequentRequestsWithCorrectData() {
        // given
        String providerAuthenticationMeansUrlBatch = prepareProviderAuthenticationMeansUrlBatch(clientId);
        String providerAuthenticationMeansUrlWithRedirectUrlId = prepareProviderAuthenticationMeansUrlWithRedirectUrlId(clientId, VALID_REDIRECT_URL_ID);
        // create client application.
        clientRedirectUrlRepository
                .upsertClientRedirectUrl(new ClientRedirectUrl(clientId, VALID_REDIRECT_URL_ID, "https://clientappdomain.com", Instant.now()));

        // when
        // Post authentication means. (AIS)
        ResponseEntity<Void> firstPostResponse = testRestTemplate
                .exchange(providerAuthenticationMeansUrlBatch, POST, createHttpEntity(REQUEST_BODY_POST_DUMMY_AUTHENTICATION_MEANS_BATCH_TEST_IMPL_OPENBANKING), Void.class);
        // Replicate for PIS (can't do it by controller yet because there is no PIS implementation with 'getTypedAuthenticationMeans'
        Optional<InternalClientRedirectUrlClientConfiguration> internalClientRedirectUrlClientConfigurations = clientRedirectUrlClientConfigurationRepository
                .get(clientId, VALID_REDIRECT_URL_ID, AIS, TEST_IMPL_OPENBANKING);
        internalClientRedirectUrlClientConfigurations.ifPresent(configuration -> {
            configuration.setServiceType(ServiceType.PIS);
            clientRedirectUrlClientConfigurationRepository.upsert(configuration);
        });
        ResponseEntity<ProviderTypedAuthenticationMeans[]> getResponse = testRestTemplate
                .exchange(providerAuthenticationMeansUrlWithRedirectUrlId, GET, createHttpEntity(""), ProviderTypedAuthenticationMeans[].class);
        ResponseEntity<ProviderTypedAuthenticationMeans[]> getFilteredResponse = testRestTemplate
                .exchange(providerAuthenticationMeansUrlWithRedirectUrlId + "?serviceType=AIS", GET, createHttpEntity(""), ProviderTypedAuthenticationMeans[].class);

        // then
        assertThat(firstPostResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).hasSize(2);
        assertThat(getFilteredResponse.getBody()).hasSize(1);
    }

    @Test
    public void shouldUpdateExistingAndAddNewAuthenticationMeansPerServiceTypeForSubsequentRequestsWithCorrectDataForUploadEndpoint() {
        // given
        String providerAuthenticationMeansUrlBatch = prepareProviderAuthenticationMeansUrlBatch(clientId);
        String providerAuthenticationMeansUrlWithRedirectUrlId = prepareProviderAuthenticationMeansUrlWithRedirectUrlId(clientId, VALID_REDIRECT_URL_ID);
        // create client application.
        clientRedirectUrlRepository
                .upsertClientRedirectUrl(new ClientRedirectUrl(clientId, VALID_REDIRECT_URL_ID, "https://clientappdomain.com", Instant.now()));

        // when
        // Post authentication means. (AIS)
        ResponseEntity<Void> firstPostResponse = testRestTemplate
                .exchange(providerAuthenticationMeansUrlBatch, POST, createHttpEntity(REQUEST_BODY_POST_DUMMY_AUTHENTICATION_MEANS), Void.class);
        // Replicate for PIS (can't do it by controller yet because there is no PIS implementation with 'getTypedAuthenticationMeans'
        Optional<InternalClientRedirectUrlClientConfiguration> internalClientRedirectUrlClientConfigurations = clientRedirectUrlClientConfigurationRepository
                .get(clientId, VALID_REDIRECT_URL_ID, AIS, TEST_IMPL_OPENBANKING);
        internalClientRedirectUrlClientConfigurations.ifPresent(configuration -> {
            configuration.setServiceType(ServiceType.PIS);
            clientRedirectUrlClientConfigurationRepository.upsert(configuration);
        });
        ResponseEntity<ProviderTypedAuthenticationMeans[]> getResponse = testRestTemplate
                .exchange(providerAuthenticationMeansUrlWithRedirectUrlId, GET, createHttpEntity(""), ProviderTypedAuthenticationMeans[].class);
        ResponseEntity<ProviderTypedAuthenticationMeans[]> getFilteredResponse = testRestTemplate
                .exchange(providerAuthenticationMeansUrlWithRedirectUrlId + "?serviceType=AIS", GET, createHttpEntity(""), ProviderTypedAuthenticationMeans[].class);

        // then
        assertThat(firstPostResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).hasSize(2);
        assertThat(getFilteredResponse.getBody()).hasSize(1);
    }

    @Test
    public void shouldDeleteProviderOnceForSubsequentCallWithCorrectData() {
        // given
        String providerAuthenticationMeansUrl = prepareProviderAuthenticationMeansUrl(clientId);
        String preparePostProviderAuthenticationMeansUrl = preparePostProviderAuthenticationMeansUrl(clientId, AIS);
        String urlWithProviderName = providerAuthenticationMeansUrl + "/" + TEST_IMPL_OPENBANKING;
        Map<String, BasicAuthenticationMean> expectedMap = new HashMap<>();
        expectedMap.put(AUDIENCE_KEY, new BasicAuthenticationMean(NoWhiteCharacterStringType.getInstance(), ASTRIX_PLACEHOLDER));
        expectedMap.put(CERTIFICATE_KEY, new BasicAuthenticationMean(PemType.getInstance(), ASTRIX_PLACEHOLDER));

        // when
        // Post authentication means.
        ResponseEntity<Void> firstPostResponse = testRestTemplate
                .exchange(preparePostProviderAuthenticationMeansUrl, POST, createHttpEntity(REQUEST_BODY_POST_DUMMY_AUTHENTICATION_MEANS), Void.class);
        ResponseEntity<ProviderTypedAuthenticationMeans[]> getResponse = testRestTemplate
                .exchange(providerAuthenticationMeansUrl, GET, createHttpEntity(""), ProviderTypedAuthenticationMeans[].class);
        testRestTemplate.exchange(urlWithProviderName, DELETE, createHttpEntity(""), Void.class);
        testRestTemplate.delete(urlWithProviderName);
        // Idempotent
        testRestTemplate.delete(urlWithProviderName);
        // Try to get it again
        ResponseEntity<ProviderTypedAuthenticationMeans[]> getDeletedResponse = testRestTemplate
                .exchange(providerAuthenticationMeansUrl, GET, createHttpEntity(""), ProviderTypedAuthenticationMeans[].class);

        // then
        assertThat(firstPostResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getDeletedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(getResponse.getBody()).isNotNull().hasSize(1);
        assertThat(getResponse.getBody()[0]).extracting(ProviderTypedAuthenticationMeans::getProvider,
                ProviderTypedAuthenticationMeans::getServiceType,
                ProviderTypedAuthenticationMeans::getAuthenticationMeans)
                .contains(TEST_IMPL_OPENBANKING, AIS, expectedMap);
        assertThat(getDeletedResponse.getBody()).isEmpty();
    }

    @Test
    public void shouldDeleteProviderAuthenticationMeansPerRedirectUrlOnceForSubsequentRequestsWithCorrectData() {
        // given
        String prepareProviderAuthenticationMeansUrlBatch = prepareProviderAuthenticationMeansUrlBatch(clientId);
        String providerAuthenticationMeansUrlWithRedirectUrlId = prepareProviderAuthenticationMeansUrlWithRedirectUrlId(clientId, VALID_REDIRECT_URL_ID);
        String urlWithRedirectUrlIdAndProviderName = providerAuthenticationMeansUrlWithRedirectUrlId + "/" + TEST_IMPL_OPENBANKING + "/" + AIS.name();
        Map<String, BasicAuthenticationMean> expectedMap = new HashMap<>();
        expectedMap.put(AUDIENCE_KEY, new BasicAuthenticationMean(NoWhiteCharacterStringType.getInstance(), ASTRIX_PLACEHOLDER));
        expectedMap.put(CERTIFICATE_KEY, new BasicAuthenticationMean(PemType.getInstance(), ASTRIX_PLACEHOLDER));
        // create client application.
        clientRedirectUrlRepository
                .upsertClientRedirectUrl(new ClientRedirectUrl(clientId, VALID_REDIRECT_URL_ID, "https://clientappdomain.com", Instant.now()));

        // when
        // Post authentication means.
        ResponseEntity<Void> postResponse = testRestTemplate
                .exchange(prepareProviderAuthenticationMeansUrlBatch, POST, createHttpEntity(REQUEST_BODY_POST_DUMMY_AUTHENTICATION_MEANS), Void.class);
        ResponseEntity<ProviderTypedAuthenticationMeans[]> getResponse = testRestTemplate
                .exchange(providerAuthenticationMeansUrlWithRedirectUrlId, GET, createHttpEntity(""), ProviderTypedAuthenticationMeans[].class);

        ClientToken clientToken = testClientTokens.createClientToken(clientGroupId, clientId);
        testRestTemplate.execute(urlWithRedirectUrlIdAndProviderName, HttpMethod.DELETE,
                // Add client token on the DELETE-call
                request -> request.getHeaders().put(ClientTokenConstants.CLIENT_TOKEN_HEADER_NAME, List.of(clientToken.getSerialized())),
                null);
        ResponseEntity<ProviderTypedAuthenticationMeans[]> getDeletedResponse = testRestTemplate
                .exchange(providerAuthenticationMeansUrlWithRedirectUrlId, GET, createHttpEntity(""), ProviderTypedAuthenticationMeans[].class);

        // then
        assertThat(postResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getDeletedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(getResponse.getBody()).isNotNull().hasSize(1);
        assertThat(getResponse.getBody()[0]).extracting(ProviderTypedAuthenticationMeans::getProvider,
                ProviderTypedAuthenticationMeans::getServiceType,
                ProviderTypedAuthenticationMeans::getAuthenticationMeans)
                .contains(TEST_IMPL_OPENBANKING, AIS, expectedMap);

        assertThat(getDeletedResponse.getBody()).isNotNull().isEmpty();
    }

    @Test
    public void shouldFailToAddProviderAuthenticationMeansWhenSendingRequestWithNonExistingClientRedirectUrl() {
        // given
        String urlWithNonExistingRedirectUrlId = prepareProviderAuthenticationMeansUrlBatch(clientId);

        // when
        // Post authentication means.
        ErrorDTO notFoundRedirectUrlWithClientErrorResponse = testRestTemplate
                .exchange(urlWithNonExistingRedirectUrlId, POST, createHttpEntity(REQUEST_BODY_POST_DUMMY_AUTHENTICATION_MEANS_UNKNOWN_REDIRECTURLID), ErrorDTO.class)
                .getBody();

        // then
        assertThat(notFoundRedirectUrlWithClientErrorResponse).isNotNull();
        assertThat(notFoundRedirectUrlWithClientErrorResponse.getCode()).isEqualTo("PR001");
        assertThat(notFoundRedirectUrlWithClientErrorResponse.getMessage()).isEqualTo(String.format("Could not find client redirect url with client-id: %s and redirect-url-id: %s",
                clientId, "44c76f4d-707b-4315-ad26-df79fe33331e"));
    }

    @Test
    public void shouldFailToAddProviderAuthenticationMeansWhenValidationFails() {
        // given
        String urlWithNonExistingRedirectUrlId = prepareProviderAuthenticationMeansUrlBatch(clientId);

        // when
        // Post authentication means.
        ErrorDTO notValidBodyErrorResponse = testRestTemplate
                .exchange(urlWithNonExistingRedirectUrlId, POST, createHttpEntity("{\"provider\": null, \"scopes\": null, \"authenticationMeans\": null}"), ErrorDTO.class)
                .getBody();

        // then
        assertThat(notValidBodyErrorResponse).isNotNull();
        assertThat(notValidBodyErrorResponse.getCode()).isEqualTo("PR1008");
        assertThat(notValidBodyErrorResponse.getMessage())
                .matches("Method argument not valid \\(request body validation error\\)\\." +
                        " Offending field: (provider|authenticationMeans|serviceTypes|redirectUrlIds)");
    }

    @Test
    public void shouldFailToSaveProviderAuthenticationMeansPerRedirectUrlWhenAuthenticationMeansValidationFails() {
        // given
        String providerAuthenticationMeansUrlBatch = prepareProviderAuthenticationMeansUrlBatch(clientId);
        // Create client application.
        clientRedirectUrlRepository
                .upsertClientRedirectUrl(new ClientRedirectUrl(clientId, VALID_REDIRECT_URL_ID, REDIRECT_URL, Instant.now()));

        // when
        // Post authentication means.
        ErrorDTO invalidFormatErrorResponse = testRestTemplate
                .exchange(providerAuthenticationMeansUrlBatch, POST, createHttpEntity(REQUEST_BODY_POST_DUMMY_AUTHENTICATION_MEANS_WITH_INVALID_FORMAT), ErrorDTO.class)
                .getBody();
        ErrorDTO notRecognizableKeyErrorResponse = testRestTemplate
                .exchange(providerAuthenticationMeansUrlBatch, POST, createHttpEntity(REQUEST_BODY_POST_DUMMY_AUTHENTICATION_MEANS_WITH_NOT_RECOGNIZABLE_KEY), ErrorDTO.class)
                .getBody();

        // then
        assertThat(invalidFormatErrorResponse).isNotNull();
        assertThat(invalidFormatErrorResponse.getCode()).isEqualTo("PR039");
        assertThat(invalidFormatErrorResponse.getMessage()).isEqualTo("Wrong format of provided authentication means.");

        assertThat(notRecognizableKeyErrorResponse).isNotNull();
        assertThat(notRecognizableKeyErrorResponse.getCode()).isEqualTo("PR040");
        assertThat(notRecognizableKeyErrorResponse.getMessage()).isEqualTo("Authentication mean key not recognized.");
    }

    @Test
    public void shouldFailToSaveProviderAuthenticationMeansWhenAuthenticationMeansValidationFails() {
        // given
        String preparePostProviderAuthenticationMeansUrl = preparePostProviderAuthenticationMeansUrl(clientId, AIS);
        // Create client application.
        clientRedirectUrlRepository
                .upsertClientRedirectUrl(new ClientRedirectUrl(clientId, VALID_REDIRECT_URL_ID, REDIRECT_URL, Instant.now()));

        // when
        // Post authentication means.
        ErrorDTO invalidFormatErrorResponse = testRestTemplate
                .exchange(preparePostProviderAuthenticationMeansUrl, POST, createHttpEntity(REQUEST_BODY_POST_DUMMY_AUTHENTICATION_MEANS_WITH_INVALID_FORMAT), ErrorDTO.class)
                .getBody();
        ErrorDTO notRecognizableKeyErrorResponse = testRestTemplate
                .exchange(preparePostProviderAuthenticationMeansUrl, POST, createHttpEntity(REQUEST_BODY_POST_DUMMY_AUTHENTICATION_MEANS_WITH_NOT_RECOGNIZABLE_KEY), ErrorDTO.class)
                .getBody();

        // then
        assertThat(invalidFormatErrorResponse).isNotNull();
        assertThat(invalidFormatErrorResponse.getCode()).isEqualTo("PR039");
        assertThat(invalidFormatErrorResponse.getMessage()).isEqualTo("Wrong format of provided authentication means.");
        assertThat(notRecognizableKeyErrorResponse).isNotNull();
        assertThat(notRecognizableKeyErrorResponse.getCode()).isEqualTo("PR040");
        assertThat(notRecognizableKeyErrorResponse.getMessage()).isEqualTo("Authentication mean key not recognized.");
    }

    private static String readFile(String filename) throws Exception {
        Path filePath = new File(Objects.requireNonNull(ClientAuthenticationMeansIntegrationTest.class.getClassLoader()
                .getResource(filename)).toURI()).toPath();
        return String.join("\n", Files.readAllLines(filePath, StandardCharsets.UTF_8));
    }

    private HttpEntity<String> createHttpEntity(final String requestBody) {
        ClientToken clientToken = testClientTokens.createClientToken(clientGroupId, clientId);
        HttpHeaders headers = new HttpHeaders();
        headers.add(ClientTokenConstants.CLIENT_TOKEN_HEADER_NAME, clientToken.getSerialized());
        headers.setContentType(MediaType.APPLICATION_JSON);

        return new HttpEntity<>(requestBody, headers);
    }

    private String prepareProviderAuthenticationMeansUrl(final UUID clientId) {
        return UriComponentsBuilder.fromUriString("/clients/{clientId}/provider-authentication-means")
                .buildAndExpand(clientId.toString())
                .toUriString();
    }

    private String preparePostProviderAuthenticationMeansUrl(final UUID clientId, ServiceType serviceType) {
        return UriComponentsBuilder.fromUriString("/clients/{clientId}/provider-authentication-means/{serviceType}")
                .buildAndExpand(clientId.toString(), serviceType.name())
                .toUriString();
    }

    private String prepareProviderAuthenticationMeansUrlBatch(final UUID clientId) {
        return UriComponentsBuilder.fromUriString("/clients/{clientId}/provider-authentication-means/batch")
                .build()
                .expand(clientId.toString())
                .toUriString();
    }

    private String prepareProviderAuthenticationMeansUrlWithRedirectUrlId(final UUID clientId, final UUID redirectUrlId) {
        return UriComponentsBuilder.fromUriString("/clients/{clientId}/redirect-urls/{redirectUrls}/provider-authentication-means")
                .build()
                .expand(clientId.toString(), redirectUrlId.toString())
                .toUriString();
    }
}
