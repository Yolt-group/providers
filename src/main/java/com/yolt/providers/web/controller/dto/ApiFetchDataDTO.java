package com.yolt.providers.web.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import nl.ing.lovebird.providershared.AccessMeansDTO;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;

import java.time.Instant;
import java.util.UUID;

@Data
@AllArgsConstructor
public class ApiFetchDataDTO {
    private final UUID userId;
    private final Instant transactionsFetchStartTime;
    private final AccessMeansDTO accessMeans;
    private final AuthenticationMeansReference authenticationMeansReference;
    private final UUID providerRequestId;
    private final String providerState;
    private final UUID activityId;
    private final String psuIpAddress;
    private final UserSiteDataFetchInformation userSiteDataFetchInformation;
}
