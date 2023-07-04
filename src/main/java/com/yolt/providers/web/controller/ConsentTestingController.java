package com.yolt.providers.web.controller;

import com.yolt.providers.web.controller.dto.InvokeConsentTestingDTO;
import com.yolt.providers.web.service.consenttesting.ConsentTestingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.clienttokens.annotations.VerifiedClientToken;
import nl.ing.lovebird.providerdomain.ServiceType;
import org.springframework.web.bind.annotation.*;

import static com.yolt.providers.web.service.ServiceConstants.SERVICE_SITE_MANAGEMENT;

@Slf4j
@RestController
@RequestMapping
@RequiredArgsConstructor
public class ConsentTestingController {

    private final ConsentTestingService consentTestingService;

    @PostMapping("/clients/invoke-consent-tests")
    public void invokeConsentTesting(
            @RequestParam(required = false, defaultValue = "AIS") ServiceType serviceType,
            @RequestBody InvokeConsentTestingDTO invokeConsentTestingDTO,
            @VerifiedClientToken(restrictedTo = {SERVICE_SITE_MANAGEMENT}) ClientToken clientToken
    ) {
        var authMeansReference = invokeConsentTestingDTO.getAuthenticationMeansReference();
        log.info("Invoked consent testing with client-id {}, client-group-id {}, redirectUrlId {} and serviceType {}",
                authMeansReference.getClientId(),
                authMeansReference.getClientGroupId(),
                authMeansReference.getRedirectUrlId(),
                serviceType);
        consentTestingService.invokeConsentTesting(
                invokeConsentTestingDTO.getAuthenticationMeansReference(),
                clientToken,
                serviceType,
                invokeConsentTestingDTO.getBaseClientRedirectUrl());
    }
}
