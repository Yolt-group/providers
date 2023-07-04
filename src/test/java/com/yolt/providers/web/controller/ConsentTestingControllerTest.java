package com.yolt.providers.web.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yolt.providers.web.ProviderApp;
import com.yolt.providers.web.configuration.TestConfiguration;
import com.yolt.providers.web.controller.dto.InvokeConsentTestingDTO;
import com.yolt.providers.web.errorhandling.ExtendedExceptionHandlingService;
import com.yolt.providers.web.service.consenttesting.ConsentTestingService;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.clienttokens.test.TestClientTokens;
import nl.ing.lovebird.providerdomain.ServiceType;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import org.jose4j.jwt.JwtClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.function.Consumer;

import static nl.ing.lovebird.clienttokens.constants.ClientTokenConstants.CLIENT_TOKEN_HEADER_NAME;
import static nl.ing.lovebird.clienttokens.constants.ClientTokenConstants.EXTRA_CLAIM_ISSUED_FOR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ConsentTestingController.class)
@ContextConfiguration(classes = {ProviderApp.class})
@Import({
        TestConfiguration.class,
        ExtendedExceptionHandlingService.class
})
class ConsentTestingControllerTest {

    private static final String INVOKE_CONSENT_TESTS_URL_TEMPLATE = "/clients/{clientId}/redirect-urls/{clientRedirectUrlId}/invoke-consent-tests";
    private static final String INVOKE_NEW_CONSENT_TESTS_URL_TEMPLATE = "/clients/invoke-consent-tests";
    private static final String REDIRECT_URL = "https://redirecturl.com";
    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID CLIENT_GROUP_ID = UUID.randomUUID();
    private static final UUID CLIENT_REDIRECT_ID = UUID.randomUUID();
    private static final AuthenticationMeansReference AUTH_MEANS_REFERENCE = new AuthenticationMeansReference(CLIENT_ID, null, CLIENT_REDIRECT_ID);

    @MockBean
    ConsentTestingService consentTestingService;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TestClientTokens testClientTokens;

    ClientToken siteManagementClientToken;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        Consumer<JwtClaims> mutator = claims -> claims.setClaim(EXTRA_CLAIM_ISSUED_FOR, "site-management");
        siteManagementClientToken = testClientTokens.createClientToken(CLIENT_GROUP_ID, CLIENT_ID, mutator);
    }

    @Test
    void shouldInvokeConsentTestingForAisWhenServiceTypeIsNotProvided() throws Exception {
        // given / when
        mockMvc.perform(post(INVOKE_NEW_CONSENT_TESTS_URL_TEMPLATE)
                        .header(CLIENT_TOKEN_HEADER_NAME, siteManagementClientToken.getSerialized())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createInvokeConsentTestingBody()))
                .andExpect(status().isOk());

        // then
        verify(consentTestingService).invokeConsentTesting(eq(AUTH_MEANS_REFERENCE), any(ClientToken.class), eq(ServiceType.AIS), eq(REDIRECT_URL));
    }

    @Test
    void shouldInvokeConsentTestingForAisWhenServiceTypeAisIsProvided() throws Exception {
        // given / when
        mockMvc.perform(post(INVOKE_NEW_CONSENT_TESTS_URL_TEMPLATE)
                        .queryParam("serviceType", "AIS")
                        .header(CLIENT_TOKEN_HEADER_NAME, siteManagementClientToken.getSerialized())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createInvokeConsentTestingBody()))
                .andExpect(status().isOk());

        // then
        verify(consentTestingService).invokeConsentTesting(eq(AUTH_MEANS_REFERENCE), any(ClientToken.class), eq(ServiceType.AIS), eq(REDIRECT_URL));
    }

    @Test
    void shouldInvokeConsentTestingForPisWhenServiceTypePisIsProvided() throws Exception {
        // given / when
        mockMvc.perform(post(INVOKE_NEW_CONSENT_TESTS_URL_TEMPLATE)
                        .queryParam("serviceType", "PIS")
                        .header(CLIENT_TOKEN_HEADER_NAME, siteManagementClientToken.getSerialized())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createInvokeConsentTestingBody()))
                .andExpect(status().isOk());

        // then
        verify(consentTestingService).invokeConsentTesting(eq(AUTH_MEANS_REFERENCE), any(ClientToken.class), eq(ServiceType.PIS), eq(REDIRECT_URL));
    }

    private String createInvokeConsentTestingBody() throws JsonProcessingException {
        var dto = new InvokeConsentTestingDTO(REDIRECT_URL, AUTH_MEANS_REFERENCE);
        return objectMapper.writeValueAsString(dto);
    }
}
