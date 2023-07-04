package com.yolt.providers.dummy;

import com.yolt.providers.common.ais.url.UrlAutoOnboardingRequest;
import com.yolt.providers.common.domain.authenticationmeans.BasicAuthenticationMean;
import com.yolt.providers.common.domain.authenticationmeans.TypedAuthenticationMeans;
import com.yolt.providers.common.domain.autoonboarding.RegistrationOperation;
import com.yolt.providers.common.domain.consenttesting.ConsentValidityRules;
import com.yolt.providers.common.providerinterface.AutoOnboardingProvider;
import com.yolt.providers.common.versioning.ProviderVersion;

import java.util.HashMap;
import java.util.Map;

import static com.yolt.providers.common.versioning.ProviderVersion.VERSION_1;

public class DummyTestAutoOnboardingProvider extends DummyTestProvider implements AutoOnboardingProvider {

    @Override
    public Map<String, TypedAuthenticationMeans> getAutoConfiguredMeans() {
        return new HashMap<String, TypedAuthenticationMeans>() {{
            put(CLIENT_ID_NAME, TypedAuthenticationMeans.CLIENT_ID_UUID);
        }};
    }

    @Override
    public Map<String, BasicAuthenticationMean> autoConfigureMeans(final UrlAutoOnboardingRequest urlAutoOnboardingRequest) {
        final Map<String, BasicAuthenticationMean> mutableMeans = new HashMap<>(
                urlAutoOnboardingRequest.getAuthenticationMeans());

        if (urlAutoOnboardingRequest.getRegistrationOperation().equals(RegistrationOperation.CREATE)) {
            // Simulate auto configuring the mean declared above.
            getAutoConfiguredMeans().forEach((k, v) -> {
                        mutableMeans.put(k, new BasicAuthenticationMean(v.getType(), "testAutoOnboarding"));
                    }
            );
        }
        return mutableMeans;
    }

    @Override
    public String getProviderIdentifier() {
        return "TEST_IMPL_OPENBANKING_AUTOONBOARDING_MOCK";
    }

    @Override
    public ProviderVersion getVersion() {
        return VERSION_1;
    }

    @Override
    public ConsentValidityRules getConsentValidityRules() {
        return ConsentValidityRules.EMPTY_RULES_SET;
    }
}
