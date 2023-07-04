package com.yolt.providers.web.service;

import com.yolt.providers.common.providerinterface.Provider;
import com.yolt.providers.web.exception.ProviderNotFoundException;
import com.yolt.providers.web.service.configuration.VersionProperties;
import com.yolt.providers.web.service.configuration.VersionType;
import com.yolt.providers.web.service.configuration.VersionedProviders;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.providerdomain.ServiceType;
import org.springframework.stereotype.Service;

import java.util.List;

import static nl.ing.lovebird.providerdomain.ServiceType.AIS;
import static nl.ing.lovebird.providerdomain.ServiceType.PIS;

@Service
@Slf4j
public class ProviderFactoryService {

    private final VersionedProviders versionedProviders;

    public ProviderFactoryService(final List<Provider> providers,
                                  final VersionProperties versionProperties) {
        versionedProviders = new VersionedProviders(versionProperties, providers);
    }

    public List<Provider> getStableProviders(final String providerId) {
        return versionedProviders.getStableProviders(providerId);
    }

    public List<Provider> getAllStableProviders() {
        return versionedProviders.getAllStableProviders();
    }

    public <T> T getProvider(final String providerId,
                             final Class<T> classType,
                             final ServiceType serviceType,
                             final VersionType versionType) {
        if (serviceType == null) {
            return getTypedProvider(providerId, classType, versionType);
        }
        Provider provider = versionedProviders.getProvider(providerId, serviceType, versionType);
        if (provider == null || !classType.isInstance(provider)) {
            throw new ProviderNotFoundException(String.format("Provider %s have no %s implementation of %s class", providerId, versionType, classType.getSimpleName()));
        }
        return classType.cast(provider);
    }

    private <T> T getTypedProvider(final String providerId, final Class<T> classType, final VersionType versionType) {
        Provider aisProvider = versionedProviders.getProvider(providerId, AIS, versionType);
        Provider pisProvider = versionedProviders.getProvider(providerId, PIS, versionType);

        if (classType.isInstance(aisProvider)) {
            return classType.cast(aisProvider);
        } else if (classType.isInstance(pisProvider)) {
            return classType.cast(pisProvider);
        }
        throw new ProviderNotFoundException(String.format("Provider %s have no %s implementation of %s class", providerId, versionType, classType.getSimpleName()));
    }
}