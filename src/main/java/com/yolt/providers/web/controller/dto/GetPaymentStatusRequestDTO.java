package com.yolt.providers.web.controller.dto;

import lombok.Value;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import org.springframework.lang.Nullable;

import javax.validation.constraints.NotNull;

@Value
public class GetPaymentStatusRequestDTO {

    /**
     * Set of necessary fields to find authentication means
     */
    @NotNull
    private AuthenticationMeansReference authenticationMeansReference;

    @Nullable
    private String paymentId;

    @Nullable
    private String psuIpAddress;

    @Nullable
    private String providerState;
}
