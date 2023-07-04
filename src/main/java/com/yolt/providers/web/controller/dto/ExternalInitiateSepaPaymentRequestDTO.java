package com.yolt.providers.web.controller.dto;

import lombok.Value;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import org.springframework.lang.Nullable;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * Request data to initiate a SEPA payment.
 */
@Value
public class ExternalInitiateSepaPaymentRequestDTO {

    /**
     * Required payment data (as per Berlin standard)
     */
    @Valid
    @NotNull
    ExternalSepaInitiatePaymentRequestDTO requestDTO;

    /**
     * State (usually in form of id) that serves as a reference to payment entry in site-management
     */
    @NotNull
    String state;

    /**
     * Set of necessary fields to find authentication means
     */
    @NotNull
    AuthenticationMeansReference authenticationMeansReference;

    /**
     * Client's base redirect url (for example, http://ing.nl/redirect), should be further enriched with additional parameters
     */
    @NotNull
    String baseClientRedirectUrl;

    @Nullable
    String psuIpAddress;

}
