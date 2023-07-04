package com.yolt.providers.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yolt.providers.common.exception.PaymentValidationException;
import com.yolt.providers.common.exception.dto.DetailedErrorInformation;
import com.yolt.providers.common.exception.dto.FieldName;
import com.yolt.providers.common.pis.common.PeriodicPaymentExecutionRule;
import com.yolt.providers.common.pis.common.PeriodicPaymentFrequency;
import com.yolt.providers.common.pis.common.SepaPeriodicPaymentInfo;
import com.yolt.providers.common.pis.sepa.SepaAccountDTO;
import com.yolt.providers.common.pis.sepa.SepaAmountDTO;
import com.yolt.providers.common.pis.sepa.SepaInitiatePaymentRequestDTO;
import com.yolt.providers.web.ProviderApp;
import com.yolt.providers.web.circuitbreaker.ProvidersCircuitBreaker;
import com.yolt.providers.web.circuitbreaker.ProvidersCircuitBreakerException;
import com.yolt.providers.web.circuitbreaker.ProvidersCircuitBreakerFactory;
import com.yolt.providers.web.circuitbreaker.ProvidersCircuitBreakerMock;
import com.yolt.providers.web.configuration.TestConfiguration;
import com.yolt.providers.web.controller.dto.ExternalInitiateSepaPaymentRequestDTO;
import com.yolt.providers.web.controller.dto.ExternalSepaInitiatePaymentRequestDTO;
import com.yolt.providers.web.controller.dto.InitiateSepaPaymentRequestDTO;
import com.yolt.providers.web.controller.dto.SubmitSepaPaymentRequestDTO;
import com.yolt.providers.web.errorhandling.ExtendedErrorDTO;
import com.yolt.providers.web.errorhandling.ExtendedExceptionHandlingService;
import com.yolt.providers.web.exception.ProviderNotFoundException;
import com.yolt.providers.web.service.ClientTokenVerificationService;
import com.yolt.providers.web.service.ProviderSepaPaymentService;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.clienttokens.constants.ClientTokenConstants;
import nl.ing.lovebird.clienttokens.test.TestClientTokens;
import nl.ing.lovebird.errorhandling.ErrorDTO;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import org.jose4j.jwt.JwtClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Consumer;

import static com.yolt.providers.web.configuration.TestConfiguration.JACKSON_OBJECT_MAPPER;
import static nl.ing.lovebird.clienttokens.constants.ClientTokenConstants.EXTRA_CLAIM_ISSUED_FOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@WebMvcTest(controllers = ProviderSepaPaymentController.class)
@ContextConfiguration(classes = {ProviderApp.class})
@Import({
        TestConfiguration.class,
        ExtendedExceptionHandlingService.class
})
class ProviderSepaPaymentControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CLIENT_GROUP_ID = UUID.randomUUID();
    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID SITE_ID = UUID.randomUUID();
    private static final String YOLT_PROVIDER_SEPA_PIS_NAME = "YOLT_PROVIDER_SEPA_PIS";

    private final ProvidersCircuitBreaker circuitBreaker = new ProvidersCircuitBreakerMock();

    @Autowired
    @Qualifier(JACKSON_OBJECT_MAPPER)
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private Clock clock;
    @Autowired
    private TestClientTokens testClientTokens;

    @MockBean
    private ProviderSepaPaymentService providerSepaPaymentService;
    @MockBean
    private ClientTokenVerificationService clientTokenVerificationService;

    @Mock
    private ProvidersCircuitBreakerFactory circuitBreakerFactory;

    @BeforeEach
    public void beforeEach() {
        when(circuitBreakerFactory.create(any(), any(), any(), any(), any())).thenReturn(circuitBreaker);
    }

    @Test
    public void shouldReturnNotFoundResponseWhenSendingPostRequestToProviderPaymentSepaSingleInitiateEndpointWithNonExistingProviderKey() throws Exception {
        // given
        ExternalInitiateSepaPaymentRequestDTO initiateSepaPaymentRequestDTO = new ExternalInitiateSepaPaymentRequestDTO(minimalValidSinglePaymentRequest(),
                "", new AuthenticationMeansReference(CLIENT_ID, UUID.randomUUID()), "http://redirect.url", null);
        when(providerSepaPaymentService.initiateSinglePayment(eq("FAKE_PROVIDER"), any(), any(), any(), any(boolean.class))).thenThrow(ProviderNotFoundException.class);
        // when
        MockHttpServletResponse response = callEndpoint("/FAKE_PROVIDER/payment/sepa/single/initiate", createGenericParameters(), initiateSepaPaymentRequestDTO);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    public void shouldReturnBadRequestResponseWhenSendingPostRequestToProviderPaymentSepaSingleInitiateEndpointWithInvalidRequestBody() throws Exception {
        // given
        ExternalInitiateSepaPaymentRequestDTO initiateSepaPaymentRequestDTO = new ExternalInitiateSepaPaymentRequestDTO(minimalValidSinglePaymentRequest(),
                null, null, null, null);

        // when
        MockHttpServletResponse response = callEndpoint("/YOLT_PROVIDER_SEPA_PIS/payment/sepa/single/initiate", initiateSepaPaymentRequestDTO);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    public void shouldReturnOkResponseWhenSendingPostRequestToProviderPaymentSepaSingleInitiateEndpointWithCorrectData() throws Exception {
        // given
        AuthenticationMeansReference authenticationMeansReference = new AuthenticationMeansReference(CLIENT_ID, UUID.randomUUID());
        ExternalInitiateSepaPaymentRequestDTO initiateSepaPaymentRequestDTO = new ExternalInitiateSepaPaymentRequestDTO(minimalValidSinglePaymentRequest(),
                "", authenticationMeansReference, "http://redirect.url", null);
        // when
        MockHttpServletResponse response = callEndpoint("/YOLT_PROVIDER_SEPA_PIS/payment/sepa/single/initiate", createGenericParameters(), initiateSepaPaymentRequestDTO);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        verify(providerSepaPaymentService).initiateSinglePayment(
                eq(YOLT_PROVIDER_SEPA_PIS_NAME), eq(initiateSepaPaymentRequestDTO), any(ClientToken.class), eq(SITE_ID), any(boolean.class)
        );
        verify(clientTokenVerificationService).verify(any(ClientToken.class), eq(authenticationMeansReference));
    }

    @Test
    public void shouldReturnNotFoundResponseWhenSendingPostRequestToProviderPaymentSepaPeriodicInitiateEndpointWithNonExistingProviderKey() throws Exception {
        // given
        InitiateSepaPaymentRequestDTO initiateSepaPaymentRequestDTO = new InitiateSepaPaymentRequestDTO(minimalValidPeriodicPaymentRequest(),
                "", new AuthenticationMeansReference(CLIENT_ID, UUID.randomUUID()), "http://redirect.url", null);
        when(providerSepaPaymentService.initiatePeriodicPayment(eq("FAKE_PROVIDER"), any(), any(), any(), any(boolean.class))).thenThrow(ProviderNotFoundException.class);
        // when
        MockHttpServletResponse response = callEndpoint("/FAKE_PROVIDER/payment/sepa/periodic/initiate", createGenericParameters(), initiateSepaPaymentRequestDTO);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    public void shouldReturnBadRequestResponseWhenSendingPostRequestToProviderPaymentSepaPeriodicInitiateEndpointWithInvalidRequestBody() throws Exception {
        // given
        ExternalInitiateSepaPaymentRequestDTO initiateSepaPaymentRequestDTO = new ExternalInitiateSepaPaymentRequestDTO(minimalValidSinglePaymentRequest(),
                null, null, null, null);

        // when
        MockHttpServletResponse response = callEndpoint("/YOLT_PROVIDER_SEPA_PIS/payment/sepa/periodic/initiate", initiateSepaPaymentRequestDTO);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    public void shouldReturnOkResponseWhenSendingPostRequestToProviderPaymentSepaPeriodicInitiateEndpointWithCorrectData() throws Exception {
        // given
        AuthenticationMeansReference authenticationMeansReference = new AuthenticationMeansReference(CLIENT_ID, UUID.randomUUID());
        InitiateSepaPaymentRequestDTO initiateSepaPeriodicPaymentRequestDTO = new InitiateSepaPaymentRequestDTO(minimalValidPeriodicPaymentRequest(),
                "", authenticationMeansReference, "http://redirect.url", null);
        // when
        MockHttpServletResponse response = callEndpoint("/YOLT_PROVIDER_SEPA_PIS/payment/sepa/periodic/initiate", createGenericParameters(), initiateSepaPeriodicPaymentRequestDTO);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        verify(providerSepaPaymentService).initiatePeriodicPayment(
                eq(YOLT_PROVIDER_SEPA_PIS_NAME), eq(initiateSepaPeriodicPaymentRequestDTO), any(ClientToken.class), eq(SITE_ID), any(boolean.class)
        );
        verify(clientTokenVerificationService).verify(any(ClientToken.class), eq(authenticationMeansReference));
    }

    @Test
    public void shouldReturnNotFoundResponseWhenSendingPostRequestToProviderPaymentSepaSingleSubmitEndpointWithInvalidProviderKey() throws Exception {
        // given
        SubmitSepaPaymentRequestDTO submitSepaPaymentRequestDTO = new SubmitSepaPaymentRequestDTO(null,
                new AuthenticationMeansReference(CLIENT_ID, UUID.randomUUID()), "redirectUrl", null);
        when(providerSepaPaymentService.submitSinglePayment(eq("FAKE_PROVIDER"), any(), any(), any(), any(boolean.class))).thenThrow(ProviderNotFoundException.class);
        // when
        MockHttpServletResponse response = callEndpoint("/FAKE_PROVIDER/payment/sepa/single/submit", createGenericParameters(), submitSepaPaymentRequestDTO);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    public void shouldReturnBadRequestResponseWhenSendingPostRequestToProviderPaymentSepaSingleSubmitEndpointWithInvalidRequestBody() throws Exception {
        // given
        SubmitSepaPaymentRequestDTO submitSepaPaymentRequestDTO = new SubmitSepaPaymentRequestDTO(null,
                null, null, null);

        // when
        MockHttpServletResponse response = callEndpoint("/YOLT_PROVIDER_SEPA_PIS/payment/sepa/single/submit", submitSepaPaymentRequestDTO);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    public void shouldReturnOkResponseWhenSendingPostRequestToProviderPaymentSepaSingleSubmitWithCorrectData() throws Exception {
        // given
        AuthenticationMeansReference authenticationMeansReference = new AuthenticationMeansReference(CLIENT_ID, UUID.randomUUID());
        SubmitSepaPaymentRequestDTO submitSepaPaymentRequestDTO = new SubmitSepaPaymentRequestDTO(null,
                authenticationMeansReference, "redirectUrl", null);
        // when
        MockHttpServletResponse response = callEndpoint("/YOLT_PROVIDER_SEPA_PIS/payment/sepa/single/submit", createGenericParameters(), submitSepaPaymentRequestDTO);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        verify(providerSepaPaymentService).submitSinglePayment(
                eq(YOLT_PROVIDER_SEPA_PIS_NAME), eq(submitSepaPaymentRequestDTO), any(ClientToken.class), eq(SITE_ID), any(boolean.class)
        );
        verify(clientTokenVerificationService).verify(any(ClientToken.class), eq(authenticationMeansReference));
    }

    @Test
    public void shouldReturnNotFoundResponseWhenSendingPostRequestToProviderPaymentSepaPeriodicSubmitEndpointWithInvalidProviderKey() throws Exception {
        // given
        SubmitSepaPaymentRequestDTO submitSepaPaymentRequestDTO = new SubmitSepaPaymentRequestDTO(null,
                new AuthenticationMeansReference(CLIENT_ID, UUID.randomUUID()), "redirectUrl", null);
        when(providerSepaPaymentService.submitPeriodicPayment(eq("FAKE_PROVIDER"), any(), any(), any(), any(boolean.class))).thenThrow(ProviderNotFoundException.class);
        // when
        MockHttpServletResponse response = callEndpoint("/FAKE_PROVIDER/payment/sepa/periodic/submit", createGenericParameters(), submitSepaPaymentRequestDTO);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    public void shouldReturnBadRequestResponseWhenSendingPostRequestToProviderPaymentSepaPeriodicSubmitEndpointWithInvalidRequestBody() throws Exception {
        // given
        SubmitSepaPaymentRequestDTO submitSepaPaymentRequestDTO = new SubmitSepaPaymentRequestDTO(null,
                null, null, null);

        // when
        MockHttpServletResponse response = callEndpoint("/YOLT_PROVIDER_SEPA_PIS/payment/sepa/periodic/submit", submitSepaPaymentRequestDTO);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    public void shouldReturnOkResponseWhenSendingPostRequestToProviderPaymentSepaPeriodicSubmitWithCorrectData() throws Exception {
        // given
        AuthenticationMeansReference authenticationMeansReference = new AuthenticationMeansReference(CLIENT_ID, UUID.randomUUID());
        SubmitSepaPaymentRequestDTO submitSepaPaymentRequestDTO = new SubmitSepaPaymentRequestDTO(null,
                authenticationMeansReference, "redirectUrl", null);
        // when
        MockHttpServletResponse response = callEndpoint("/YOLT_PROVIDER_SEPA_PIS/payment/sepa/periodic/submit", createGenericParameters(), submitSepaPaymentRequestDTO);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        verify(providerSepaPaymentService).submitPeriodicPayment(
                eq(YOLT_PROVIDER_SEPA_PIS_NAME), eq(submitSepaPaymentRequestDTO), any(ClientToken.class), eq(SITE_ID), any(boolean.class)
        );
        verify(clientTokenVerificationService).verify(any(ClientToken.class), eq(authenticationMeansReference));
    }

    @Test
    public void shouldReturnHttpClientErrorExceptionWhenSendingPayment() throws Exception {
        // given
        ExternalInitiateSepaPaymentRequestDTO initiateSepaPaymentRequestDTO = new ExternalInitiateSepaPaymentRequestDTO(minimalValidSinglePaymentRequest(),
                "", new AuthenticationMeansReference(CLIENT_ID, UUID.randomUUID()), "http://redirect.url", null);
        when(providerSepaPaymentService.initiateSinglePayment(eq("YOLT_PROVIDER_SEPA_PIS"), any(), any(), any(), any(boolean.class))).thenThrow(HttpClientErrorException.class);
        // when
        MockHttpServletResponse response = callEndpoint("/YOLT_PROVIDER_SEPA_PIS/payment/sepa/single/initiate", createGenericParameters(), initiateSepaPaymentRequestDTO);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        ErrorDTO errorDTO = objectMapper.readValue(response.getContentAsString(), ErrorDTO.class);
        assertThat(errorDTO.getCode()).isEqualTo("PR1000");
        assertThat(errorDTO.getMessage()).isEqualTo("Server error");
    }

    @Test
    public void shouldReturnHttpClientErrorExtendedExceptionWhenSendingPayment() throws Exception {
        // given
        ExternalInitiateSepaPaymentRequestDTO initiateSepaPaymentRequestDTO = new ExternalInitiateSepaPaymentRequestDTO(minimalValidSinglePaymentRequest(),
                "", new AuthenticationMeansReference(CLIENT_ID, UUID.randomUUID()), "http://redirect.url", null);
        when(providerSepaPaymentService.initiateSinglePayment(eq("YOLT_PROVIDER_SEPA_PIS"), any(), any(), any(), any(boolean.class))).thenThrow(new ProvidersCircuitBreakerException(new PaymentValidationException(new DetailedErrorInformation(FieldName.DYNAMICFIELDS_CREDITORAGENTNAME, "^.{1,31}$"))));
        ExtendedErrorDTO expectedErrorDTO = new ExtendedErrorDTO("PR1008", "Method argument not valid (request body validation error)", new DetailedErrorInformation(FieldName.DYNAMICFIELDS_CREDITORAGENTNAME, "^.{1,31}$"));
        // when
        MockHttpServletResponse response = callEndpoint("/YOLT_PROVIDER_SEPA_PIS/payment/sepa/single/initiate", createGenericParameters(), initiateSepaPaymentRequestDTO);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        ExtendedErrorDTO errorDTO = objectMapper.readValue(response.getContentAsString(), ExtendedErrorDTO.class);
        assertThat(errorDTO).usingRecursiveComparison()
                .isEqualTo(expectedErrorDTO);
    }

    @Test
    public void shouldReturnHttpClientErrorExceptionWhenSendingPeriodicPayment() throws Exception {
        // given
        InitiateSepaPaymentRequestDTO initiateSepaPaymentRequestDTO = new InitiateSepaPaymentRequestDTO(minimalValidPeriodicPaymentRequest(),
                "", new AuthenticationMeansReference(CLIENT_ID, UUID.randomUUID()), "http://redirect.url", null);
        when(providerSepaPaymentService.initiatePeriodicPayment(eq("YOLT_PROVIDER_SEPA_PIS"), any(), any(), any(), any(boolean.class))).thenThrow(HttpClientErrorException.class);
        // when
        MockHttpServletResponse response = callEndpoint("/YOLT_PROVIDER_SEPA_PIS/payment/sepa/periodic/initiate", createGenericParameters(), initiateSepaPaymentRequestDTO);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        ErrorDTO errorDTO = objectMapper.readValue(response.getContentAsString(), ErrorDTO.class);
        assertThat(errorDTO.getCode()).isEqualTo("PR1000");
        assertThat(errorDTO.getMessage()).isEqualTo("Server error");
    }

    private MultiValueMap<String, String> createGenericParameters() {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("forceExperimentalVersion", "false");
        return parameters;
    }

    private MockHttpServletResponse callEndpoint(String endpoint, MultiValueMap<String, String> parameters, Object content) throws Exception {
        MockHttpServletRequestBuilder post = post(endpoint);
        if (parameters != null) {
            post.params(parameters);
        }
        Consumer<JwtClaims> mutator = claims -> claims.setClaim(EXTRA_CLAIM_ISSUED_FOR, "client-gateway");
        ClientToken clientGatewayClientToken = testClientTokens.createClientToken(CLIENT_GROUP_ID, CLIENT_ID, mutator);
        return mockMvc.perform(post
                        .header("user-id", USER_ID)
                        .header("site_id", SITE_ID)
                        .header(ClientTokenConstants.CLIENT_TOKEN_HEADER_NAME, clientGatewayClientToken.getSerialized())
                        .content(objectMapper.writeValueAsString(content))
                        .contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
                .andReturn()
                .getResponse();
    }

    private MockHttpServletResponse callEndpoint(String endpoint, Object content) throws Exception {
        return callEndpoint(endpoint, null, content);
    }

    private static ExternalSepaInitiatePaymentRequestDTO minimalValidSinglePaymentRequest() {
        return ExternalSepaInitiatePaymentRequestDTO.builder()
                .creditorAccount(SepaAccountDTO.builder()
                        .iban("AB1234")
                        .build())
                .creditorName("fake creditor")
                .debtorAccount(SepaAccountDTO.builder()
                        .iban("CD5678")
                        .build())
                .endToEndIdentification("endToEndIdentification")
                .instructedAmount(SepaAmountDTO.builder()
                        .amount(BigDecimal.ONE)
                        .build())
                .remittanceInformationUnstructured("fake reference")
                .build();
    }

    private SepaInitiatePaymentRequestDTO minimalValidPeriodicPaymentRequest() {
        return SepaInitiatePaymentRequestDTO.builder()
                .creditorAccount(SepaAccountDTO.builder()
                        .iban("AB1234")
                        .build())
                .creditorName("fake creditor")
                .debtorAccount(SepaAccountDTO.builder()
                        .iban("CD5678")
                        .build())
                .endToEndIdentification("endToEndIdentification")
                .instructedAmount(SepaAmountDTO.builder()
                        .amount(BigDecimal.ONE)
                        .build())
                .remittanceInformationUnstructured("fake reference")
                .periodicPaymentInfo(SepaPeriodicPaymentInfo.builder()
                        .startDate(LocalDate.now(clock))
                        .endDate(LocalDate.now(clock))
                        .frequency(PeriodicPaymentFrequency.DAILY)
                        .executionRule(PeriodicPaymentExecutionRule.FOLLOWING)
                        .build())
                .build();
    }
}
