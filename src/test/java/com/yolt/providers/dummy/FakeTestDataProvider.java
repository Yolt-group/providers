package com.yolt.providers.dummy;

import com.yolt.providers.common.ais.DataProviderResponse;
import com.yolt.providers.common.ais.url.UrlCreateAccessMeansRequest;
import com.yolt.providers.common.ais.url.UrlFetchDataRequest;
import com.yolt.providers.common.ais.url.UrlGetLoginRequest;
import com.yolt.providers.common.ais.url.UrlRefreshAccessMeansRequest;
import com.yolt.providers.common.domain.authenticationmeans.TypedAuthenticationMeans;
import com.yolt.providers.common.domain.consenttesting.ConsentValidityRules;
import com.yolt.providers.common.domain.dynamic.AccessMeansOrStepDTO;
import com.yolt.providers.common.domain.dynamic.step.RedirectStep;
import com.yolt.providers.common.exception.ProviderFetchDataException;
import com.yolt.providers.common.exception.TokenInvalidException;
import com.yolt.providers.common.providerinterface.UrlDataProvider;
import com.yolt.providers.common.versioning.ProviderVersion;
import nl.ing.lovebird.providershared.AccessMeansDTO;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.stereotype.Service;

import java.util.Map;

import static com.yolt.providers.common.versioning.ProviderVersion.VERSION_1;

@Service
public class FakeTestDataProvider implements UrlDataProvider {

    @Override
    public DataProviderResponse fetchData(UrlFetchDataRequest urlFetchData)
            throws ProviderFetchDataException, TokenInvalidException {
        return null;
    }

    @Override
    public Map<String, TypedAuthenticationMeans> getTypedAuthenticationMeans() {
        throw new NotImplementedException();
    }

    @Override
    public ConsentValidityRules getConsentValidityRules() {
        return ConsentValidityRules.EMPTY_RULES_SET;
    }

    @Override
    public RedirectStep getLoginInfo(UrlGetLoginRequest urlGetLogin) {

        RedirectStep step = new RedirectStep("https://ingress.integration.yolt.io/stubs/yoltprovider/authorize?x=https://www.yolt.com/callback&" + urlGetLogin.getState());
        return step;
    }

    @Override
    public AccessMeansOrStepDTO createNewAccessMeans(UrlCreateAccessMeansRequest urlCreateAccessMeans) {
        return null;
    }

    @Override
    public AccessMeansDTO refreshAccessMeans(UrlRefreshAccessMeansRequest urlRefreshAccessMeans)
            throws TokenInvalidException {
        return null;
    }

    @Override
    public String getProviderIdentifier() {
        return "FAKE_TEST_PROVIDER";
    }

    @Override
    public String getProviderIdentifierDisplayName() {
        return "Fake Test Provider";
    }

    @Override
    public ProviderVersion getVersion() {
        return VERSION_1;
    }
}
