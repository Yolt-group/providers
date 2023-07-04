package com.yolt.providers.web.service.consenttesting.consenturlretriever.ais;

import com.yolt.providers.common.domain.dynamic.step.RedirectStep;
import com.yolt.providers.common.domain.dynamic.step.Step;
import com.yolt.providers.common.providerinterface.Provider;
import com.yolt.providers.common.providerinterface.UrlDataProvider;
import com.yolt.providers.web.controller.dto.ApiGetLoginDTO;
import com.yolt.providers.web.service.ProviderService;
import com.yolt.providers.web.service.consenttesting.consenturlretriever.ConsentUrlRetriever;
import com.yolt.providers.web.sitedetails.sites.SiteDetailsService;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class UrlDataProviderConsentUrlRetriever extends ConsentUrlRetriever {

    private final ProviderService providerService;
    private final SiteDetailsService siteDetailsService;

    public UrlDataProviderConsentUrlRetriever(ProviderService providerService, SiteDetailsService siteDetailsService, @Value("${yolt.externalIpAddress}") String externalIpAddress) {
        super(externalIpAddress);
        this.providerService = providerService;
        this.siteDetailsService = siteDetailsService;
    }

    @Override
    public String retrieveConsentUrlForProvider(String providerIdentifier, AuthenticationMeansReference authenticationMeansReference, String baseRedirectUrl, ClientToken clientToken) {
        ApiGetLoginDTO apiGetLoginDTO = new ApiGetLoginDTO(baseRedirectUrl,
                UUID.randomUUID().toString(),
                authenticationMeansReference,
                null,
                externalIpAddress);
        var siteId = siteDetailsService.getMatchingSiteIdForProviderKey(providerIdentifier)
                .map(UUID::fromString)
                .orElse(FAKE_SITE_ID);

        Step loginInfo = providerService.getLoginInfo(providerIdentifier,
                apiGetLoginDTO,
                clientToken,
                siteId,
                false);

        if (!(loginInfo instanceof RedirectStep)) {
            throw new IllegalStateException("CONSENT TESTING - Only RedirectStep is supported");
        }

        return ((RedirectStep) loginInfo).getRedirectUrl();
    }

    @Override
    public boolean supports(Provider provider) {
        return provider instanceof UrlDataProvider;
    }

    @Override
    public int getPriority() {
        return HIGHEST_PRIORITY;
    }
}
