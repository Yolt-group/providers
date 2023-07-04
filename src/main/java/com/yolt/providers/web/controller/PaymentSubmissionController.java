package com.yolt.providers.web.controller;

import com.yolt.providers.common.pis.common.PaymentStatusResponseDTO;
import com.yolt.providers.common.pis.common.SubmitPaymentRequestDTO;
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
public class PaymentSubmissionController {

    private final ProviderUkDomesticPaymentService paymentService;
    private final ClientTokenVerificationService clientTokenVerificationService;

    @PostMapping("/{provider}/payments/submit")
    public PaymentStatusResponseDTO submitPayment(
            @PathVariable final String provider,
            @RequestParam final boolean forceExperimentalVersion,
            @VerifiedClientToken(restrictedTo = {
                    SERVICE_API_GATEWAY, SERVICE_CLIENT_GATEWAY, SERVICE_CONSENT_STARTER
            }) final ClientToken clientToken,
            @RequestHeader(value = "site_id") UUID siteId,
            @Valid @RequestBody final SubmitPaymentRequestDTO submitPaymentRequest) {
        clientTokenVerificationService.verify(clientToken, submitPaymentRequest.getAuthenticationMeansReference());
        return paymentService.submitSinglePayment(provider, submitPaymentRequest, clientToken, siteId, forceExperimentalVersion);
    }

    @PostMapping("/{provider}/payments/periodic/submit")
    public PaymentStatusResponseDTO submitPeriodicPayment(
            @PathVariable final String provider,
            @RequestParam final boolean forceExperimentalVersion,
            @VerifiedClientToken(restrictedTo = {
                    SERVICE_API_GATEWAY, SERVICE_CLIENT_GATEWAY, SERVICE_CONSENT_STARTER
            }) final ClientToken clientToken,
            @RequestHeader(value = "site_id") UUID siteId,
            @Valid @RequestBody final SubmitPaymentRequestDTO submitPaymentRequest) {
        clientTokenVerificationService.verify(clientToken, submitPaymentRequest.getAuthenticationMeansReference());
        return paymentService.submitPeriodicPayment(provider, submitPaymentRequest, clientToken, siteId, forceExperimentalVersion);
    }
}
