package com.yolt.providers.web.controller;

import com.yolt.providers.common.pis.common.PaymentStatusResponseDTO;
import com.yolt.providers.common.pis.sepa.SepaPaymentStatusResponseDTO;
import com.yolt.providers.web.controller.dto.GetPaymentStatusRequestDTO;
import com.yolt.providers.web.service.ClientTokenVerificationService;
import com.yolt.providers.web.service.ProviderSepaPaymentService;
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
public class PaymentStatusController {

    private final ProviderSepaPaymentService sepaPaymentService;
    private final ProviderUkDomesticPaymentService ukDomesticPaymentService;
    private final ClientTokenVerificationService clientTokenVerificationService;

    /**
     * This endpoint is only used by site-management to cache the SEPA payment status updates with an automated/batch process.
     * Currently there is no plan of allowing our clients to directly access this endpoint. The idea is to
     * only update the payment status in the batch and let site-management work as a cache of the payment status.
     * <p>
     * This is why SITE_MANAGEMENT is the only allowed Client-Token requester for this endpoint.
     */
    @PostMapping("/{provider}/payments/SEPA/status")
    public SepaPaymentStatusResponseDTO getSepaPaymentStatus(@PathVariable String provider,
                                                             @RequestParam boolean forceExperimentalVersion,
                                                             @VerifiedClientToken(restrictedTo = {
                                                                     SERVICE_PIS,
                                                                     SERVICE_CLIENT_GATEWAY,
                                                                     SERVICE_ASSISTANCE_PORTAL_YTS,
                                                                     SERVICE_CONSENT_STARTER
                                                             }) final ClientToken clientToken,
                                                             @RequestHeader(value = "site_id") UUID siteId,
                                                             @Valid @RequestBody final GetPaymentStatusRequestDTO getPaymentStatusRequestDTO) {
        clientTokenVerificationService.verify(clientToken, getPaymentStatusRequestDTO.getAuthenticationMeansReference());
        return sepaPaymentService.getPaymentStatus(provider, getPaymentStatusRequestDTO, clientToken, siteId, forceExperimentalVersion);
    }

    /**
     * This endpoint is only used by site-management to cache the UK Domestic payment status updates with an automated/batch process.
     * Currently there is no plan of allowing our clients to directly access this endpoint. The idea is to
     * only update the payment status in the batch and let site-management work as a cache of the payment status.
     * <p>
     * This is why SITE_MANAGEMENT is the only allowed Client-Token requester for this endpoint.
     */
    @PostMapping("/{provider}/payments/UK/status")
    public PaymentStatusResponseDTO getUkPaymentStatus(@PathVariable String provider,
                                                       @RequestParam boolean forceExperimentalVersion,
                                                       @VerifiedClientToken(restrictedTo = {
                                                               SERVICE_PIS,
                                                               SERVICE_ASSISTANCE_PORTAL_YTS,
                                                               SERVICE_CONSENT_STARTER
                                                       }) final ClientToken clientToken,
                                                       @RequestHeader(value = "site_id") UUID siteId,
                                                       @Valid @RequestBody final GetPaymentStatusRequestDTO getPaymentStatusRequestDTO) {
        clientTokenVerificationService.verify(clientToken, getPaymentStatusRequestDTO.getAuthenticationMeansReference());
        return ukDomesticPaymentService.getPaymentStatus(provider, getPaymentStatusRequestDTO, clientToken, siteId, forceExperimentalVersion);
    }
}
