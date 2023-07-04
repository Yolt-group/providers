package com.yolt.providers.web.form;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.yolt.providers.web.exception.SiteManagementUserSiteExternalIdUpdateException;
import com.yolt.providers.web.rest.InternalRestTemplateBuilder;
import nl.ing.lovebird.logging.MDCContextCreator;
import nl.ing.lovebird.providershared.form.SetExternalUserSiteIdDTO;
import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SiteManagementClientTest {

    private static final String SITE_MANAGEMENT_URL = "/site-management";
    private static final WireMockServer wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    private final UUID randomUserId = UUID.randomUUID();
    private final UUID randomUserSiteId = UUID.randomUUID();
    private final String randomExternalId = RandomStringUtils.random(36, true, true);

    private SiteManagementClient siteManagementClient;

    @BeforeAll
    public static void startWireMock() {
        wireMockServer.start();
    }

    private static List<HttpStatus> provideFailingStatuses() {
        return Arrays.asList(HttpStatus.NOT_FOUND, HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.OK, HttpStatus.BAD_REQUEST);
    }

    @BeforeEach
    public void init() {
        siteManagementClient = new SiteManagementClient(new InternalRestTemplateBuilder(), "http://localhost:" + wireMockServer.port() + SITE_MANAGEMENT_URL);
        MDC.put(MDCContextCreator.USER_ID_MDC_KEY, randomUserId.toString()); //NOSHERIFF
    }

    /**
     * test the right exception is thrown when the HTTP requests does not return "204 No Content"
     */
    @ParameterizedTest
    @MethodSource("provideFailingStatuses")
    public void shouldThrowSiteManagementUserSiteExternalIdUpdateExceptionForUpdateExternalUserSiteIdWhenFailingResponseStatus(HttpStatus failingStatus) {
        // given
        wireMockServer.stubFor(WireMock.put(urlMatching("/site-management/user-sites/" + randomUserSiteId + "/external"))
                .withHeader("Content-Type", equalTo(MediaType.APPLICATION_JSON_VALUE))
                .withHeader("Accept", containing(MediaType.APPLICATION_JSON_VALUE))
                .withHeader("user-id", equalTo(randomUserId.toString()))
                .withRequestBody(matchingJsonPath("$.externalUserSiteId", equalTo(randomExternalId)))
                .willReturn(aResponse().withStatus(failingStatus.value())));

        // when
        ThrowableAssert.ThrowingCallable updateExternalUserSiteIdCallable = () -> siteManagementClient.updateExternalUserSiteId(new SetExternalUserSiteIdDTO(
                randomUserId,
                randomUserSiteId,
                randomExternalId));

        // then
        assertThatThrownBy(updateExternalUserSiteIdCallable)
                .isInstanceOf(SiteManagementUserSiteExternalIdUpdateException.class)
                .hasMessage("request to Site Management did not succeed with '204 No Content', it returned %d instead", failingStatus.value());
    }

    /**
     * test that the method returns if the HTTP requests returns "204 No Content"
     */
    @Test
    public void shouldNotThrowAnyExceptionForUpdateExternalUserSiteIdWhenNoContentResponse() {
        wireMockServer.stubFor(WireMock.put(urlMatching("/site-management/user-sites/" + randomUserSiteId + "/external"))
                .withHeader("Content-Type", equalTo(MediaType.APPLICATION_JSON_VALUE))
                .withHeader("Accept", containing(MediaType.APPLICATION_JSON_VALUE))
                .withHeader("user-id", equalTo(randomUserId.toString()))
                .withRequestBody(matchingJsonPath("$.externalUserSiteId", equalTo(randomExternalId)))
                .willReturn(aResponse().withStatus(204)));

        // when
        ThrowableAssert.ThrowingCallable updateExternalUserSiteIdCallable = () -> siteManagementClient.updateExternalUserSiteId(new SetExternalUserSiteIdDTO(
                randomUserId,
                randomUserSiteId,
                randomExternalId));

        // then
        assertThatCode(updateExternalUserSiteIdCallable)
                .doesNotThrowAnyException();
    }

    @AfterEach
    public void after() {
        MDC.clear();
    }

    @AfterAll
    public static void stopWireMock() {
        wireMockServer.stop();
    }
}
