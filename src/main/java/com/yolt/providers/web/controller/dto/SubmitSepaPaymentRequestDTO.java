package com.yolt.providers.web.controller.dto;

import lombok.Value;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import org.springframework.lang.Nullable;

import javax.validation.constraints.NotNull;

/**
 * Request data to submit SEPA payment.
 */
@Value
public class SubmitSepaPaymentRequestDTO {

    /**
     * Optional data that is returned on initiate payment endpoint (usually in form of JSON)
     */
    @Nullable
    String providerState;

    /**
     * Set of necessary fields to find authentication means
     */
    @NotNull
    AuthenticationMeansReference authenticationMeansReference;

    /**
     * Redirect url that was constructed by the bank in OpenBanking flow.
     */
    @NotNull
    String redirectUrlPostedBackFromSite;

    @Nullable
    String psuIpAddress;

}
