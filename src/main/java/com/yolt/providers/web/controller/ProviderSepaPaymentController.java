package com.yolt.providers.web.controller;

import com.yolt.providers.common.pis.sepa.LoginUrlAndStateDTO;
import com.yolt.providers.common.pis.sepa.SepaPaymentStatusResponseDTO;
import com.yolt.providers.web.controller.dto.ExternalInitiateSepaPaymentRequestDTO;
import com.yolt.providers.web.controller.dto.ExternalLoginUrlAndStateDTO;
import com.yolt.providers.web.controller.dto.InitiateSepaPaymentRequestDTO;
import com.yolt.providers.web.controller.dto.SubmitSepaPaymentRequestDTO;
import com.yolt.providers.web.service.ClientTokenVerificationService;
import com.yolt.providers.web.service.ProviderSepaPaymentService;
import lombok.RequiredArgsConstructor;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.clienttokens.annotations.VerifiedClientToken;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.UUID;

import static com.yolt.providers.web.service.ServiceConstants.*;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
@RequiredArgsConstructor
public class ProviderSepaPaymentController {

    private final ProviderSepaPaymentService providerSepaPaymentService;
    private final ClientTokenVerificationService clientTokenVerificationService;

    @PostMapping("/{provider}/payment/sepa/single/initiate")
    public ExternalLoginUrlAndStateDTO initiatePayment(
            @PathVariable final String provider,
            @RequestParam boolean forceExperimentalVersion,
            @VerifiedClientToken(restrictedTo = {
                    SERVICE_API_GATEWAY, SERVICE_CLIENT_GATEWAY, SERVICE_CONSENT_STARTER
            }) final ClientToken clientToken,
            @RequestHeader(value = "site_id") UUID siteId,
            @Valid @RequestBody final ExternalInitiateSepaPaymentRequestDTO initiateSepaPaymentRequestDTO) {
        clientTokenVerificationService.verify(clientToken, initiateSepaPaymentRequestDTO.getAuthenticationMeansReference());
        return providerSepaPaymentService.initiateSinglePayment(provider, initiateSepaPaymentRequestDTO, clientToken, siteId, forceExperimentalVersion);
    }

    @PostMapping("/{provider}/payment/sepa/single/submit")
    public SepaPaymentStatusResponseDTO submitPayment(
            @PathVariable final String provider,
            @RequestParam boolean forceExperimentalVersion,
            @VerifiedClientToken(restrictedTo = {
                    SERVICE_API_GATEWAY, SERVICE_CLIENT_GATEWAY, SERVICE_CONSENT_STARTER
            }) final ClientToken clientToken,
            @RequestHeader(value = "site_id") UUID siteId,
            @Valid @RequestBody final SubmitSepaPaymentRequestDTO submitSepaPaymentRequestDTO) {
        clientTokenVerificationService.verify(clientToken, submitSepaPaymentRequestDTO.getAuthenticationMeansReference());
        return providerSepaPaymentService.submitSinglePayment(provider, submitSepaPaymentRequestDTO, clientToken, siteId, forceExperimentalVersion);
    }

    @PostMapping("/{provider}/payment/sepa/periodic/initiate")
    public LoginUrlAndStateDTO initiatePeriodicPayment(
            @PathVariable final String provider,
            @RequestParam boolean forceExperimentalVersion,
            @VerifiedClientToken(restrictedTo = {
                    SERVICE_API_GATEWAY, SERVICE_CLIENT_GATEWAY, SERVICE_CONSENT_STARTER
            }) final ClientToken clientToken,
            @RequestHeader(value = "site_id") UUID siteId,
            @Valid @RequestBody final InitiateSepaPaymentRequestDTO initiateSepaPeriodicPaymentRequestDTO) {
        clientTokenVerificationService.verify(clientToken, initiateSepaPeriodicPaymentRequestDTO.getAuthenticationMeansReference());
        return providerSepaPaymentService.initiatePeriodicPayment(provider, initiateSepaPeriodicPaymentRequestDTO, clientToken, siteId, forceExperimentalVersion);
    }

    @PostMapping("/{provider}/payment/sepa/periodic/submit")
    public SepaPaymentStatusResponseDTO submitPeriodicPayment(
            @PathVariable final String provider,
            @RequestParam boolean forceExperimentalVersion,
            @VerifiedClientToken(restrictedTo = {
                    SERVICE_API_GATEWAY, SERVICE_CLIENT_GATEWAY, SERVICE_CONSENT_STARTER
            }) final ClientToken clientToken,
            @RequestHeader(value = "site_id") UUID siteId,
            @Valid @RequestBody final SubmitSepaPaymentRequestDTO submitSepaPaymentRequestDTO) {
        clientTokenVerificationService.verify(clientToken, submitSepaPaymentRequestDTO.getAuthenticationMeansReference());
        return providerSepaPaymentService.submitPeriodicPayment(provider, submitSepaPaymentRequestDTO, clientToken, siteId, forceExperimentalVersion);
    }
}
