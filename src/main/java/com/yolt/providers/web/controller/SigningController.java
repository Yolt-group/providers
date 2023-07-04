package com.yolt.providers.web.controller;

import com.yolt.providers.web.service.SigningService;
import com.yolt.securityutils.signing.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.clienttokens.annotations.VerifiedClientToken;
import org.springframework.web.bind.annotation.*;

import static com.yolt.providers.web.service.ServiceConstants.SERVICE_ASSISTANCE_PORTAL_YTS;
import static com.yolt.providers.web.service.ServiceConstants.SERVICE_DEV_PORTAL;


@RestController
@RequestMapping
@RequiredArgsConstructor
public class SigningController {

    private static final String SIGNING_KEY_ID_NAME = "signing_key_id";

    private final SigningService signingService;

    @PostMapping("/N26/sign")
    public String signPayload(
            @RequestBody String payload,
            @RequestParam(SIGNING_KEY_ID_NAME) String signingKeyId,
            @VerifiedClientToken(restrictedTo = SERVICE_DEV_PORTAL) ClientToken clientToken) {
        return signingService.signPayload(payload, signingKeyId, SignatureAlgorithm.SHA256_WITH_RSA.getJvmAlgorithm(), clientToken);
    }

    @PostMapping("/Nationwide/sign")
    public String signNationwidePayload(
            @RequestBody String payload,
            @RequestParam(SIGNING_KEY_ID_NAME) String signingKeyId,
            @VerifiedClientToken(restrictedTo = SERVICE_ASSISTANCE_PORTAL_YTS) ClientToken clientToken) {
        return signingService.signNationwidePayload(payload, signingKeyId, SignatureAlgorithm.SHA256_WITH_RSA_PSS.getJvmAlgorithm(), clientToken);
    }
}
