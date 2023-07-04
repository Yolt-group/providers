package com.yolt.providers.web.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;

import javax.validation.constraints.NotNull;

@Data
@AllArgsConstructor
public class InvokeConsentTestingDTO {
    @NotNull
    private final String baseClientRedirectUrl;
    @NotNull
    private final AuthenticationMeansReference authenticationMeansReference;
}