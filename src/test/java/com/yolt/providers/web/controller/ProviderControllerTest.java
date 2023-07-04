package com.yolt.providers.web.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yolt.providers.common.ais.form.EncryptionDetails;
import com.yolt.providers.common.domain.dynamic.AccessMeansOrStepDTO;
import com.yolt.providers.common.domain.dynamic.step.FormStep;
import com.yolt.providers.common.domain.dynamic.step.RedirectStep;
import com.yolt.providers.common.exception.FormDecryptionFailedException;
import com.yolt.providers.common.exception.MissingAuthenticationMeansException;
import com.yolt.providers.common.exception.TokenInvalidException;
import com.yolt.providers.web.circuitbreaker.ProvidersCircuitBreakerException;
import com.yolt.providers.web.circuitbreaker.ProvidersNonCircuitBreakingTokenInvalidException;
import com.yolt.providers.web.configuration.TestConfiguration;
import com.yolt.providers.web.controller.dto.*;
import com.yolt.providers.web.errorhandling.ExtendedExceptionHandlingService;
import com.yolt.providers.web.service.ProviderService;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.clienttokens.ClientUserToken;
import nl.ing.lovebird.clienttokens.test.TestClientTokens;
import nl.ing.lovebird.errorhandling.ErrorDTO;
import nl.ing.lovebird.providershared.AccessMeansDTO;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import nl.ing.lovebird.providershared.form.ExplanationField;
import nl.ing.lovebird.providershared.form.Form;
import nl.ing.lovebird.providershared.form.FormField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.yolt.providers.web.configuration.TestConfiguration.JACKSON_OBJECT_MAPPER;
import static nl.ing.lovebird.clienttokens.constants.ClientTokenConstants.CLIENT_TOKEN_HEADER_NAME;
import static nl.ing.lovebird.clienttokens.constants.ClientTokenConstants.EXTRA_CLAIM_ISSUED_FOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProviderController.class)
@Import({
        TestConfiguration.class,
        ExtendedExceptionHandlingService.class
})
class ProviderControllerTest {

    private static final Long HOUR_IN_MILLISECS = 1000L * 60L * 60L;
    private static final UUID CLIENT_GROUP_ID = UUID.randomUUID();
    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID CLIENT_APPLICATION_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID USER_SITE_ID = UUID.randomUUID();
    private static final UUID SITE_ID = UUID.randomUUID();
    private static final String STALE_ACCESS_MEANS = UUID.randomUUID().toString();
    private static final String ACCESS_MEANS = UUID.randomUUID().toString();
    private static final String YOLT_PROVIDER_NAME = "YOLT_PROVIDER";
    private static final String STARLINGBANK_NAME = "STARLINGBANK";
    protected Appender<ILoggingEvent> mockAppender;
    protected ArgumentCaptor<ILoggingEvent> captorLoggingEvent;
    @Autowired
    @Qualifier(JACKSON_OBJECT_MAPPER)
    private ObjectMapper objectMapper;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestClientTokens testClientTokens;

    @MockBean
    private ProviderService providerService;

    private ClientUserToken siteManagementClientUserToken;
    private ClientUserToken clientGatewayClientUserToken;

    @BeforeEach
    void setup() {
        mockAppender = mock(Appender.class);
        captorLoggingEvent = ArgumentCaptor.forClass(ILoggingEvent.class);
        var logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.addAppender(mockAppender);
        siteManagementClientUserToken = testClientTokens.createClientUserToken(CLIENT_GROUP_ID, CLIENT_ID, USER_ID,
                claims -> claims.setClaim(EXTRA_CLAIM_ISSUED_FOR, "site-management"));
        clientGatewayClientUserToken = testClientTokens.createClientUserToken(CLIENT_GROUP_ID, CLIENT_ID, USER_ID,
                claims -> claims.setClaim(EXTRA_CLAIM_ISSUED_FOR, "client-gateway"));
    }

    @AfterEach
    public void teardown() {
        final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.detachAppender(mockAppender);
    }

    @Test
    void shouldReturnRefreshedAccessMeansWhenSendingPostRequestToProviderAccessMeansRefreshEndpointWithCorrectData() throws Exception {
        // given
        String provider = YOLT_PROVIDER_NAME;
        String url = "/" + provider + "/access-means/refresh";
        Date staleUpdate = new Date(System.currentTimeMillis() - HOUR_IN_MILLISECS * 24L);
        Date staleExpires = new Date(System.currentTimeMillis() - HOUR_IN_MILLISECS);
        AccessMeansDTO staleAccessMeansDTO = new AccessMeansDTO(USER_ID, STALE_ACCESS_MEANS, staleUpdate, staleExpires);
        RefreshAccessMeansDTO requestDTO = new RefreshAccessMeansDTO(staleAccessMeansDTO, new AuthenticationMeansReference(CLIENT_ID, CLIENT_APPLICATION_ID), null, Instant.now().minus(30, ChronoUnit.DAYS));
        String requestBodyJson = writeJson(requestDTO);
        AccessMeansDTO accessMeansDTO = makeAccessMeansDTO();
        when(providerService.refreshAccessMeans(eq(provider), eq(requestDTO), any(ClientToken.class), eq(SITE_ID), any(boolean.class))).thenReturn(accessMeansDTO);
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("forceExperimentalVersion", "false");
        // when
        MockHttpServletResponse response = mockMvc.perform(post(url)
                        .params(parameters)
                        .content(requestBodyJson)
                        .header("user-id", USER_ID)
                        .header("site_id", SITE_ID)
                        .header(CLIENT_TOKEN_HEADER_NAME, siteManagementClientUserToken.getSerialized())
                        .contentType(APPLICATION_JSON))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        AccessMeansDTO result = objectMapper.readValue(response.getContentAsString(), AccessMeansDTO.class);
        assertThat(result.getUserId()).isEqualTo(accessMeansDTO.getUserId());
        assertThat(result.getAccessMeans()).isEqualTo(accessMeansDTO.getAccessMeans());
    }

    @Test
    void shouldReturnProperErrorWhenSendingPostRequestToProviderAccessMeansRefreshEndpointWhenInvalidToken() throws Exception {
        // given
        String provider = YOLT_PROVIDER_NAME;
        String url = "/" + provider + "/access-means/refresh";
        Date staleUpdate = new Date(System.currentTimeMillis() - HOUR_IN_MILLISECS * 24L);
        Date staleExpires = new Date(System.currentTimeMillis() - HOUR_IN_MILLISECS);
        AccessMeansDTO staleAccessMeansDTO = new AccessMeansDTO(USER_ID, STALE_ACCESS_MEANS, staleUpdate, staleExpires);
        RefreshAccessMeansDTO requestDTO = new RefreshAccessMeansDTO(staleAccessMeansDTO, new AuthenticationMeansReference(CLIENT_ID, CLIENT_APPLICATION_ID), null, Instant.now().minus(30, ChronoUnit.DAYS));
        String requestBodyJson = writeJson(requestDTO);
        doThrow(new ProvidersNonCircuitBreakingTokenInvalidException("lala", new TokenInvalidException()))
                .when(providerService).refreshAccessMeans(eq(provider), eq(requestDTO), any(ClientToken.class), eq(SITE_ID), any(boolean.class));
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("forceExperimentalVersion", "false");

        // when
        MockHttpServletResponse response = mockMvc.perform(post(url)
                        .params(parameters)
                        .content(requestBodyJson)
                        .header("user-id", USER_ID)
                        .header("site_id", SITE_ID)
                        .header(CLIENT_TOKEN_HEADER_NAME, clientGatewayClientUserToken)
                        .contentType(APPLICATION_JSON))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        ErrorDTO errorDTO = objectMapper.readValue(response.getContentAsString(), ErrorDTO.class);
        assertThat(errorDTO.getCode()).isEqualTo("PR034");
        assertThat(errorDTO.getMessage()).isEqualTo("The provided access means are not valid.");
    }

    @Test
    void shouldReturnErrorWhenSendingPostRequestToProviderAccessMeansRefreshEndpointWhenAuthenticationMeansAreMissing() throws Exception {
        // given
        String provider = YOLT_PROVIDER_NAME;
        String url = "/" + provider + "/access-means/refresh";
        Date staleUpdate = new Date(System.currentTimeMillis() - HOUR_IN_MILLISECS * 24L);
        Date staleExpires = new Date(System.currentTimeMillis() - HOUR_IN_MILLISECS);
        AccessMeansDTO staleAccessMeansDTO = new AccessMeansDTO(USER_ID, STALE_ACCESS_MEANS, staleUpdate, staleExpires);
        RefreshAccessMeansDTO requestDTO = new RefreshAccessMeansDTO(staleAccessMeansDTO, new AuthenticationMeansReference(CLIENT_ID, CLIENT_APPLICATION_ID), null, Instant.now().minus(30, ChronoUnit.DAYS));
        String requestBodyJson = writeJson(requestDTO);
        doThrow(new MissingAuthenticationMeansException(STARLINGBANK_NAME, "bla"))
                .when(providerService).refreshAccessMeans(eq(provider), eq(requestDTO), any(ClientToken.class), eq(SITE_ID), any(boolean.class));
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("forceExperimentalVersion", "false");
        // when
        MockHttpServletResponse response = mockMvc.perform(post(url)
                        .params(parameters)
                        .content(requestBodyJson)
                        .header("user-id", USER_ID)
                        .header("site_id", SITE_ID)
                        .header(CLIENT_TOKEN_HEADER_NAME, siteManagementClientUserToken.getSerialized())
                        .contentType(APPLICATION_JSON))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        ErrorDTO errorDTO = objectMapper.readValue(response.getContentAsString(), ErrorDTO.class);
        assertThat(errorDTO.getCode()).isEqualTo("PR031");
        assertThat(errorDTO.getMessage()).isEqualTo("The authentication means for given provider are missing.");
    }


    @Test
    void shouldReturnAccessMeansWhenSendingPostRequestToProviderAccessMeansCreateEndpointWithCorrectData() throws Exception {
        // given
        String provider = YOLT_PROVIDER_NAME;
        String url = "/" + provider + "/access-means/create";
        ApiCreateAccessMeansDTO requestDTO = new ApiCreateAccessMeansDTO(USER_ID, null, null, new AuthenticationMeansReference(CLIENT_ID, CLIENT_APPLICATION_ID), null, null, null, null);
        String requestBodyJson = writeJson(requestDTO);
        AccessMeansDTO accessMeansDTO = makeAccessMeansDTO();
        when(providerService.createNewAccessMeans(eq(provider), eq(requestDTO), any(ClientToken.class), eq(SITE_ID), any(boolean.class))).thenReturn(new AccessMeansOrStepDTO(accessMeansDTO));
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("forceExperimentalVersion", "false");
        // when
        MockHttpServletResponse response = mockMvc.perform(post(url)
                        .params(parameters)
                        .content(requestBodyJson)
                        .header("user-id", USER_ID)
                        .header("site_id", SITE_ID)
                        .header(CLIENT_TOKEN_HEADER_NAME, clientGatewayClientUserToken.getSerialized())
                        .contentType(APPLICATION_JSON))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        AccessMeansDTO result = objectMapper.readValue(response.getContentAsString(), AccessMeansDTO.class);
        assertThat(result.getUserId()).isEqualTo(accessMeansDTO.getUserId());
        assertThat(result.getAccessMeans()).isEqualTo(accessMeansDTO.getAccessMeans());
    }

    @Test
    void shouldReturnCorrectResponseWhenSendingPostRequestToProviderFetchDataEndpointWithCorrectData() throws Exception {
        // given
        String provider = YOLT_PROVIDER_NAME;
        String url = "/" + provider + "/fetch-data";
        AccessMeansDTO accessMeansDTO = makeAccessMeansDTO();
        ApiFetchDataDTO requestDTO = new ApiFetchDataDTO(
                USER_ID,
                Instant.now(),
                accessMeansDTO,
                new AuthenticationMeansReference(CLIENT_ID, CLIENT_APPLICATION_ID),
                null,
                null,
                null,
                null,
                new UserSiteDataFetchInformation(null, USER_SITE_ID, null, Collections.emptyList(), Collections.emptyList()));
        String requestBodyJson = writeJson(requestDTO);
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("forceExperimentalVersion", "false");
        // when
        MockHttpServletResponse response = mockMvc.perform(post(url)
                        .params(parameters)
                        .content(requestBodyJson)
                        .header("user-id", USER_ID)
                        .header("site_id", SITE_ID)
                        .header(CLIENT_TOKEN_HEADER_NAME, siteManagementClientUserToken.getSerialized())
                        .contentType(APPLICATION_JSON))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.ACCEPTED.value());
        assertThat(response.getContentAsString()).isEmpty();
        verify(providerService).fetchDataAsync(eq(provider), any(), eq(SITE_ID), any(), any(boolean.class));
    }

    @Test
    void shouldReturnCorrectResponseWhenSendingPostRequestToProviderNotifyUserSiteDeleteEndpointWithCorrectData() throws Exception {
        // given
        String provider = YOLT_PROVIDER_NAME;
        String url = "/" + provider + "/notify-user-site-delete";
        ApiNotifyUserSiteDeleteDTO requestDTO = new ApiNotifyUserSiteDeleteDTO("external-consent-id", new AuthenticationMeansReference(CLIENT_ID, CLIENT_APPLICATION_ID), null, null);
        String requestBodyJson = writeJson(requestDTO);
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("forceExperimentalVersion", "false");
        // when
        MockHttpServletResponse response = mockMvc.perform(post(url)
                        .params(parameters)
                        .content(requestBodyJson)
                        .header("user-id", USER_ID)
                        .header(CLIENT_TOKEN_HEADER_NAME, siteManagementClientUserToken.getSerialized())
                        .contentType(APPLICATION_JSON))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NO_CONTENT.value());
        assertThat(response.getContentAsString()).isEmpty();
        verify(providerService).notifyUserSiteDelete(eq(provider), isNull(), eq(requestDTO), any(ClientToken.class), any(boolean.class));
    }

    @Test
    void shouldReturnCorrectResponseWhenSendingPostRequestToV2ProviderAccessMeansCreateEndpointWithCorrectData() throws Exception {
        // given
        String provider = YOLT_PROVIDER_NAME;
        String url = String.format("/v2/%s/access-means/create", provider);
        ApiCreateAccessMeansDTO requestDTO = new ApiCreateAccessMeansDTO(USER_ID, null, null, new AuthenticationMeansReference(CLIENT_ID, CLIENT_APPLICATION_ID), null, null, null, null);
        when(providerService.createNewAccessMeans(eq(provider), eq(requestDTO), any(ClientToken.class), eq(SITE_ID), any(boolean.class))).thenReturn(new AccessMeansOrStepDTO(new AccessMeansDTO(UUID.randomUUID(), "accessMeans", Date.from(Instant.now()), Date.from(Instant.now()))));
        String requestBody = writeJson(requestDTO);
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("forceExperimentalVersion", "false");
        // when
        MockHttpServletResponse response = mockMvc.perform(post(url)
                        .params(parameters)
                        .content(requestBody)
                        .header("user-id", USER_ID)
                        .header("site_id", SITE_ID)
                        .header(CLIENT_TOKEN_HEADER_NAME, clientGatewayClientUserToken.getSerialized())
                        .contentType(APPLICATION_JSON))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void shouldReturnErrorResponseAndProduceSemaEventWhenSendingPostRequestToV2ProviderWithInvalidFormEncryption() throws Exception {
        // given
        String provider = YOLT_PROVIDER_NAME;
        String url = String.format("/v2/%s/access-means/create", provider);
        ApiCreateAccessMeansDTO requestDTO = new ApiCreateAccessMeansDTO(USER_ID, null, null, new AuthenticationMeansReference(CLIENT_ID, CLIENT_APPLICATION_ID), null, null, null, null);
        when(providerService.createNewAccessMeans(eq(provider), eq(requestDTO), any(ClientToken.class), eq(SITE_ID), any(boolean.class))).thenThrow(new ProvidersCircuitBreakerException("Wrapper", new FormDecryptionFailedException("Form decryption failed.")));
        String requestBody = writeJson(requestDTO);
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("forceExperimentalVersion", "false");

        // when
        MockHttpServletResponse response = mockMvc.perform(post(url)
                        .params(parameters)
                        .content(requestBody)
                        .header("user-id", USER_ID)
                        .header("site_id", SITE_ID)
                        .header(CLIENT_TOKEN_HEADER_NAME, clientGatewayClientUserToken.getSerialized())
                        .contentType(APPLICATION_JSON))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
//        verify(mockAppender, times(2)).doAppend(any());
        verify(mockAppender,times(2)).doAppend(captorLoggingEvent.capture());
        var allLoggingEvents = captorLoggingEvent.getAllValues();
        var semaLogEvent = allLoggingEvents.get(0);
        var applicationLogEvent = allLoggingEvents.get(1);
        assertThat(semaLogEvent.getMarker().toString()).isEqualTo("log_type=SEMA, sema_type=com.yolt.providers.web.configuration.FormDecryptionFailedSEMaEvent");
        assertThat(applicationLogEvent.getLevel()).isEqualTo(Level.WARN);
        assertThat(applicationLogEvent.toString()).isEqualTo("[WARN] Submitted form could not be decrypted (PR063): com.yolt.providers.common.exception.FormDecryptionFailedException");
    }

    @Test
    void shouldReturnRedirectStepWhenSendingPostRequestToV2ProviderLoginInfoEndpointWithCorrectData() throws Exception {
        // given
        String provider = YOLT_PROVIDER_NAME;
        String url = String.format("/v2/%s/login-info/", provider);
        ApiGetLoginDTO requestDTO = new ApiGetLoginDTO(null, null, new AuthenticationMeansReference(CLIENT_ID, CLIENT_APPLICATION_ID), null, null);
        String requestBodyJson = writeJson(requestDTO);
        RedirectStep redirectStep = new RedirectStep("http://my-test.com/login", null, null);
        when(providerService.getLoginInfo(eq(provider), eq(requestDTO), any(ClientToken.class), eq(SITE_ID), any(boolean.class))).thenReturn(redirectStep);
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("forceExperimentalVersion", "false");
        // when
        MockHttpServletResponse response = mockMvc.perform(post(url)
                        .params(parameters)
                        .content(requestBodyJson)
                        .header("user-id", USER_ID)
                        .header("site_id", SITE_ID)
                        .header(CLIENT_TOKEN_HEADER_NAME, clientGatewayClientUserToken.getSerialized())
                        .contentType(APPLICATION_JSON))
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        HashMap<String, String> result = objectMapper.readValue(response.getContentAsString(), new TypeReference<>() {
        });
        assertThat(result)
                .containsEntry("redirectUrl", redirectStep.getRedirectUrl())
                .containsEntry("providerState", redirectStep.getProviderState());
        assertThat(result.get("externalConsentId")).isNullOrEmpty();
    }

    @Test
    void getLoginInfoWithFormStepFromDynamicEndpoint() throws Exception {
        // given
        String provider = YOLT_PROVIDER_NAME;
        String url = String.format("/v2/%s/login-info/", provider);
        ApiGetLoginDTO requestDTO = new ApiGetLoginDTO(null, null, new AuthenticationMeansReference(CLIENT_ID, CLIENT_APPLICATION_ID), null, null);
        String requestBodyJson = writeJson(requestDTO);
        Date date = Date.from(Instant.parse("2018-12-18T10:15:30.00Z"));
        Form form = new Form(Collections.emptyList(), new ExplanationField(), Collections.emptyMap());
        FormStep step = new FormStep(form, EncryptionDetails.noEncryption(), date.toInstant(), "state");
        when(providerService.getLoginInfo(eq(provider), eq(requestDTO), any(ClientToken.class), eq(SITE_ID), any(boolean.class))).thenReturn(step);
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("forceExperimentalVersion", "false");
        // when
        MockHttpServletResponse response = mockMvc.perform(post(url)
                        .params(parameters)
                        .content(requestBodyJson)
                        .header("user-id", USER_ID)
                        .header("site_id", SITE_ID)
                        .header(CLIENT_TOKEN_HEADER_NAME, clientGatewayClientUserToken.getSerialized())
                        .contentType(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();

        // then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        FormStep result = objectMapper.readValue(response.getContentAsString(), FormStep.class);
        assertThat(result.getTimeoutTime()).isNotNull();
        assertThat(result.getProviderState()).isEqualTo("state");
        Form resultForm = result.getForm();
        assertThat(resultForm.getFormComponents()).isEmpty();
        ExplanationField explanationField = resultForm.getExplanationField();
        assertThat(explanationField.getFieldType()).isEqualTo(FormField.FieldType.EXPLANATION);
        assertThat(explanationField.getId()).isNullOrEmpty();
        assertThat(explanationField.getDisplayName()).isNullOrEmpty();
        assertThat(explanationField.getExplanation()).isNullOrEmpty();
        assertThat(explanationField.getComponentType()).isEqualTo(FormField.ComponentType.FIELD);
        assertThat(resultForm.getHiddenComponents()).isEmpty();
    }

    private AccessMeansDTO makeAccessMeansDTO() {
        Date refreshedUpdate = new Date();
        Date refreshedExpires = new Date(System.currentTimeMillis() + HOUR_IN_MILLISECS * 24L);
        return new AccessMeansDTO(USER_ID, ACCESS_MEANS, refreshedUpdate, refreshedExpires);
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
