package com.yolt.providers.web.service.consenttesting.consenturlretriever;

import com.yolt.providers.common.providerinterface.Provider;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;

import java.util.UUID;

public abstract class ConsentUrlRetriever {

    public static final int HIGHEST_PRIORITY = Integer.MAX_VALUE;

    public static final UUID FAKE_SITE_ID = UUID.fromString("00000000-1234-4444-4321-000000000000");

    protected final String externalIpAddress;

    public ConsentUrlRetriever(String externalIpAddress) {
        this.externalIpAddress = externalIpAddress;
    }

    public abstract String retrieveConsentUrlForProvider(String providerIdentifier,
                                                         AuthenticationMeansReference authenticationMeansReference,
                                                         String baseRedirectUrl,
                                                         ClientToken clientToken);

    public abstract boolean supports(Provider provider);

    public abstract int getPriority();
}
