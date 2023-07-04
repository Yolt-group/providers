package com.yolt.providers.web.controller.dto;

import lombok.NonNull;
import lombok.Value;
import nl.ing.lovebird.providershared.AccessMeansDTO;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;

@Value
public class ApiNotifyUserSiteDeleteDTO {

    /**
     * The consent ID which is known at the site. This cannot be null, since then it wouldn't make sense to send a notification..
     */
    @NonNull
    private final String externalConsentId;

    /**
     * The authentication means references that will be used to connect with during this request.
     */
    @NonNull
    private final AuthenticationMeansReference authenticationMeansReference;

    private final String psuIpAddress;

    private final AccessMeansDTO accessMeansDTO;
}
