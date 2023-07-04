package com.yolt.providers.web.controller.dto;

import com.yolt.providers.common.pis.ukdomestic.InitiateUkDomesticPeriodicPaymentRequestDTO;
import lombok.Value;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import org.springframework.lang.Nullable;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@Value
public class InitiateUkPeriodicPaymentRequestDTO {

    /**
     * Required payment data (as per openbanking standard)
     */
    @Valid
    @NotNull
    InitiateUkDomesticPeriodicPaymentRequestDTO requestDTO;

    /**
     * State (usually in form of a uuid) that serves as a reference to payment entry in site-management
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
