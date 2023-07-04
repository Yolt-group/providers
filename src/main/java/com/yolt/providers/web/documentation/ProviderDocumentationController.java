package com.yolt.providers.web.documentation;

import lombok.RequiredArgsConstructor;
import nl.ing.lovebird.providerdomain.ServiceType;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * Endpoints for getting provider documentation (only for internal usage).
 */
@RestController
@RequestMapping(value = "internal-documentation", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ProviderDocumentationController {

    private final ProviderDocumentationService providerDocumentationService;

    @GetMapping
    public List<ProviderDocumentation> getProvidersInternalDocumentation() {
        return providerDocumentationService.getProvidersDocumentation();
    }

    @GetMapping("provider/{providerIdentifier}/serviceType/{serviceType}")
    public Optional<ProviderDocumentation> getProvidersInternalDocumentation(@PathVariable("providerIdentifier") String providerIdentifier,
                                                                             @PathVariable("serviceType") ServiceType serviceType) {
        return providerDocumentationService.getProviderDocumentation(providerIdentifier, serviceType);
    }
}