package com.yolt.providers.web.authenticationmeans;

import lombok.RequiredArgsConstructor;
import nl.ing.lovebird.clienttokens.ClientGroupToken;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.clienttokens.annotations.VerifiedClientToken;
import nl.ing.lovebird.clienttokens.verification.ClientGroupIdVerificationService;
import nl.ing.lovebird.clienttokens.verification.ClientIdVerificationService;
import nl.ing.lovebird.providerdomain.ServiceType;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.UUID;

/**
 * Rest controller that can be used to manage authentication means and callback urls.
 * Each client could have multiple sub-clients/apps. These apps need different callbackUrls. On top of that, it should be configurable
 * what authentication means are used for the provider.
 */
@RestController
@RequestMapping("clients")
@RequiredArgsConstructor
class ClientAuthenticationMeansController {

    private final ClientAuthenticationMeansService clientAuthenticationMeansService;
    private final ClientIdVerificationService clientIdVerificationService;
    private final ClientAuthenticationMeansCleanupService cleanupService;
    private final ClientGroupIdVerificationService clientGroupIdVerificationService;
    private final ClientGroupAuthenticationMeansCleanupService clientGroupAuthenticationMeansCleanupService;

    @PostMapping("/{clientId}/provider-authentication-means/{serviceType}")
    public void saveProviderAuthenticationMeans(
            @RequestBody @Valid final ProviderAuthenticationMeans body,
            @PathVariable final UUID clientId,
            @PathVariable final ServiceType serviceType) {
        clientAuthenticationMeansService.saveProviderAuthenticationMeans(clientId, body, serviceType);
    }

    @PostMapping("/{clientId}/provider-authentication-means/batch")
    public void saveProviderAuthenticationMeans(
            @RequestBody @Valid final ProviderAuthenticationMeansBatch body,
            @VerifiedClientToken final ClientToken clientToken,
            @PathVariable final UUID clientId) {
        clientIdVerificationService.verify(clientToken, clientId);
        clientAuthenticationMeansService.saveProviderAuthenticationMeans(clientToken, body.getProvider(), body.getRedirectUrlIds(),
                body.getServiceTypes(), body.getScopes(), body.getAuthenticationMeans(), body.isIgnoreAutoOnboarding());
    }

    @PostMapping("/group/{clientGroupId}/provider-authentication-means/batch")
    public void saveProviderAuthenticationMeansForGroup(
            @RequestBody @Valid final ProviderAuthenticationMeansBatch body,
            @VerifiedClientToken final ClientGroupToken clientGroupToken,
            @PathVariable final UUID clientGroupId) {
        clientGroupIdVerificationService.verify(clientGroupToken, clientGroupId);
        clientAuthenticationMeansService.saveProviderAuthenticationMeans(clientGroupToken, body.getProvider(), body.getRedirectUrlIds(),
                body.getServiceTypes(), body.getScopes(), body.getAuthenticationMeans(), body.isIgnoreAutoOnboarding());
    }

    @PostMapping("/group/provider-authentication-means/batch")
    public void saveProviderAuthenticationMeans(
            @RequestBody @Valid final ProviderAuthenticationMeansBatch body,
            @VerifiedClientToken final ClientGroupToken clientGroupToken) {
        clientAuthenticationMeansService.saveProviderAuthenticationMeans(clientGroupToken, body.getProvider(), body.getRedirectUrlIds(),
                body.getServiceTypes(), body.getScopes(), body.getAuthenticationMeans(), body.isIgnoreAutoOnboarding());
    }

    @GetMapping("/group/redirect-urls/{redirectUrlId}/provider-authentication-means")
    public List<ProviderTypedAuthenticationMeans> getCensoredAuthenticationMeans(
            @PathVariable final UUID redirectUrlId,
            @RequestParam(required = false) final ServiceType serviceType,
            @VerifiedClientToken final ClientGroupToken clientGroupToken) {
        return clientAuthenticationMeansService.getCensoredAuthenticationMeans(clientGroupToken, redirectUrlId, serviceType);
    }

    @GetMapping("/{clientId}/redirect-urls/{redirectUrlId}/provider-authentication-means")
    public List<ProviderTypedAuthenticationMeans> getCensoredAuthenticationMeans(
            @PathVariable final UUID clientId,
            @PathVariable final UUID redirectUrlId,
            @RequestParam(required = false) final ServiceType serviceType) {
        return clientAuthenticationMeansService.getCensoredAuthenticationMeans(clientId, redirectUrlId, serviceType);
    }

    @GetMapping("/{clientId}/provider-authentication-means")
    public List<ProviderTypedAuthenticationMeans> getCensoredAuthenticationMeans(
            @PathVariable final UUID clientId) {
        return clientAuthenticationMeansService.getCensoredAuthenticationMeans(clientId);
    }

    @DeleteMapping("/{clientId}/redirect-urls/{redirectUrlId}/provider-authentication-means/{provider}/{serviceType}")
    public void delete(
            @VerifiedClientToken final ClientToken clientToken,
            @PathVariable final UUID clientId,
            @PathVariable final UUID redirectUrlId,
            @PathVariable final String provider,
            @PathVariable final ServiceType serviceType) {
        clientAuthenticationMeansService.delete(clientToken, clientId, redirectUrlId, serviceType, provider);
    }

    @DeleteMapping("/{clientId}/provider-authentication-means/{provider}")
    public void delete(
            @PathVariable final UUID clientId,
            @PathVariable final String provider) {
        clientAuthenticationMeansService.delete(clientId, provider);
    }

    @DeleteMapping("/group/redirect-urls/{redirectUrlId}/provider-authentication-means/{provider}/{serviceType}")
    public void delete(
            @VerifiedClientToken final ClientGroupToken clientGroupToken,
            @PathVariable final UUID redirectUrlId,
            @PathVariable final String provider,
            @PathVariable final ServiceType serviceType) {
        clientAuthenticationMeansService.delete(clientGroupToken, redirectUrlId, serviceType, provider);
    }

    @PostMapping("/{clientId}/{redirectUrlId}/import")
    public void importFromProviderAuthenticationMeans(@PathVariable final UUID clientId,
                                                      @PathVariable final UUID redirectUrlId,
                                                      @RequestParam final UUID toClientId,
                                                      @RequestParam final UUID toRedirectUrlId,
                                                      @RequestParam final String fromProvider,
                                                      @RequestParam final String toProvider,
                                                      @RequestParam final ServiceType fromProviderServiceType,
                                                      @RequestParam final ServiceType toProviderServiceType) {
        clientAuthenticationMeansService.importFromProviderAuthenticationMeans(
                new AuthenticationMeansReference(clientId, redirectUrlId),
                new AuthenticationMeansReference(toClientId, toRedirectUrlId),
                fromProvider,
                toProvider,
                fromProviderServiceType,
                toProviderServiceType);
    }

    @PostMapping("/{clientId}/{redirectUrlId}/import-to-non-existing-provider")
    public void importFromProviderAuthenticationMeansToAnyProviderKey(@PathVariable final UUID clientId,
                                                                      @PathVariable final UUID redirectUrlId,
                                                                      @RequestParam final UUID toClientId,
                                                                      @RequestParam final UUID toRedirectUrlId,
                                                                      @RequestParam final String fromProvider,
                                                                      @RequestParam final String toProvider,
                                                                      @RequestParam final ServiceType fromProviderServiceType,
                                                                      @RequestParam final ServiceType toProviderServiceType) {
        clientAuthenticationMeansService.importFromProviderAuthenticationMeansToNonExistingProviderKey(
                new AuthenticationMeansReference(clientId, redirectUrlId),
                new AuthenticationMeansReference(toClientId, toRedirectUrlId),
                fromProvider,
                toProvider,
                fromProviderServiceType,
                toProviderServiceType);
    }

    @PutMapping("/{clientId}/provider-authentication-means/cleanup")
    public void cleanupProviderAuthenticationMeans(
            @RequestBody @Valid final ProviderAuthenticationMeansCleanupDTO body,
            @VerifiedClientToken final ClientToken clientToken,
            @PathVariable final UUID clientId) {
        clientIdVerificationService.verify(clientToken, clientId);
        cleanupService.cleanupProviderAuthenticationMeans(clientToken, body.getProvider(), body.getRedirectUrlIds(),
                body.getServiceTypes(), body.isDryRun());
    }

    @PutMapping("/group/{clientGroupId}/provider-authentication-means/cleanup")
    public void cleanupProviderAuthenticationMeansForGroup(
            @RequestBody @Valid final ProviderAuthenticationMeansCleanupDTO body,
            @VerifiedClientToken final ClientGroupToken clientGroupToken,
            @PathVariable final UUID clientGroupId) {
        clientGroupIdVerificationService.verify(clientGroupToken, clientGroupId);
        clientGroupAuthenticationMeansCleanupService.cleanupProviderAuthenticationMeans(clientGroupToken, body);
    }

    @PostMapping("/group/{clientGroupId}/{redirectUrlId}/import")
    public void importFromProviderAuthenticationMeansForGroup(@PathVariable final UUID clientGroupId,
                                                              @PathVariable final UUID redirectUrlId,
                                                              @RequestParam final UUID toClientGroupId,
                                                              @RequestParam final UUID toRedirectUrlId,
                                                              @RequestParam final String fromProvider,
                                                              @RequestParam final String toProvider,
                                                              @RequestParam final ServiceType fromProviderServiceType,
                                                              @RequestParam final ServiceType toProviderServiceType) {
        clientAuthenticationMeansService.importFromProviderAuthenticationMeansForGroup(
                new AuthenticationMeansReference(null, clientGroupId, redirectUrlId),
                new AuthenticationMeansReference(null, toClientGroupId, toRedirectUrlId),
                fromProvider,
                toProvider,
                fromProviderServiceType,
                toProviderServiceType);
    }
}
