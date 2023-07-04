package com.yolt.providers.web.service.configuration;

import com.yolt.providers.common.providerinterface.Provider;
import com.yolt.providers.common.versioning.ProviderVersion;
import com.yolt.providers.web.exception.ProviderDuplicateException;
import com.yolt.providers.web.exception.ProviderNotFoundException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.providerdomain.ServiceType;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.yolt.providers.web.service.configuration.VersionType.STABLE;
import static nl.ing.lovebird.providerdomain.ServiceType.AIS;
import static nl.ing.lovebird.providerdomain.ServiceType.PIS;

@Slf4j
public class VersionedProviders {

    private final Map<ProviderIdKey, Provider> providers;
    private final VersionProperties properties;

    public VersionedProviders(final VersionProperties versionProperties, final List<Provider> availableProviders) {
        Set<ProviderIdKey> requiredProviders = retrieveRequiredProviders(versionProperties);
        providers = availableProviders.stream()
                .collect(Collectors.toMap(
                        provider -> new ProviderIdKey(provider.getProviderIdentifier(), provider.getServiceType(), provider.getVersion()),
                        Function.identity(),
                        (o, o2) -> {
                            throw new ProviderDuplicateException(o);
                        }));
        validate(providers, requiredProviders);
        properties = versionProperties;
    }

    /**
     * Method filters through all loaded providers and returns all stable implementations of provider with given providerIdentifier
     *
     * @param providerId providerIdentifier used to filter providers
     * @return list of all stable implementations of provider with given providerIdentifier
     */
    public List<Provider> getStableProviders(final String providerId) {
        return getAllStableProviders().stream()
                .filter(provider -> provider.getProviderIdentifier().equals(providerId))
                .collect(Collectors.toList());
    }

    /**
     * Method filters through all loaded providers and returns stable implementations of all providers
     *
     * @return list of all stable implementations of provider with given providerIdentifier
     */
    public List<Provider> getAllStableProviders() {
        return providers.entrySet()
                .stream()
                .filter(entry -> {
                    ProviderVersion currentProviderVersion = entry.getKey().getVersion();
                    Optional<ProviderVersion> requiredProviderVersion = getVersionForProvider(entry.getKey().providerKey, entry.getKey().getServiceType(), STABLE);
                    return requiredProviderVersion.isPresent() && currentProviderVersion.equals(requiredProviderVersion.get());
                })
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    public Provider getProvider(final String providerKey, final ServiceType serviceType, final VersionType versionType) {
        Optional<ProviderVersion> version = getVersionForProvider(providerKey, serviceType, versionType);
        if (version.isPresent()) {
            ProviderIdKey providerIdKey = new ProviderIdKey(providerKey, serviceType, version.get());
            return providers.get(providerIdKey);
        }
        return null;
    }

    private Optional<ProviderVersion> getVersionForProvider(final String providerKey, final ServiceType serviceType, final VersionType versionType) {
        if (properties.getProviders() == null || properties.getProviders().get(providerKey) == null) {
            String message = String.format("Provider: %s is not listed in properties", providerKey);
            throw new ProviderNotFoundException(message);
        }

        Function<VersionProperties.Version, Optional<ProviderVersion>> getVersion = versions -> {
            if (versions == null) {
                return Optional.empty();
            }
            switch (versionType) {
                case STABLE:
                    return Optional.of(versions.getStable());
                case EXPERIMENTAL:
                    return Optional.of(versions.getExperimental());
                default:
                    throw new IllegalStateException("Only STABLE or EXPERIMENTAL value are allowed here. Received value: " + versionType.name());
            }
        };

        VersionProperties.ProviderVersions versions = properties.getProviders().get(providerKey);
        switch (serviceType) {
            case AIS:
                return getVersion.apply(versions.getAis());
            case PIS:
                return getVersion.apply(versions.getPis());
            default:
                String message = String.format("Service type: %s is not supported", serviceType);
                throw new UnsupportedOperationException(message);
        }
    }

    private Set<ProviderIdKey> retrieveRequiredProviders(final VersionProperties versionProperties) {
        if (versionProperties.getProviders() == null || versionProperties.getProviders().isEmpty()) {
            return Collections.emptySet();
        }
        Set<ProviderIdKey> requiredProviders = new HashSet<>();
        for (Map.Entry<String, VersionProperties.ProviderVersions> providerEntry : versionProperties.getProviders().entrySet()) {
            VersionProperties.ProviderVersions value = providerEntry.getValue();
            requiredProviders.addAll(convertToProviderKeys(providerEntry.getKey(), AIS, value.getAis()));
            requiredProviders.addAll(convertToProviderKeys(providerEntry.getKey(), PIS, value.getPis()));
        }
        return requiredProviders;
    }

    private Set<ProviderIdKey> convertToProviderKeys(String providerKey, ServiceType serviceType, VersionProperties.Version version) {
        Set<ProviderIdKey> providerIdKeys = new HashSet<>();
        if (version == null) {
            return providerIdKeys;
        }
        providerIdKeys.add(new ProviderIdKey(providerKey, serviceType, version.getStable()));
        providerIdKeys.add(new ProviderIdKey(providerKey, serviceType, version.getExperimental()));
        return providerIdKeys;
    }

    private void validate(final Map<ProviderIdKey, Provider> providers, final Set<ProviderIdKey> requiredProviders) {
        for (Provider provider : providers.values()) {
            if (StringUtils.isEmpty(provider.getProviderIdentifier())) {
                throw new IllegalStateException(String.format("Class %s does not have provider identifier", provider.getClass().getSimpleName()));
            }
        }
        Set<ProviderIdKey> notMatchedPropertyProviders = new HashSet<>(requiredProviders);
        notMatchedPropertyProviders.removeAll(providers.keySet());
        if (!notMatchedPropertyProviders.isEmpty()) {
            throw new ProviderNotFoundException("Provider implementation is missing for: " + StringUtils.collectionToCommaDelimitedString(notMatchedPropertyProviders));
        }
        Set<ProviderIdKey> notUsedProviders = new HashSet(providers.keySet());
        notUsedProviders.removeAll(new HashSet(requiredProviders));
        if (!notUsedProviders.isEmpty()) {
            log.warn("There are not used providers: " + StringUtils.collectionToCommaDelimitedString(notUsedProviders)); //NOSHERIFF we log here Meta data about now used providers.
        }
    }

    @Data
    private class ProviderIdKey {

        private final String providerKey;
        private final ServiceType serviceType;
        private final ProviderVersion version;
    }
}