package com.yolt.providers.web.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import nl.ing.lovebird.providershared.form.FilledInUserSiteFormValues;
import nl.ing.lovebird.providershared.form.FormUserSiteDTO;

import java.time.Instant;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FormSubmitMfaDTO {
    private String externalSiteId;
    private FormUserSiteDTO formUserSite;
    private String accessMeans;
    private Instant transactionsFetchStartTime;
    private String mfaFormJson;
    private FilledInUserSiteFormValues filledInUserSiteFormValues;
    private UUID clientId;
    private UUID providerRequestId;
    private UserSiteDataFetchInformation userSiteDataFetchInformation;
    private UUID activityId;
    private UUID siteId;
}
