package com.yolt.providers.web.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import nl.ing.lovebird.providershared.AccessMeansDTO;
import nl.ing.lovebird.providershared.form.FormUserSiteDTO;

import java.time.Instant;
import java.util.UUID;

@Data
@AllArgsConstructor
public class FormFetchDataDTO {

    private final String externalSiteId;
    private final FormUserSiteDTO formUserSite;
    private final Instant transactionsFetchStartTime;
    private final AccessMeansDTO accessMeans;
    private final UUID providerRequestId;
    private final UUID clientId;
    @JsonProperty("isForFlywheel")
    private final boolean isForFlywheel;
    private final UserSiteDataFetchInformation userSiteDataFetchInformation;
    private final UUID activityId;
    private final UUID siteId;
}
