package com.yolt.providers.web.sitedetails.controller;

import com.yolt.providers.web.sitedetails.dto.ProvidersSites;
import com.yolt.providers.web.sitedetails.sites.SiteDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint for getting list of registered sited
 */
@RestController
@RequestMapping(value = "sites-details", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class SiteDetailsController {

    private final SiteDetailsService siteDetailsService;

    @GetMapping
    ResponseEntity<ProvidersSites> getRegisteredProvidersSites() {
        return ResponseEntity.ok(new ProvidersSites(
                siteDetailsService.getAisSiteDetails(),
                siteDetailsService.getAisSiteDetails(),
                siteDetailsService.getPisSiteDetails()));
    }
}
