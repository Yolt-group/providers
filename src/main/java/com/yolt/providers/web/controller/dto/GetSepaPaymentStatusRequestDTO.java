package com.yolt.providers.web.controller.dto;

import lombok.Value;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import org.springframework.lang.Nullable;

import javax.validation.constraints.NotNull;

/**
 * Request data to submit SEPA payment.
 */
@Value
public class GetSepaPaymentStatusRequestDTO {

    /**
     * Set of necessary fields to find authentication means
     */
    @NotNull
    AuthenticationMeansReference authenticationMeansReference;

    /**
     * Redirect url that was constructed by the bank in OpenBanking flow.
     */
    @NotNull
    String paymentId;

    @Nullable
    String psuIpAddress;

}
