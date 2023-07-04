package com.yolt.providers.web.controller;

import com.yolt.providers.web.controller.dto.ExternalInitiateUkDomesticPaymentResponseDTO;
import com.yolt.providers.web.controller.dto.ExternalInitiateUkScheduledPaymentRequestDTO;
import com.yolt.providers.web.controller.dto.InitiateUkPeriodicPaymentRequestDTO;
import com.yolt.providers.web.service.ClientTokenVerificationService;
import com.yolt.providers.web.service.ProviderUkDomesticPaymentService;
import lombok.RequiredArgsConstructor;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.clienttokens.annotations.VerifiedClientToken;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.UUID;

import static com.yolt.providers.web.service.ServiceConstants.*;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ProviderUkDomesticPaymentController {

    private final ProviderUkDomesticPaymentService paymentService;
    private final ClientTokenVerificationService clientTokenVerificationService;

    @PostMapping("/{provider}/payments/single/uk/initiate")
    public ExternalInitiateUkDomesticPaymentResponseDTO createSinglePayment(
            @PathVariable final String provider,
            @RequestParam final boolean forceExperimentalVersion,
            @VerifiedClientToken(restrictedTo = {
                    SERVICE_API_GATEWAY, SERVICE_CLIENT_GATEWAY, SERVICE_CONSENT_STARTER
            }) final ClientToken clientToken,
            @RequestHeader(value = "site_id") UUID siteId,
            @Valid @RequestBody final ExternalInitiateUkScheduledPaymentRequestDTO initiatePaymentRequest) {
        clientTokenVerificationService.verify(clientToken, initiatePaymentRequest.getAuthenticationMeansReference());
        return paymentService.initiateSinglePayment(provider, initiatePaymentRequest, clientToken, siteId, forceExperimentalVersion);
    }

    @PostMapping("/{provider}/payments/periodic/uk/initiate")
    public ExternalInitiateUkDomesticPaymentResponseDTO createPeriodicPayment(
            @PathVariable final String provider,
            @RequestParam final boolean forceExperimentalVersion,
            @VerifiedClientToken(restrictedTo = {
                    SERVICE_API_GATEWAY, SERVICE_CLIENT_GATEWAY, SERVICE_CONSENT_STARTER
            }) final ClientToken clientToken,
            @RequestHeader(value = "site_id") UUID siteId,
            @Valid @RequestBody final InitiateUkPeriodicPaymentRequestDTO initiatePaymentRequest) {
        clientTokenVerificationService.verify(clientToken, initiatePaymentRequest.getAuthenticationMeansReference());
        return paymentService.initiatePeriodicPayment(provider, initiatePaymentRequest, clientToken, siteId, forceExperimentalVersion);
    }
}
