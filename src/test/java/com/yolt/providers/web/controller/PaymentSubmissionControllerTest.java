package com.yolt.providers.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yolt.providers.common.pis.common.SubmitPaymentRequestDTO;
import com.yolt.providers.web.ProviderApp;
import com.yolt.providers.web.configuration.TestConfiguration;
import com.yolt.providers.web.errorhandling.ExtendedExceptionHandlingService;
import com.yolt.providers.web.exception.ProviderNotFoundException;
import com.yolt.providers.web.service.ClientTokenVerificationService;
import com.yolt.providers.web.service.ProviderUkDomesticPaymentService;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.clienttokens.constants.ClientTokenConstants;
import nl.ing.lovebird.clienttokens.test.TestClientTokens;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import org.jose4j.jwt.JwtClaims;
import org.junit.jupiter.api.Test;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

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

@WebMvcTest(controllers = PaymentSubmissionController.class)
@ContextConfiguration(classes = {ProviderApp.class})
@Import({
        TestConfiguration.class,
        ExtendedExceptionHandlingService.class
})
class PaymentSubmissionControllerTest {

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

    @MockBean
    private ProviderUkDomesticPaymentService providerUkDomesticPaymentService;
    @MockBean
    private ClientTokenVerificationService clientTokenVerificationService;

    @Test
    public void shouldReturnNotFoundResponseWhenSendingPostRequestToProviderPaymentUkDomesticSubmitEndpointWithInvalidProviderKey() throws Exception {
        // given
        SubmitPaymentRequestDTO submitPaymentRequestDTO = new SubmitPaymentRequestDTO("random state",
                new AuthenticationMeansReference(CLIENT_ID, UUID.randomUUID()), "redirectUrl", null);
        when(providerUkDomesticPaymentService.submitSinglePayment(eq("FAKE_PROVIDER"), any(), any(), any(), any(boolean.class))).thenThrow(ProviderNotFoundException.class);
        // when
        MockHttpServletResponse response = callEndpoint("/FAKE_PROVIDER/payments/submit", createGenericParameters(), submitPaymentRequestDTO);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    public void shouldReturnBadRequestResponseWhenSendingPostRequestToProviderPaymentUkDomesticSubmitEndpointWithInvalidRequestBody() throws Exception {
        // given
        SubmitPaymentRequestDTO submitPaymentRequestDTO = new SubmitPaymentRequestDTO("random state",
                null, null, null);

        // when
        MockHttpServletResponse response = callEndpoint("/TEST_IMPL_OPENBANKING/payments/submit", submitPaymentRequestDTO);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    public void shouldReturnOkResponseWhenSendingPostRequestToProviderPaymentUkDomesticSubmitWithCorrectData() throws Exception {
        // given
        AuthenticationMeansReference authenticationMeansReference = new AuthenticationMeansReference(CLIENT_ID, UUID.randomUUID());
        SubmitPaymentRequestDTO submitPaymentRequestDTO = new SubmitPaymentRequestDTO("random state",
                authenticationMeansReference, "redirectUrl", null);

        // when
        MockHttpServletResponse response = callEndpoint("/TEST_IMPL_OPENBANKING/payments/submit", createGenericParameters(), submitPaymentRequestDTO);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        verify(providerUkDomesticPaymentService).submitSinglePayment(
                eq(TEST_IMPL_OPENBANKING_NAME), eq(submitPaymentRequestDTO), any(ClientToken.class), eq(SITE_ID), any(boolean.class)
        );
        verify(clientTokenVerificationService).verify(any(ClientToken.class), eq(authenticationMeansReference));
    }

    @Test
    public void shouldReturnNotFoundResponseWhenSendingPostRequestToProviderPaymentUkDomesticPeriodicPaymentSubmitEndpointWithInvalidProviderKey() throws Exception {
        // given
        SubmitPaymentRequestDTO submitPaymentRequestDTO = new SubmitPaymentRequestDTO("random state",
                new AuthenticationMeansReference(CLIENT_ID, UUID.randomUUID()), "redirectUrl", null);
        when(providerUkDomesticPaymentService.submitPeriodicPayment(eq("FAKE_PROVIDER"), any(), any(), any(), any(boolean.class))).thenThrow(ProviderNotFoundException.class);
        // when
        MockHttpServletResponse response = callEndpoint("/FAKE_PROVIDER/payments/periodic/submit", createGenericParameters(), submitPaymentRequestDTO);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    public void shouldReturnBadRequestResponseWhenSendingPostRequestToProviderPaymentUkDomesticPeriodicPaymentSubmitEndpointWithInvalidRequestBody() throws Exception {
        // given
        SubmitPaymentRequestDTO submitPaymentRequestDTO = new SubmitPaymentRequestDTO("random state",
                null, null, null);

        // when
        MockHttpServletResponse response = callEndpoint("/TEST_IMPL_OPENBANKING/payments/periodic/submit", submitPaymentRequestDTO);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    public void shouldReturnOkResponseWhenSendingPostRequestToProviderPaymentUkDomesticPeriodicPaymentSubmitWithCorrectData() throws Exception {
        // given
        AuthenticationMeansReference authenticationMeansReference = new AuthenticationMeansReference(CLIENT_ID, UUID.randomUUID());
        SubmitPaymentRequestDTO submitPaymentRequestDTO = new SubmitPaymentRequestDTO("random state",
                authenticationMeansReference, "redirectUrl", null);
        // when
        MockHttpServletResponse response = callEndpoint("/TEST_IMPL_OPENBANKING/payments/periodic/submit", createGenericParameters(), submitPaymentRequestDTO);

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        verify(providerUkDomesticPaymentService).submitPeriodicPayment(
                eq(TEST_IMPL_OPENBANKING_NAME), eq(submitPaymentRequestDTO), any(ClientToken.class), eq(SITE_ID), any(boolean.class)
        );
        verify(clientTokenVerificationService).verify(any(ClientToken.class), eq(authenticationMeansReference));
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
        ResultActions respBuilder = mockMvc.perform(post
                        .header("user-id", USER_ID)
                        .header("site_id", SITE_ID)
                        .header(ClientTokenConstants.CLIENT_TOKEN_HEADER_NAME, clientGatewayClientToken.getSerialized())
                        .content(objectMapper.writeValueAsString(content))
                        .contentType(MediaType.APPLICATION_JSON_VALUE));
        MvcResult result = respBuilder.andReturn();
        MockHttpServletResponse resp = result.getResponse();
        return resp;
    }

    private MockHttpServletResponse callEndpoint(String endpoint, Object content) throws Exception {
        return callEndpoint(endpoint, null, content);
    }
}