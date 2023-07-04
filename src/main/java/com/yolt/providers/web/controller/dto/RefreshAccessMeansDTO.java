package com.yolt.providers.web.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import nl.ing.lovebird.providershared.AccessMeansDTO;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;

import java.time.Instant;

@Data
@AllArgsConstructor
public class RefreshAccessMeansDTO {

    private final AccessMeansDTO accessMeansDTO;
    private final AuthenticationMeansReference authenticationMeansReference;
    private final String psuIpAddress;
    private final Instant consentCreationTime;

}
