package com.yolt.providers.web.service;

import com.yolt.providers.common.domain.ProviderMetaData;
import com.yolt.providers.common.domain.authenticationmeans.AuthenticationMeanTypeKeyDTO;
import com.yolt.providers.common.domain.authenticationmeans.TypedAuthenticationMeans;
import com.yolt.providers.common.domain.authenticationmeans.keymaterial.KeyRequirements;
import com.yolt.providers.common.providerinterface.AutoOnboardingProvider;
import com.yolt.providers.common.providerinterface.Provider;
import com.yolt.providers.web.service.domain.ProviderInfo;
import com.yolt.providers.web.service.domain.ServiceInfo;
import lombok.RequiredArgsConstructor;
import nl.ing.lovebird.providerdomain.ServiceType;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;

@Service
@RequiredArgsConstructor
public class ProviderInfoService {

    private final ProviderFactoryService providerFactoryService;

    public Map<String, ProviderInfo> getProvidersInfo() {
        return providerFactoryService.getAllStableProviders().stream()
                .collect(groupingBy(Provider::getProviderIdentifier))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, it -> getProviderInfo(it.getValue())));
    }

    public Optional<ProviderInfo> getProviderInfo(String providerKey) {
        List<Provider> providers = providerFactoryService.getStableProviders(providerKey);

        if (providers.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(getProviderInfo(providers));
    }

    private ProviderInfo getProviderInfo(List<Provider> providers) {
        Map<ServiceType, ServiceInfo> services = new EnumMap<>(ServiceType.class);
        for (Provider provider : providers) {
            ProviderMetaData metadata = provider.getProviderMetadata();
            KeyRequirements signingKeyRequirements = provider.getSigningKeyRequirements().orElse(null);
            KeyRequirements transportKeyRequirements = provider.getTransportKeyRequirements().orElse(null);
            List<AuthenticationMeanTypeKeyDTO> authMeans = getTypedAuthenticationMeans(provider);
            ServiceInfo serviceInfo = new ServiceInfo(metadata, signingKeyRequirements, transportKeyRequirements, authMeans);
            services.put(provider.getServiceType(), serviceInfo);
        }
        String providerDisplayName = providers.get(0).getProviderIdentifierDisplayName();
        return new ProviderInfo(providerDisplayName, services);
    }

    private List<AuthenticationMeanTypeKeyDTO> getTypedAuthenticationMeans(final Provider provider) {
        try {
            Map<String, TypedAuthenticationMeans> requiredAuthenticationMeans = new HashMap<>(provider.getTypedAuthenticationMeans());
            if (provider instanceof AutoOnboardingProvider) {
                // Withhold any auto-configured authentication means
                Map<String, TypedAuthenticationMeans> autoConfiguredMeans =
                        ((AutoOnboardingProvider) provider).getAutoConfiguredMeans();
                autoConfiguredMeans.forEach(requiredAuthenticationMeans::remove);
            }
            return requiredAuthenticationMeans.entrySet().stream()
                    .map(typedAuthenticationMean ->
                            AuthenticationMeanTypeKeyDTO.builder()
                                    .key(typedAuthenticationMean.getKey())
                                    .displayName(typedAuthenticationMean.getValue().getDisplayName())
                                    .placeholder(typedAuthenticationMean.getValue().getType().getDisplayName())
                                    .regex(typedAuthenticationMean.getValue().getType().getRegex())
                                    .type(typedAuthenticationMean.getValue().getRendering().getValue())
                                    .build())
                    .collect(Collectors.toList());
        } catch (NotImplementedException e) {
            // Eventually all providers should have been migrated to implement the authenticationMeans method.
            // Then we can remove this and the default implementation in Providers (which throws the NotImplementedException)
            // When we return null it means that the Provider is not migrated yet to multi-tenant auth means and cannot be added yet through YAP.
            return null;
        }
    }
}