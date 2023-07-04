package com.yolt.providers.web.form;

import com.yolt.providers.web.exception.SiteManagementUserSiteExternalIdUpdateException;
import com.yolt.providers.web.rest.InternalRestTemplateBuilder;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.providershared.form.SetExternalUserSiteIdDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class SiteManagementClient {

    private final RestTemplate restTemplate;
    private final String siteManagementBaseUrl;

    SiteManagementClient(final InternalRestTemplateBuilder internalRestTemplateBuilder,
                         @Value("${service.siteManagement.url}") final String siteManagementBaseUrl) {
        this.restTemplate = internalRestTemplateBuilder.build();
        this.siteManagementBaseUrl = siteManagementBaseUrl;
    }

    void updateExternalUserSiteId(final SetExternalUserSiteIdDTO dto) {
        var body = new ExternalUserSiteDTO(dto.getExternalId());

        // "client-id" HTTP header will be injected
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);

        String url = siteManagementBaseUrl + "/user-sites/{userSiteId}/external";
        Map<String, String> uriParameters = new HashMap<>();
        uriParameters.put("userSiteId", dto.getUserSiteId().toString());
        HttpEntity<ExternalUserSiteDTO> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> exchange = restTemplate.exchange(url, HttpMethod.PUT, entity, String.class, uriParameters);

            int responseCode = exchange.getStatusCode().value();
            if (HttpStatus.NO_CONTENT.value() != responseCode) {
                throwSiteManagementUserSiteExternalIdUpdateException(responseCode);
            }
        } catch (HttpStatusCodeException exception) {
            throwSiteManagementUserSiteExternalIdUpdateException(exception.getStatusCode().value());
        }
    }

    @lombok.Value
    public static class ExternalUserSiteDTO {
        String externalUserSiteId;
    }

    private void throwSiteManagementUserSiteExternalIdUpdateException(final int statusCode) {
        String errorMessage = "request to Site Management did not succeed with '204 No Content', it returned " + statusCode + " instead";
        log.error(errorMessage);
        throw new SiteManagementUserSiteExternalIdUpdateException(errorMessage);
    }
}
