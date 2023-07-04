package com.yolt.providers.web.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;

@Data
@AllArgsConstructor
public class ApiGetLoginDTO {
    /**
     * Base url for authorization_code callback.
     */
    private final String baseClientRedirectUrl;
    /**
     * State parameter value that the ApiDataProvider should include in authorization_code callback.
     */
    private final String state;
    private final AuthenticationMeansReference authenticationMeansReference;

    /**
     * An optional consent ID which is known at the site. This will always be null if the login URL is for an add bank.
     */
    private final String externalConsentId;

    private final String psuIpAddress;
}
