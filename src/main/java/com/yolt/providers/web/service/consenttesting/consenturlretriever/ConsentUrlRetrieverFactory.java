package com.yolt.providers.web.service.consenttesting.consenturlretriever;

import com.yolt.providers.common.providerinterface.Provider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConsentUrlRetrieverFactory {

    private final List<ConsentUrlRetriever> consentUrlRetrievers;

    public ConsentUrlRetriever getConsentUrlRetriever(Provider provider) {
        return consentUrlRetrievers.stream()
                .filter(consentUrlRetriever -> consentUrlRetriever.supports(provider))
                .reduce((consentUrlRetriever, consentUrlRetriever2) ->
                        consentUrlRetriever.getPriority() > consentUrlRetriever2.getPriority() ? consentUrlRetriever : consentUrlRetriever2)
                .orElseThrow(() -> new IllegalStateException("CONSENT TESTING - Could not find consent url retriever supporting provider " + provider.getProviderIdentifier()));
    }
}
