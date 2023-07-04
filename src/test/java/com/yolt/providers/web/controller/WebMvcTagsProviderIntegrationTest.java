package com.yolt.providers.web.controller;

import com.yolt.providers.web.configuration.IntegrationTestContext;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.logging.MDCContextCreator;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.web.server.LocalManagementPort;
import org.springframework.boot.test.autoconfigure.actuate.metrics.AutoConfigureMetrics;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@IntegrationTestContext
@AutoConfigureMetrics
public class WebMvcTagsProviderIntegrationTest {

    private static final String SITE_ID = "8627538c-0fbe-4562-89d7-b9bec49fec33";
    private static final String PROVIDER = "YOLT_PROVIDER";

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int localPort;

    @LocalManagementPort
    private int managementPort;

    @Test
    public void shouldReturnHttpServerRequestsSecondsCountMetricForNotFoundStatusWhenSendingGetRequestToManagementPrometheusEndpointAfterSendingRequestToNonExistingEndpoint() {
        // given
        restTemplate.postForEntity("http://localhost:" + localPort + "/providers/wrong_path", "", String.class);

        // when
        String metrics = restTemplate.getForEntity("http://localhost:" + managementPort + "/providers/actuator/prometheus", String.class).getBody();

        // then
        List<String> restTemplateMetrics = Arrays.stream(metrics.split("\n"))
                .filter(it -> it.contains("http_server_requests_seconds_count") &&
                        it.contains("method=\"POST\"") &&
                        it.contains("provider=\"\"") &&
                        it.contains("site_id=\"\"") &&
                        it.contains("status=\"404\"") &&
                        it.contains("uri=\"/**\""))
                .collect(Collectors.toList());
        assertThat(restTemplateMetrics).hasSize(1);
    }

    @Test
    public void shouldReturnHttpServerRequestsSecondsCountMetricForBadRequestStatusWhenSendingGetRequestToManagementPrometheusEndpointAfterSendingRequestToExistingEndpoint() {
        // given
        HttpHeaders headers = new HttpHeaders();
        MDC.put(MDCContextCreator.SITE_ID_MDC_KEY, SITE_ID);
        headers.add("site_id", SITE_ID);
        restTemplate.postForEntity("http://localhost:" + localPort + "/providers/v2/{provider}/login-info", new HttpEntity<>(headers), String.class, PROVIDER);

        // when
        String metrics = restTemplate.getForEntity("http://localhost:" + managementPort + "/providers/actuator/prometheus", String.class).getBody();

        // then
        List<String> restTemplateMetrics = Arrays.stream(metrics.split("\n"))
                .filter(it -> it.contains("http_server_requests_seconds_count") &&
                        it.contains("method=\"POST\"") &&
                        it.contains("provider=\"" + PROVIDER + "\"") &&
                        it.contains("site_id=\"" + SITE_ID + "\"") &&
                        it.contains("status=\"400\"") &&
                        it.contains("uri=\"/v2/{provider}/login-info\""))
                .collect(Collectors.toList());
        assertThat(restTemplateMetrics).hasSize(1);
    }
}