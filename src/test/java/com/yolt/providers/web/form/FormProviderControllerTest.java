package com.yolt.providers.web.form;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yolt.providers.common.ais.form.EncryptionDetails;
import com.yolt.providers.common.ais.form.FormCreateNewUserResponse;
import com.yolt.providers.common.ais.form.LoginFormResponse;
import com.yolt.providers.common.exception.AuthenticationMeanValidationException;
import com.yolt.providers.common.exception.ExternalUserSiteDoesNotExistException;
import com.yolt.providers.web.ProviderApp;
import com.yolt.providers.web.configuration.TestConfiguration;
import com.yolt.providers.web.errorhandling.ExtendedExceptionHandlingService;
import com.yolt.providers.web.form.dto.EncryptionDetailsDTO;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.clienttokens.ClientUserToken;
import nl.ing.lovebird.clienttokens.test.TestClientTokens;
import nl.ing.lovebird.clienttokens.verification.ClientIdVerificationService;
import nl.ing.lovebird.errorhandling.ErrorDTO;
import nl.ing.lovebird.providershared.AccessMeansDTO;
import nl.ing.lovebird.providershared.form.FormCreateNewUserResponseDTO;
import nl.ing.lovebird.providershared.form.LoginFormResponseDTO;
import org.jose4j.jwt.JwtClaims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.UUID;
import java.util.function.Consumer;

import static nl.ing.lovebird.clienttokens.constants.ClientTokenConstants.CLIENT_TOKEN_HEADER_NAME;
import static nl.ing.lovebird.clienttokens.constants.ClientTokenConstants.EXTRA_CLAIM_ISSUED_FOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@WebMvcTest(controllers = FormProviderController.class)
@ContextConfiguration(classes = {ProviderApp.class})
@ActiveProfiles("test")
@Import({
        TestConfiguration.class,
        ExtendedExceptionHandlingService.class
})
class FormProviderControllerTest {

    private static final Date THOUSAND_YEARS_IN_THE_FUTURE = Date.from(ZonedDateTime.now(ZoneOffset.UTC).plusYears(1000).toInstant());
    private static final UUID CLIENT_GROUP_ID = UUID.randomUUID();
    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SITE_ID = UUID.randomUUID();
    private static final UUID USER_SITE_ID = UUID.randomUUID();
    private static final String BUDGET_INSIGHT_NAME = "BUDGET_INSIGHT";

    @MockBean
    private FormProviderService formProviderService;
    @MockBean
    private ClientIdVerificationService clientIdVerificationService;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestClientTokens testClientTokens;

    private final ObjectMapper objectMapper = new TestConfiguration().jacksonObjectMapper();

    private ClientToken clientGatewayClientToken;
    private ClientUserToken clientGatewayClientUserToken;

    @BeforeEach
    void beforeEach() {
        Consumer<JwtClaims> mutator = claims -> claims.setClaim(EXTRA_CLAIM_ISSUED_FOR, "client-gateway");
        clientGatewayClientUserToken = testClientTokens.createClientUserToken(CLIENT_GROUP_ID, CLIENT_ID, USER_ID, mutator);
        clientGatewayClientToken = testClientTokens.createClientUserToken(CLIENT_GROUP_ID, CLIENT_ID, USER_ID, mutator);
    }

    @AfterEach
    void afterEach() {
        reset(formProviderService);
    }

    @Test
    void shouldReturnCorrectResponseWhenSendingPostRequestToFormProviderAccessMeansRefreshEndpointWithCorrectData() throws Exception {
        // given
        String content = String.format("{ \"accessMeansDTO\": { \"userId\": \"%s\", \"accessMeansBlob\": \"access-means\", \"updated\": \"%s\", \"expireTime\": \"%s\" }, \"clientId\": \"%s\" }",
                USER_ID,
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(new Date()),
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(THOUSAND_YEARS_IN_THE_FUTURE),
                CLIENT_ID);

        // when
        MockHttpServletResponse response = mockMvc.perform(post("/form/BUDGET_INSIGHT/access-means/refresh")
                        .content(content)
                        .contentType(APPLICATION_JSON)
                        .header(CLIENT_TOKEN_HEADER_NAME, clientGatewayClientUserToken.getSerialized()))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        verify(formProviderService).refreshAccessMeansForForm(eq(BUDGET_INSIGHT_NAME), isNotNull(), any());
        verify(clientIdVerificationService).verify(isNotNull(), eq(CLIENT_ID));
        verifyNoMoreInteractions(formProviderService);
    }

    @Test
    void shouldReturnCorrectResponseWhenSendingPostRequestToFormProviderDeleteUserSiteEndpointWithCorrectData() throws Exception {
        // given
        String content = String.format("{ \"userId\": \"%s\", \"clientId\": \"%s\", \"accessMeans\": \"access-means\", \"userSiteExternalId\": \"42\" }",
                USER_ID, CLIENT_ID);

        // when
        MockHttpServletResponse response = mockMvc.perform(post("/form/BUDGET_INSIGHT/delete-user-site")
                        .content(content)
                        .contentType(APPLICATION_JSON)
                        .header(CLIENT_TOKEN_HEADER_NAME, clientGatewayClientToken.getSerialized()))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.ACCEPTED.value());
        verify(formProviderService).deleteUserSite(eq(BUDGET_INSIGHT_NAME), isNotNull(), any());
        verify(clientIdVerificationService).verify(isNotNull(), eq(CLIENT_ID));
    }

    @Test
    void shouldReturnCorrectResponseWhenSendingPostRequestToFormProviderDeleteUserEndpointWithCorrectData() throws Exception {
        // given
        String content = String.format("{ \"clientId\": \"%s\", \"accessMeans\": \"access-means\" }", CLIENT_ID);

        // when
        MockHttpServletResponse response = mockMvc.perform(post("/form/BUDGET_INSIGHT/delete-user")
                        .content(content)
                        .contentType(APPLICATION_JSON)
                        .header(CLIENT_TOKEN_HEADER_NAME, clientGatewayClientToken.getSerialized()))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.ACCEPTED.value());
        verify(formProviderService).deleteUser(eq(BUDGET_INSIGHT_NAME), isNotNull(), any());
        verify(clientIdVerificationService).verify(isNotNull(), eq(CLIENT_ID));
    }

    @Test
    void shouldReturnLoginFormResponseWhenSendingPostRequestToFormProviderFetchLoginFormEndpointWithCorrectData() throws Exception {
        // given
        when(formProviderService.fetchLoginForm(eq(BUDGET_INSIGHT_NAME), isNotNull(), any())).thenReturn(new LoginFormResponse("providerForm", null));
        String content = String.format("{ \"clientId\": \"%s\", \"externalSiteId\": \"external-site-id\" }", CLIENT_ID);

        // when
        MockHttpServletResponse response = mockMvc.perform(post("/form/BUDGET_INSIGHT/fetch-login-form")
                        .content(content)
                        .contentType(APPLICATION_JSON)
                        .header(CLIENT_TOKEN_HEADER_NAME, clientGatewayClientToken.getSerialized()))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        LoginFormResponseDTO result = objectMapper.readValue(response.getContentAsString(), LoginFormResponseDTO.class);
        assertThat(result.getProviderForm()).isEqualTo("providerForm");
        verify(formProviderService).fetchLoginForm(eq(BUDGET_INSIGHT_NAME), isNotNull(), any());
        verify(clientIdVerificationService).verify(isNotNull(), eq(CLIENT_ID));
    }

    @Test
    void shouldReturnEncryptionDetailsWhenSendingPostRequestToFormProviderGetEncryptionDetailsEndpointWithCorrectData() throws Exception {
        // given
        when(formProviderService.getEncryptionDetails(eq(BUDGET_INSIGHT_NAME), isNotNull(), any())).thenReturn(EncryptionDetails.of(new EncryptionDetails.JWEDetails("alg", "method", "pubKey", null)));
        String content = String.format("{ \"clientId\": \"%s\" }", CLIENT_ID);

        // when
        MockHttpServletResponse response = mockMvc.perform(post("/form/BUDGET_INSIGHT/get-encryption-details")
                        .content(content)
                        .contentType(APPLICATION_JSON)
                        .header(CLIENT_TOKEN_HEADER_NAME, clientGatewayClientToken.getSerialized()))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        EncryptionDetailsDTO result = objectMapper.readValue(response.getContentAsString(), EncryptionDetailsDTO.class);
        assertThat(result.getJweDetails().getAlgorithm()).isEqualTo("alg");
        assertThat(result.getJweDetails().getEncryptionMethod()).isEqualTo("method");
        assertThat(result.getJweDetails().getPublicJSONWebKey()).isEqualTo("pubKey");
        verify(formProviderService).getEncryptionDetails(eq(BUDGET_INSIGHT_NAME), isNotNull(), any());
        verify(clientIdVerificationService).verify(isNotNull(), eq(CLIENT_ID));
    }

    @Test
    void shouldReturnFormCreateNewUserResponseWhenSendingPostRequestToFormProviderCreateNewUserEndpointWithCorrectData() throws Exception {
        // given
        when(formProviderService.createNewUser(eq(BUDGET_INSIGHT_NAME), isNotNull(), any())).thenReturn(new FormCreateNewUserResponse(new AccessMeansDTO(UUID.randomUUID(), "something", new Date(), new Date()), "extUserId"));
        String content = String.format("{ \"userId\": \"%s\", \"clientId\": \"%s\", \"siteId\": \"%s\", \"userAlreadyExists\": \"false\", \"isTestUser\": \"false\" }",
                USER_ID, CLIENT_ID, SITE_ID);

        // when
        MockHttpServletResponse response = mockMvc.perform(post("/form/BUDGET_INSIGHT/create-new-user")
                        .content(content)
                        .contentType(APPLICATION_JSON)
                        .header(CLIENT_TOKEN_HEADER_NAME, clientGatewayClientToken.getSerialized()))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        FormCreateNewUserResponseDTO result = objectMapper.readValue(response.getContentAsString(), FormCreateNewUserResponseDTO.class);
        assertThat(result.getExternalUserId()).isEqualTo("extUserId");
        assertThat(result.getAccessMeans().getAccessMeans()).isEqualTo("something");
        verify(formProviderService).createNewUser(eq(BUDGET_INSIGHT_NAME), isNotNull(), any());
        verify(clientIdVerificationService).verify(isNotNull(), eq(CLIENT_ID));
    }

    @Test
    void shouldReturnCorrectResponseWhenSendingPostRequestToFormProviderUpdateExternalUserSiteEndpointWithCorrectData() throws Exception {
        // given
        String content = String.format("{ \"externalSiteId\": \"42\", " +
                        "\"formUserSite\": { \"userId\": \"%s\", \"userSiteId\": \"%s\", \"externalId\": \"42\" }, " +
                        "\"accessMeans\": \"access-means\", " +
                        "\"transactionsFetchStartTime\": \"%s\", " +
                        "\"filledInUserSiteFormValues\": { \"fieldId\": \"fieldX\", \"fieldValue\": \"access-means\" }, " +
                        "\"formSiteLoginForm\": { \"fieldId\": \"fieldX\", \"fieldValue\": \"access-means\" }, " +
                        "\"clientId\": \"%s\", " +
                        "\"providerRequestId\": \"%s\"}",
                USER_ID,
                USER_SITE_ID,
                DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
                CLIENT_ID,
                UUID.randomUUID());

        // when
        MockHttpServletResponse response = mockMvc.perform(post("/form/BUDGET_INSIGHT/update-external-user-site")
                        .content(content)
                        .contentType(APPLICATION_JSON)
                        .header(CLIENT_TOKEN_HEADER_NAME, clientGatewayClientUserToken.getSerialized()))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.ACCEPTED.value());
        verify(formProviderService).updateExternalUserSite(eq(BUDGET_INSIGHT_NAME), any(), isNotNull());
        verify(clientIdVerificationService).verify(isNotNull(), eq(CLIENT_ID));
    }

    @Test
    void shouldReturnCorrectResponseWhenSendingPostRequestToFormProviderCreateNewExternalUserSiteEndpointWithCorrectData() throws Exception {
        // given
        String content = String.format("{ \"externalSiteId\": \"42\", " +
                        "\"userId\": \"%s\", " +
                        "\"userSiteId\": \"%s\", " +
                        "\"accessMeansDTO\": { \"userId\": \"%s\", \"accessMeans\": \"access-means\", \"updated\": \"%s\", \"expireTime\": \"%s\" }, " +
                        "\"transactionsFetchStartTime\": \"%s\", " +
                        "\"filledInUserSiteFormValues\": { \"fieldId\": \"fieldX\", \"fieldValue\": \"access-means\" }, " +
                        "\"formSiteLoginForm\": { \"fieldId\": \"fieldX\", \"fieldValue\": \"access-means\" }, " +
                        "\"clientId\": \"%s\", " +
                        "\"providerRequestId\": \"%s\"}",
                USER_ID,
                USER_SITE_ID,
                USER_ID,
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(new Date()),
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(THOUSAND_YEARS_IN_THE_FUTURE),
                DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
                CLIENT_ID,
                UUID.randomUUID());

        // when
        MockHttpServletResponse response = mockMvc.perform(post("/form/BUDGET_INSIGHT/create-new-external-user-site")
                        .content(content)
                        .contentType(APPLICATION_JSON)
                        .header(CLIENT_TOKEN_HEADER_NAME, clientGatewayClientUserToken.getSerialized()))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.ACCEPTED.value());
        verify(formProviderService).createNewExternalUserSite(eq(BUDGET_INSIGHT_NAME), any(), isNotNull());
        verify(clientIdVerificationService).verify(isNotNull(), eq(CLIENT_ID));
    }

    @Test
    void shouldReturnCorrectResponseWhenSendingPostRequestToFormProviderSubmitMfaEndpointWithCorrectData() throws Exception {
        // given
        String content = String.format("{ \"externalSiteId\": \"42\", " +
                        "\"formUserSite\": { \"userId\": \"%s\", \"userSiteId\": \"%s\", \"externalId\": \"42\" }, " +
                        "\"accessMeans\": \"access-means\", " +
                        "\"transactionsFetchStartTime\": \"%s\", " +
                        "\"mfaFormJson\": \"{ \\\"fieldId\\\": \\\"fieldX\\\", \\\"fieldValue\\\": \\\"access-means\\\" }\", " +
                        "\"filledInUserSiteFormValues\": { \"fieldId\": \"fieldX\", \"fieldValue\": \"access-means\" }, " +
                        "\"clientId\": \"%s\", " +
                        "\"providerRequestId\": \"%s\"}",
                USER_ID,
                USER_SITE_ID,
                DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
                CLIENT_ID,
                UUID.randomUUID());

        // when
        MockHttpServletResponse response = mockMvc.perform(post("/form/BUDGET_INSIGHT/submit-mfa")
                        .content(content)
                        .contentType(APPLICATION_JSON)
                        .header(CLIENT_TOKEN_HEADER_NAME, clientGatewayClientUserToken.getSerialized()))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.ACCEPTED.value());
        verify(formProviderService).submitMFA(eq(BUDGET_INSIGHT_NAME), any(), isNotNull());
        verify(clientIdVerificationService).verify(isNotNull(), eq(CLIENT_ID));
    }

    @Test
    void shouldReturnCorrectResponseWhenSendingPostRequestToFormProviderProcessCallbackEndpointWithCorrectData() throws Exception {
        // given
        String content = String.format("{ " +
                "\"body\": \"body\", " +
                "\"subPath\": \"/sub/path\", " +
                "\"moreInfo\": { \"externalSiteId\": \"42\", \"externalAccounts\": [] }, " +
                "\"clientId\": \"%s\"" +
                " }", CLIENT_ID);

        Consumer<JwtClaims> mutator = claims -> claims.setClaim(EXTRA_CLAIM_ISSUED_FOR, "site-management");
        ClientToken siteManagementClientUserToken = testClientTokens.createClientUserToken(CLIENT_GROUP_ID, CLIENT_ID, USER_ID, mutator);

        // when
        MockHttpServletResponse response = mockMvc.perform(post("/form/BUDGET_INSIGHT/process-callback")
                        .content(content)
                        .contentType(APPLICATION_JSON)
                        .header(CLIENT_TOKEN_HEADER_NAME, siteManagementClientUserToken.getSerialized()))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.ACCEPTED.value());
        verify(formProviderService).processCallback(eq(BUDGET_INSIGHT_NAME), any(), isNotNull());
        verify(clientIdVerificationService).verify(isNotNull(), eq(CLIENT_ID));
    }

    @Test
    void shouldReturnCorrectResponseWhenSendingPostRequestToFormProviderTriggerRefreshAndFetchDataEndpointWithCorrectData() throws Exception {
        // given
        String content = String.format("{ " +
                        "\"externalSiteId\": \"42\", " +
                        "\"formUserSite\": { \"userId\": \"%s\", \"userSiteId\": \"%s\", \"externalId\": \"42\" }, " +
                        "\"accessMeans\": { \"userId\": \"%s\", \"accessMeansBlob\": \"access-means\", \"updated\": \"%s\", \"expireTime\": \"%s\" }, " +
                        "\"transactionsFetchStartTime\": \"%s\", " +
                        "\"providerRequestId\": \"%s\", " +
                        "\"clientId\": \"%s\" }",
                USER_ID,
                USER_SITE_ID,
                USER_ID,
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(new Date()),
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(THOUSAND_YEARS_IN_THE_FUTURE),
                DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
                UUID.randomUUID(),
                CLIENT_ID);
        // when
        MockHttpServletResponse response = mockMvc.perform(post("/form/BUDGET_INSIGHT/trigger-refresh-and-fetch-data")
                        .content(content)
                        .contentType(APPLICATION_JSON)
                        .header(CLIENT_TOKEN_HEADER_NAME, clientGatewayClientUserToken.getSerialized()))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.ACCEPTED.value());
        verify(formProviderService).triggerRefreshAndFetchData(eq(BUDGET_INSIGHT_NAME), any(), isNotNull());
        verify(clientIdVerificationService).verify(isNotNull(), eq(CLIENT_ID));
    }

    @Test
    void shouldReturnCorrectResponseWhenSendingGetRequestToFormProviderExternalUserIdsEndpoint() throws Exception {
        // given
        UUID clientId = UUID.randomUUID();

        // when
        MockHttpServletResponse response = mockMvc.perform(get("/form/BUDGET_INSIGHT/external-user-ids")
                        .header("client-id", clientId)
                        .header(CLIENT_TOKEN_HEADER_NAME, clientGatewayClientToken.getSerialized()))
                .andReturn()
                .getResponse();

        // then
        UUID batchId = UUID.fromString(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(HttpStatus.ACCEPTED.value());
        verify(formProviderService).fetchExternalUserIdsFromProvider(eq(BUDGET_INSIGHT_NAME), eq(batchId), eq(clientId), isNotNull());
        verify(clientIdVerificationService, atLeast(2)).verify(isNotNull(), eq(clientId));
    }

    @Test
    void shouldReturnCorrectResponseWhenSendingDeleteRequestToFormProviderExternalUserIdsIdEndpoint() throws Exception {
        // given
        UUID clientId = UUID.randomUUID();

        // when
        MockHttpServletResponse response = mockMvc.perform(delete("/form/BUDGET_INSIGHT/external-user-ids/123")
                        .header("client-id", clientId)
                        .header(CLIENT_TOKEN_HEADER_NAME, clientGatewayClientToken.getSerialized()))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.ACCEPTED.value());
        verify(formProviderService).deleteExternalUserByIdAtProvider(eq(BUDGET_INSIGHT_NAME), eq("123"), eq(clientId), isNotNull());
        verify(clientIdVerificationService, atLeast(2)).verify(isNotNull(), eq(clientId));
    }

    @Test
    void shouldReturnErrorWhenSendingPostRequestToFormProviderAccessMeansRefreshWhenAuthenticationMeanValidationException() throws Exception {
        // given
        doThrow(AuthenticationMeanValidationException.class).when(formProviderService).refreshAccessMeansForForm(eq(BUDGET_INSIGHT_NAME), isNotNull(), any());
        String content = String.format("{ \"accessMeansDTO\": { \"userId\": \"%s\", \"accessMeansBlob\": \"access-means\", \"updated\": \"%s\", \"expireTime\": \"%s\" }, \"clientId\": \"%s\" }",
                USER_ID,
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(new Date()),
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(THOUSAND_YEARS_IN_THE_FUTURE),
                CLIENT_ID);

        // when
        MockHttpServletResponse response = mockMvc.perform(post("/form/BUDGET_INSIGHT/access-means/refresh")
                        .content(content)
                        .contentType(APPLICATION_JSON)
                        .header(CLIENT_TOKEN_HEADER_NAME, clientGatewayClientToken.getSerialized()))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        ErrorDTO errorDTO = objectMapper.readValue(response.getContentAsString(), ErrorDTO.class);
        assertThat(errorDTO.getCode()).isEqualTo("PR039");
        assertThat(errorDTO.getMessage()).isEqualTo("Wrong format of provided authentication means.");
        verify(formProviderService).refreshAccessMeansForForm(eq(BUDGET_INSIGHT_NAME), isNotNull(), any());
        verify(clientIdVerificationService).verify(isNotNull(), eq(CLIENT_ID));
    }

    @Test
    void shouldReturnErrorWhenSendingPostRequestToFormProviderAccessMeansRefreshWhenExternalUserSiteDoesNotExistException() throws Exception {
        // given
        doThrow(ExternalUserSiteDoesNotExistException.class).when(formProviderService).refreshAccessMeansForForm(eq(BUDGET_INSIGHT_NAME), isNotNull(), any());
        String content = String.format("{ \"accessMeansDTO\": { \"userId\": \"%s\", \"accessMeansBlob\": \"access-means\", \"updated\": \"%s\", \"expireTime\": \"%s\" }, \"clientId\": \"%s\" }",
                USER_ID,
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(new Date()),
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(THOUSAND_YEARS_IN_THE_FUTURE),
                CLIENT_ID);

        // when
        MockHttpServletResponse response = mockMvc.perform(post("/form/BUDGET_INSIGHT/access-means/refresh")
                        .content(content)
                        .contentType(APPLICATION_JSON)
                        .header(CLIENT_TOKEN_HEADER_NAME, clientGatewayClientToken.getSerialized()))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        ErrorDTO errorDTO = objectMapper.readValue(response.getContentAsString(), ErrorDTO.class);
        assertThat(errorDTO.getCode()).isEqualTo("PR050");
        assertThat(errorDTO.getMessage()).isEqualTo("User does not exist.");
        verify(formProviderService).refreshAccessMeansForForm(eq(BUDGET_INSIGHT_NAME), isNotNull(), any());
        verify(clientIdVerificationService).verify(isNotNull(), eq(CLIENT_ID));
    }

}
