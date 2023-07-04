package com.yolt.providers.web.exception;

import com.yolt.providers.common.providerinterface.Provider;

public class ProviderDuplicateException extends RuntimeException {

    public ProviderDuplicateException(final Provider provider) {
        super(String.format("Provider %s %s with version %s is duplicated", provider.getServiceType().name(), provider.getProviderIdentifier(), provider.getVersion()));
    }
}
