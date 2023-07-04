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
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

import static com.yolt.providers.common.versioning.ProviderVersion.VERSION_1;

@Service
public class AnotherTestProvider implements UrlDataProvider {

    @Override
    public DataProviderResponse fetchData(UrlFetchDataRequest urlFetchData)
            throws ProviderFetchDataException, TokenInvalidException {
        return null;
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
        return "POLISH_API_MOCK";
    }

    @Override
    public String getProviderIdentifierDisplayName() {
        return "Polish API Mock";
    }

    public static final String CLIENT_ID_NAME = "clientId";
    public static final String AUDIENCE_NAME = "audience";
    public static final String INSTITUTION_ID_NAME = "institutionId";
    public static final String PUBLIC_KEY_NAME = "publicKey";
    public static final String PRIVATE_KEY_NAME = "privateKey";

    @Override
    public Map<String, TypedAuthenticationMeans> getTypedAuthenticationMeans() {
        //TODO: Implemented to test flow with adding authentication means for two separate providers -
        // not actual implementation - copied from OpenbankingTestImplProvider replace with correct when adapting to types authentication means
        Map<String, TypedAuthenticationMeans> authenticationMeans = new HashMap<>();
        authenticationMeans.put(AUDIENCE_NAME, TypedAuthenticationMeans.AUDIENCE_STRING);
        authenticationMeans.put(CLIENT_ID_NAME, TypedAuthenticationMeans.CLIENT_ID_UUID);
        authenticationMeans.put(INSTITUTION_ID_NAME, TypedAuthenticationMeans.INSTITUTION_ID_STRING);
        authenticationMeans.put(PRIVATE_KEY_NAME, TypedAuthenticationMeans.PRIVATE_KEY_PEM);
        authenticationMeans.put(PUBLIC_KEY_NAME, TypedAuthenticationMeans.PUBLIC_KEY_PEM);

        return authenticationMeans;
    }

    @Override
    public ConsentValidityRules getConsentValidityRules() {
        return ConsentValidityRules.EMPTY_RULES_SET;
    }

    @Override
    public ProviderVersion getVersion() {
        return VERSION_1;
    }
}
