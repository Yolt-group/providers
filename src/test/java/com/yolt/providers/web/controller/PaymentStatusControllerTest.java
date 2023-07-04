package com.yolt.providers.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yolt.providers.web.ProviderApp;
import com.yolt.providers.web.configuration.TestConfiguration;
import com.yolt.providers.web.controller.dto.GetPaymentStatusRequestDTO;
import com.yolt.providers.web.errorhandling.ExtendedExceptionHandlingService;
import com.yolt.providers.web.exception.ProviderNotFoundException;
import com.yolt.providers.web.service.ClientTokenVerificationService;
import com.yolt.providers.web.service.ProviderSepaPaymentService;
import com.yolt.providers.web.service.ProviderUkDomesticPaymentService;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.clienttokens.constants.ClientTokenConstants;
import nl.ing.lovebird.clienttokens.test.TestClientTokens;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import org.jose4j.jwt.JwtClaims;
import org.junit.jupiter.api.BeforeEach;
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

@WebMvcTest(controllers = PaymentStatusController.class)
@ContextConfiguration(classes = {ProviderApp.class})
@Import({
        TestConfiguration.class,
        ExtendedExceptionHandlingService.class
})
class PaymentStatusControllerTest {

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

    @MockBean
    private ProviderUkDomesticPaymentService providerUkDomesticPaymentService;
    @MockBean
    private ProviderSepaPaymentService providerSepaPaymentService;
    @MockBean
    private ClientTokenVerificationService clientTokenVerificationService;
    @Autowired
    private TestClientTokens testClientTokens;
    private ClientToken siteManagementClientToken;

    @BeforeEach
    public void beforeEach() {
        Consumer<JwtClaims> mutator = claims -> claims.setClaim(EXTRA_CLAIM_ISSUED_FOR, "pis");
        siteManagementClientToken = testClientTokens.createClientToken(CLIENT_GROUP_ID, CLIENT_ID, mutator);
    }

    @Test
    public void shouldReturnNotFoundResponseWhenSendingPostRequestToProviderPaymentGetStatusEndpointWithInvalidProviderKey() throws Exception {
        // given
        GetPaymentStatusRequestDTO getPaymentStatusRequestDTO = new GetPaymentStatusRequestDTO(
                new AuthenticationMeansReference(CLIENT_ID, UUID.randomUUID()), "paymentId", null, null);
        when(providerUkDomesticPaymentService.getPaymentStatus(eq("FAKE_PROVIDER"), any(), any(), any(), any(boolean.class))).thenThrow(ProviderNotFoundException.class);
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("forceExperimentalVersion", "false");
        MockHttpServletResponse response = mockMvc.perform(post("/FAKE_PROVIDER/payments/UK/status")
                .params(parameters)
                .header("user-id", USER_ID)
                .header("site_id", SITE_ID)
                .header(ClientTokenConstants.CLIENT_TOKEN_HEADER_NAME, siteManagementClientToken.getSerialized())
                .content(objectMapper.writeValueAsString(getPaymentStatusRequestDTO))
                .contentType(MediaType.APPLICATION_JSON_VALUE))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    public void shouldReturnBadRequestResponseWhenSendingPostRequestToProviderPaymentGetStatusEndpointWithInvalidRequestBody() throws Exception {
        // given
        GetPaymentStatusRequestDTO getPaymentStatusRequestDTO =
                new GetPaymentStatusRequestDTO(null, null, null, null);

        // when
        MockHttpServletResponse response = mockMvc.perform(post("/TEST_IMPL_OPENBANKING/payments/UK/status")
                .header("user-id", USER_ID)
                .header("site_id", SITE_ID)
                .header(ClientTokenConstants.CLIENT_TOKEN_HEADER_NAME, siteManagementClientToken.getSerialized())
                .content(objectMapper.writeValueAsString(getPaymentStatusRequestDTO))
                .contentType(MediaType.APPLICATION_JSON_VALUE))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    public void shouldReturnOkResponseWhenSendingPostRequestToProviderPaymentGetStatusEndpointWithCorrectDataForUkPayment() throws Exception {
        // given
        AuthenticationMeansReference authenticationMeansReference = new AuthenticationMeansReference(CLIENT_ID, UUID.randomUUID());
        GetPaymentStatusRequestDTO getPaymentStatusRequestDTO = new GetPaymentStatusRequestDTO(
                authenticationMeansReference, "paymentId", null, null);
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("forceExperimentalVersion", "false");

        // when
        MockHttpServletResponse response = mockMvc.perform(post("/TEST_IMPL_OPENBANKING/payments/UK/status")
                .params(parameters)
                .header("user-id", USER_ID)
                .header("site_id", SITE_ID)
                .header(ClientTokenConstants.CLIENT_TOKEN_HEADER_NAME, siteManagementClientToken.getSerialized())
                .content(objectMapper.writeValueAsString(getPaymentStatusRequestDTO))
                .contentType(MediaType.APPLICATION_JSON_VALUE))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        verify(providerUkDomesticPaymentService).getPaymentStatus(
                eq(TEST_IMPL_OPENBANKING_NAME), eq(getPaymentStatusRequestDTO), any(ClientToken.class),
                eq(SITE_ID), any(boolean.class)
        );
        verify(clientTokenVerificationService).verify(any(ClientToken.class), eq(authenticationMeansReference));
    }

    @Test
    public void shouldReturnOkResponseWhenSendingPostRequestToProviderPaymentGetStatusEndpointWithCorrectDataForSepaPayment() throws Exception {
        // given
        AuthenticationMeansReference authenticationMeansReference = new AuthenticationMeansReference(CLIENT_ID, UUID.randomUUID());
        GetPaymentStatusRequestDTO getPaymentStatusRequestDTO = new GetPaymentStatusRequestDTO(
                authenticationMeansReference, "paymentId", null, null);
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("forceExperimentalVersion", "false");
        // when
        MockHttpServletResponse response = mockMvc.perform(post("/TEST_IMPL_OPENBANKING/payments/SEPA/status")
                .params(parameters)
                .header("user-id", USER_ID)
                .header("site_id", SITE_ID)
                .header(ClientTokenConstants.CLIENT_TOKEN_HEADER_NAME, siteManagementClientToken.getSerialized())
                .content(objectMapper.writeValueAsString(getPaymentStatusRequestDTO))
                .contentType(MediaType.APPLICATION_JSON_VALUE))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        verify(providerSepaPaymentService).getPaymentStatus(
                eq(TEST_IMPL_OPENBANKING_NAME), eq(getPaymentStatusRequestDTO), any(ClientToken.class),
                eq(SITE_ID), any(boolean.class)
        );
        verify(clientTokenVerificationService).verify(any(ClientToken.class), eq(authenticationMeansReference));
    }
}