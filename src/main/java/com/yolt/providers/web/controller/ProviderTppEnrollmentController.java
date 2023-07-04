package com.yolt.providers.web.controller;

import com.yolt.providers.common.domain.RestTemplateManagerConfiguration;
import com.yolt.providers.common.providerinterface.UrlDataProvider;
import com.yolt.providers.common.rest.ExternalRestTemplateBuilderFactory;
import com.yolt.providers.rabobank.JwsRequestTppEnrollment;
import com.yolt.providers.web.controller.dto.RabobankTppEnrollmentDTO;
import com.yolt.providers.web.cryptography.signing.JcaSigner;
import com.yolt.providers.web.cryptography.signing.JcaSignerFactory;
import com.yolt.providers.web.cryptography.transport.MutualTLSRestTemplateManagerCache;
import com.yolt.providers.web.service.ProviderFactoryService;
import com.yolt.providers.web.service.configuration.VersionType;
import com.yolt.securityutils.certificate.CertificateReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.clienttokens.annotations.VerifiedClientToken;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.validation.Valid;
import java.time.Clock;
import java.time.Instant;

import static com.yolt.providers.web.service.ServiceConstants.*;
import static java.time.temporal.ChronoUnit.DAYS;
import static nl.ing.lovebird.providerdomain.ServiceType.AIS;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.ResponseEntity.accepted;
import static org.springframework.http.ResponseEntity.status;

@Slf4j
@Validated
@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
@RequiredArgsConstructor
public class ProviderTppEnrollmentController {

    private final Clock clock;
    private final ProviderFactoryService providerFactoryService;
    private final MutualTLSRestTemplateManagerCache restTemplateManagerCache;
    private final JcaSignerFactory jcaSignerFactory;

    /**
     * Expose tpp enrollment functionality to YAP for Rabobank.
     * <p>
     * https://developer.rabobank.nl/jws-request-tpp-enrollment
     * https://developer.rabobank.nl/reference/third-party-providers/1-0-1
     *
     * @return
     */
    @PostMapping("/RABOBANK/tpp-enrollment")
    public ResponseEntity<Void> rabobankTppEnrollment(
            @VerifiedClientToken(restrictedTo = {SERVICE_YOLT_ASSISTANCE_PORTAL, SERVICE_DEV_PORTAL, SERVICE_ASSISTANCE_PORTAL_YTS}) ClientToken clientToken,
            @Valid @RequestBody RabobankTppEnrollmentDTO rabobankTppEnrollmentDTO
    ) {
        RestTemplate restTemplate = restTemplateManagerCache.getForClientProvider(
                clientToken,
                AIS,
                "RABOBANK",
                false,
                providerFactoryService.getProvider("RABOBANK", UrlDataProvider.class, AIS, VersionType.STABLE).getVersion()
        ).manage(new RestTemplateManagerConfiguration(rabobankTppEnrollmentDTO.getKid(),
                CertificateReader.readFromPemString(rabobankTppEnrollmentDTO.getQseal()),
                ExternalRestTemplateBuilderFactory::build));
        JcaSigner signer = jcaSignerFactory.getForClientToken(clientToken);

        // Give Rabobank a week to process our request.  Should be sufficient.
        Instant expiryTime = Instant.now(clock).plus(7, DAYS);

        boolean result = JwsRequestTppEnrollment.sendJwsRequestTppEnrollment(
                rabobankTppEnrollmentDTO.getEmail(),
                CertificateReader.readFromPemString(rabobankTppEnrollmentDTO.getQseal()),
                rabobankTppEnrollmentDTO.getKid(),
                signer,
                restTemplate,
                expiryTime,
                clock
        );

        if (!result) {
            return status(INTERNAL_SERVER_ERROR).build();
        }

        return accepted().build();
    }

}
