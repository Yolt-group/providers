package com.yolt.providers.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yolt.providers.common.pis.common.PeriodicPaymentFrequency;
import com.yolt.providers.common.pis.common.UkPeriodicPaymentInfo;
import com.yolt.providers.common.pis.ukdomestic.AccountIdentifierScheme;
import com.yolt.providers.common.pis.ukdomestic.InitiateUkDomesticPeriodicPaymentRequestDTO;
import com.yolt.providers.common.pis.ukdomestic.UkAccountDTO;
import com.yolt.providers.web.ProviderApp;
import com.yolt.providers.web.configuration.TestConfiguration;
import com.yolt.providers.web.controller.dto.ExternalInitiateUkDomesticScheduledPaymentRequestDTO;
import com.yolt.providers.web.controller.dto.ExternalInitiateUkScheduledPaymentRequestDTO;
import com.yolt.providers.web.controller.dto.InitiateUkPaymentRequestDTO;
import com.yolt.providers.web.controller.dto.InitiateUkPeriodicPaymentRequestDTO;
import com.yolt.providers.web.errorhandling.ExtendedExceptionHandlingService;
import com.yolt.providers.web.exception.ProviderNotFoundException;
import com.yolt.providers.web.service.ClientTokenVerificationService;
import com.yolt.providers.web.service.ProviderUkDomesticPaymentService;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.clienttokens.constants.ClientTokenConstants;
import nl.ing.lovebird.clienttokens.test.TestClientTokens;
import nl.ing.lovebird.errorhandling.ErrorDTO;
import nl.ing.lovebird.extendeddata.common.CurrencyCode;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

@WebMvcTest(controllers = ProviderUkDomesticPaymentController.class)
@ContextConfiguration(classes = {ProviderApp.class})
@Import({
        TestConfiguration.class,
        ExtendedExceptionHandlingService.class
})
class ProviderUkDomesticPaymentControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CLIENT_GROUP_ID = UUID.randomUUID();
    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID SITE_ID = UUID.randomUUID();
    private static final String TEST_IMPL_OPENBANKING_NAME = "TEST_IMPL_OPENBANKING";

    @Autowired
    @Qualifier(JACKSON_OBJECT_MAPPER)
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestClientTokens testClientTokens;

    @Mock
    private Clock clock;

    @MockBean
    private ProviderUkDomesticPaymentService providerUkDomesticPaymentService;
    @MockBean
    private ClientTokenVerificationService clientTokenVerificationService;

    @BeforeEach
    public void setup() {
        when(clock.instant()).thenReturn(Instant.now());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    public void shouldReturnNotFoundResponseWhenSendingPostRequestToProviderPaymentUkDomesticInitiateEndpointWithNonExistingProviderKey() throws Exception {
        // given
        InitiateUkPaymentRequestDTO initiateUkPaymentRequestDTO = new InitiateUkPaymentRequestDTO(minimalValidSinglePaymentRequest(),
                "", new AuthenticationMeansReference(CLIENT_ID, UUID.randomUUID()), "http://redirect.url", null);
        when(providerUkDomesticPaymentService.initiateSinglePayment(eq("FAKE_PROVIDER"), any(), any(), any(), any(boolean.class))).thenThrow(ProviderNotFoundException.class);
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("forceExperimentalVersion", "false");
        // when
        MockHttpServletResponse response = callEndpoint("/FAKE_PROVIDER/payments/single/uk/initiate", parameters, initiateUkPaymentRequestDTO);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    public void shouldReturnBadRequestResponseWhenSendingPostRequestToProviderPaymentUkDomesticInitiateEndpointWithInvalidRequestBody() throws Exception {
        // given
        InitiateUkPaymentRequestDTO initiateUkPaymentRequestDTO = new InitiateUkPaymentRequestDTO(minimalValidSinglePaymentRequest(),
                null, null, null, null);

        // when
        MockHttpServletResponse response = callEndpoint("/YOLT_PROVIDER_SEPA_PIS/payments/single/uk/initiate", initiateUkPaymentRequestDTO);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    public void shouldReturnOkResponseWhenSendingPostRequestToProviderPaymentUkDomesticInitiateEndpointWithCorrectData() throws Exception {
        // given
        AuthenticationMeansReference authenticationMeansReference = new AuthenticationMeansReference(CLIENT_ID, UUID.randomUUID());
        ExternalInitiateUkScheduledPaymentRequestDTO initiateUkPaymentRequestDTO = new ExternalInitiateUkScheduledPaymentRequestDTO(minimalValidSinglePaymentRequest(),
                "", authenticationMeansReference, "http://redirect.url", null);
        // when
        MockHttpServletResponse response = callEndpoint("/TEST_IMPL_OPENBANKING/payments/single/uk/initiate", createGenericParameters(), initiateUkPaymentRequestDTO);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        verify(providerUkDomesticPaymentService).initiateSinglePayment(
                eq(TEST_IMPL_OPENBANKING_NAME), eq(initiateUkPaymentRequestDTO), any(ClientToken.class), eq(SITE_ID), any(boolean.class)
        );
        verify(clientTokenVerificationService).verify(any(ClientToken.class), eq(authenticationMeansReference));
    }

    @Test
    public void shouldReturnOkResponseWhenSendingPostRequestToProviderPaymentUkDomesticInitiatePeriodicEndpointWithCorrectData() throws Exception {
        // given
        AuthenticationMeansReference authenticationMeansReference = new AuthenticationMeansReference(CLIENT_ID, UUID.randomUUID());
        InitiateUkPeriodicPaymentRequestDTO initiateUkPeriodicPaymentRequestDTO = new InitiateUkPeriodicPaymentRequestDTO(minimalValidPeriodicPaymentRequest(),
                "", authenticationMeansReference, "http://redirect.url", null);
        // when
        MockHttpServletResponse response = callEndpoint("/TEST_IMPL_OPENBANKING/payments/periodic/uk/initiate", createGenericParameters(), initiateUkPeriodicPaymentRequestDTO);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        verify(providerUkDomesticPaymentService).initiatePeriodicPayment(
                eq(TEST_IMPL_OPENBANKING_NAME), eq(initiateUkPeriodicPaymentRequestDTO), any(ClientToken.class), eq(SITE_ID), any(boolean.class)
        );
        verify(clientTokenVerificationService).verify(any(ClientToken.class), eq(authenticationMeansReference));
    }

    @Test
    public void shouldReturnNotFoundResponseWhenSendingPostRequestToProviderPeriodicPaymentUkDomesticInitiateEndpointWithNonExistingProviderKey() throws Exception {
        // given
        InitiateUkPeriodicPaymentRequestDTO initiateUkPeriodicPaymentRequestDTO = new InitiateUkPeriodicPaymentRequestDTO(minimalValidPeriodicPaymentRequest(),
                "", new AuthenticationMeansReference(CLIENT_ID, UUID.randomUUID()), "http://redirect.url", null);
        when(providerUkDomesticPaymentService.initiatePeriodicPayment(eq("FAKE_PROVIDER"), any(), any(), any(), any(boolean.class))).thenThrow(ProviderNotFoundException.class);
        // when
        MockHttpServletResponse response = callEndpoint("/FAKE_PROVIDER/payments/periodic/uk/initiate", createGenericParameters(), initiateUkPeriodicPaymentRequestDTO);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    public void shouldReturnBadRequestResponseWhenSendingPostRequestToProviderPeriodicPaymentUkDomesticInitiateEndpointWithInvalidRequestBody() throws Exception {
        // given
        InitiateUkPeriodicPaymentRequestDTO initiateUkPeriodicPaymentRequestDTO = new InitiateUkPeriodicPaymentRequestDTO(minimalValidPeriodicPaymentRequest(),
                null, null, null, null);

        // when
        MockHttpServletResponse response = callEndpoint("/YOLT_PROVIDER_SEPA_PIS/payments/periodic/uk/initiate", initiateUkPeriodicPaymentRequestDTO);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    public void shouldReturnHttpClientErrorExceptionWhenSendingPayment() throws Exception {
        // given
        InitiateUkPaymentRequestDTO initiateUkPaymentRequestDTO = new InitiateUkPaymentRequestDTO(minimalValidSinglePaymentRequest(),
                "", new AuthenticationMeansReference(CLIENT_ID, UUID.randomUUID()), "http://redirect.url", null);
        when(providerUkDomesticPaymentService.initiateSinglePayment(eq(TEST_IMPL_OPENBANKING_NAME), any(), any(), any(), any(boolean.class))).thenThrow(HttpClientErrorException.class);
        // when
        MockHttpServletResponse response = callEndpoint("/TEST_IMPL_OPENBANKING/payments/single/uk/initiate", createGenericParameters(), initiateUkPaymentRequestDTO);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        ErrorDTO errorDTO = objectMapper.readValue(response.getContentAsString(), ErrorDTO.class);
        assertThat(errorDTO.getCode()).isEqualTo("PR1000");
        assertThat(errorDTO.getMessage()).isEqualTo("Server error");
    }

    @Test
    public void shouldReturnHttpClientErrorExceptionWhenSendingPeriodicPayment() throws Exception {
        // given
        InitiateUkPeriodicPaymentRequestDTO initiateUkPeriodicPaymentRequestDTO = new InitiateUkPeriodicPaymentRequestDTO(minimalValidPeriodicPaymentRequest(),
                "", new AuthenticationMeansReference(CLIENT_ID, UUID.randomUUID()), "http://redirect.url", null);
        when(providerUkDomesticPaymentService.initiatePeriodicPayment(eq(TEST_IMPL_OPENBANKING_NAME), any(), any(), any(), any(boolean.class))).thenThrow(HttpClientErrorException.class);
        // when
        MockHttpServletResponse response = callEndpoint("/TEST_IMPL_OPENBANKING/payments/periodic/uk/initiate", createGenericParameters(), initiateUkPeriodicPaymentRequestDTO);

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

    private ExternalInitiateUkDomesticScheduledPaymentRequestDTO minimalValidSinglePaymentRequest() {
        UkAccountDTO debtor = new UkAccountDTO("someSortCode",
                AccountIdentifierScheme.SORTCODEACCOUNTNUMBER,
                "Henry Moneybags",
                null);
        UkAccountDTO creditor = new UkAccountDTO("someSortCode",
                AccountIdentifierScheme.SORTCODEACCOUNTNUMBER,
                "Sean Emptypocket",
                null);
        return new ExternalInitiateUkDomesticScheduledPaymentRequestDTO(
                "endToEndIdentification",
                CurrencyCode.GBP.name(),
                BigDecimal.ONE,
                creditor,
                debtor,
                "fake description",
                null,
                LocalDate.now(clock)
        );
    }

    private InitiateUkDomesticPeriodicPaymentRequestDTO minimalValidPeriodicPaymentRequest() {
        UkAccountDTO debtor = new UkAccountDTO("someSortCode",
                AccountIdentifierScheme.SORTCODEACCOUNTNUMBER,
                "Henry Moneybags",
                null);
        UkAccountDTO creditor = new UkAccountDTO("someSortCode",
                AccountIdentifierScheme.SORTCODEACCOUNTNUMBER,
                "Sean Emptypocket",
                null);
        return new InitiateUkDomesticPeriodicPaymentRequestDTO(
                "endToEndIdentification",
                CurrencyCode.GBP.name(),
                BigDecimal.ONE,
                creditor,
                debtor,
                "fake description",
                null,
                new UkPeriodicPaymentInfo(
                        LocalDate.now(clock),
                        LocalDate.now(clock),
                        PeriodicPaymentFrequency.DAILY));
    }
}