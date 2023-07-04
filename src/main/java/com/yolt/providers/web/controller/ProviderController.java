package com.yolt.providers.web.controller;

import com.yolt.providers.common.domain.dynamic.AccessMeansOrStepDTO;
import com.yolt.providers.common.domain.dynamic.step.Step;
import com.yolt.providers.web.controller.dto.*;
import com.yolt.providers.web.service.ProviderService;
import lombok.RequiredArgsConstructor;
import nl.ing.lovebird.clienttokens.ClientUserToken;
import nl.ing.lovebird.clienttokens.annotations.VerifiedClientToken;
import nl.ing.lovebird.providershared.AccessMeansDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.yolt.providers.web.service.ServiceConstants.*;

/**
 * End-point for providers service.
 */
@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
@RequiredArgsConstructor
public class ProviderController {

    private final ProviderService providerService;

    @PostMapping("/{provider}/access-means/refresh")
    public AccessMeansDTO refreshAccessMeans(
            @PathVariable String provider,
            @RequestParam boolean forceExperimentalVersion,
            @RequestBody RefreshAccessMeansDTO refreshAccessMeansDTO,
            @RequestHeader(value = "site_id") UUID siteId,
            @VerifiedClientToken(restrictedTo = {
                    SERVICE_SITE_MANAGEMENT, SERVICE_API_GATEWAY, SERVICE_CLIENT_GATEWAY, SERVICE_DEV_PORTAL, SERVICE_YOLT_ASSISTANCE_PORTAL, SERVICE_ASSISTANCE_PORTAL_YTS
            }) ClientUserToken clientUserToken
    ) {
        return providerService.refreshAccessMeans(provider, refreshAccessMeansDTO, clientUserToken, siteId, forceExperimentalVersion);
    }

    @PostMapping("/v2/{provider}/access-means/create")
    public AccessMeansOrStepDTO createNewAccessMeansDynamic(
            @PathVariable String provider,
            @RequestParam final boolean forceExperimentalVersion,
            @RequestBody ApiCreateAccessMeansDTO apiCreateAccessMeansDTO,
            @RequestHeader(value = "site_id") UUID siteId,
            @VerifiedClientToken(restrictedTo = {
                    SERVICE_API_GATEWAY, SERVICE_CLIENT_GATEWAY, SERVICE_CONSENT_STARTER
            }) ClientUserToken clientUserToken
    ) {
        return providerService.createNewAccessMeans(provider, apiCreateAccessMeansDTO, clientUserToken, siteId, forceExperimentalVersion);
    }

    @PostMapping("/{provider}/access-means/create")
    public AccessMeansDTO createNewAccessMeans(
            @PathVariable String provider,
            @RequestParam boolean forceExperimentalVersion,
            @RequestBody ApiCreateAccessMeansDTO apiCreateAccessMeansDTO,
            @RequestHeader(value = "site_id") UUID siteId,
            @VerifiedClientToken(restrictedTo = {
                    SERVICE_API_GATEWAY, SERVICE_CLIENT_GATEWAY
            }) ClientUserToken clientUserToken
    ) {
        return providerService.createNewAccessMeans(provider, apiCreateAccessMeansDTO, clientUserToken, siteId, forceExperimentalVersion).getAccessMeans();
    }

    @PostMapping("/v2/{provider}/login-info")
    public Step getLoginInfoDynamic(
            @PathVariable String provider,
            @RequestParam boolean forceExperimentalVersion,
            @RequestBody ApiGetLoginDTO apiGetLoginDTO,
            @RequestHeader(value = "site_id") UUID siteId,
            @VerifiedClientToken(restrictedTo = {
                    SERVICE_API_GATEWAY, SERVICE_CLIENT_GATEWAY, SERVICE_CONSENT_STARTER
            }) ClientUserToken clientUserToken
    ) {
        return providerService.getLoginInfo(provider, apiGetLoginDTO, clientUserToken, siteId, forceExperimentalVersion);
    }

    @PostMapping("/{provider}/fetch-data")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void fetchData(
            @PathVariable String provider,
            @RequestParam boolean forceExperimentalVersion,
            @RequestBody ApiFetchDataDTO apiFetchDataDTO,
            @RequestHeader(value = "site_id") UUID siteId,
            @VerifiedClientToken(restrictedTo = {
                    SERVICE_SITE_MANAGEMENT, SERVICE_API_GATEWAY, SERVICE_CLIENT_GATEWAY, SERVICE_DEV_PORTAL, SERVICE_YOLT_ASSISTANCE_PORTAL, SERVICE_ASSISTANCE_PORTAL_YTS, SERVICE_CONSENT_STARTER
            }) ClientUserToken clientUserToken
    ) {
        providerService.fetchDataAsync(provider, apiFetchDataDTO, siteId, clientUserToken, forceExperimentalVersion);
    }

    @PostMapping("/{provider}/notify-user-site-delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void notifyUserSiteDelete(
            @PathVariable String provider,
            @RequestParam boolean forceExperimentalVersion,
            @RequestBody ApiNotifyUserSiteDeleteDTO apiNotifyUserSiteDeleteDTO,
            @RequestHeader(value = "site_id", required = false) UUID siteId,
            @VerifiedClientToken(restrictedTo = {
                    SERVICE_API_GATEWAY, SERVICE_CLIENT_GATEWAY, SERVICE_DEV_PORTAL, SERVICE_YOLT_ASSISTANCE_PORTAL, SERVICE_ASSISTANCE_PORTAL_YTS, SERVICE_SITE_MANAGEMENT
            }) ClientUserToken clientUserToken
    ) {
        providerService.notifyUserSiteDelete(provider, siteId, apiNotifyUserSiteDeleteDTO, clientUserToken, forceExperimentalVersion);
    }
}