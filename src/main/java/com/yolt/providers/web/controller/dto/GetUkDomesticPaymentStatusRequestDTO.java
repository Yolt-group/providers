package com.yolt.providers.web.controller.dto;

import lombok.Value;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import org.springframework.lang.Nullable;

import javax.validation.constraints.NotNull;

/**
 * Request data to get UK domestic payment status.
 */
@Value
public class GetUkDomesticPaymentStatusRequestDTO {

    /**
     * Set of necessary fields to find authentication means
     */
    @NotNull
    AuthenticationMeansReference authenticationMeansReference;

    @NotNull
    String paymentId;

    @Nullable
    String psuIpAddress;

}
