package com.yolt.providers.web.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import nl.ing.lovebird.providershared.form.FilledInUserSiteFormValues;

import java.util.UUID;

@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiCreateAccessMeansDTO {

    private final UUID userId;
    private final String redirectUrlPostedBackFromSite;
    private final String baseClientRedirectUrl;
    private final AuthenticationMeansReference authenticationMeansReference;
    private final String providerState;
    private final FilledInUserSiteFormValues filledInUserSiteFormValues;
    private final String state;
    private final String psuIpAddress;

}