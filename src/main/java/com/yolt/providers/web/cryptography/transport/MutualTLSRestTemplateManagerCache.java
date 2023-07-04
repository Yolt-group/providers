package com.yolt.providers.web.cryptography.transport;

import com.yolt.providers.common.cryptography.RestTemplateManager;
import com.yolt.providers.common.rest.YoltProxySelectorBuilder;
import com.yolt.providers.common.versioning.ProviderVersion;
import com.yolt.providers.web.cryptography.KeyService;
import com.yolt.providers.web.cryptography.trust.TrustManagerSupplier;
import com.yolt.providers.web.intercept.RawDataProducer;
import com.yolt.providers.web.service.configuration.ProviderConnectionProperties;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.clienttokens.AbstractClientToken;
import nl.ing.lovebird.clienttokens.ClientGroupToken;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.providerdomain.ServiceType;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import javax.net.ssl.X509TrustManager;

@Service
@Slf4j
public class MutualTLSRestTemplateManagerCache {

    private final KeyService keyService;
    private final X509TrustManager trustManager;
    private final YoltProxySelectorBuilder yoltProxySelectorBuilder;
    private final ProviderConnectionProperties providerConnectionProperties;
    private final RawDataProducer rawDataProducer;
    private final MeterRegistry registry;
    private final ApplicationContext applicationContext;

    public MutualTLSRestTemplateManagerCache(
            final KeyService keyService,
            final TrustManagerSupplier trustManagerSupplier,
            final YoltProxySelectorBuilder yoltProxySelectorBuilder,
            final ProviderConnectionProperties providerConnectionProperties,
            final RawDataProducer rawDataProducer,
            final MeterRegistry registry,
            final ApplicationContext applicationContext
    ) {
        this.keyService = keyService;
        this.trustManager = trustManagerSupplier.getTrustManager();
        this.yoltProxySelectorBuilder = yoltProxySelectorBuilder;
        this.providerConnectionProperties = providerConnectionProperties;
        this.rawDataProducer = rawDataProducer;
        this.registry = registry;
        this.applicationContext = applicationContext;
    }

    @Cacheable(cacheNames = "mutualTLSCache", key = "{#clientToken?.clientIdClaim, #serviceType, #providerKey, #isFetchData, #providerVersion}")
    public RestTemplateManager getForClientProvider(ClientToken clientToken, ServiceType serviceType, String providerKey, boolean isFetchData, ProviderVersion providerVersion) {
        return getRestTemplateManager(clientToken, serviceType, providerKey, isFetchData);
    }

    @Cacheable(cacheNames = "mutualTLSCache", key = "{#clientToken?.clientGroupIdClaim, #serviceType, #providerKey, #isFetchData, #providerVersion}")
    public RestTemplateManager getForClientGroupProvider(ClientGroupToken clientToken, ServiceType serviceType, String providerKey, boolean isFetchData, ProviderVersion providerVersion) {
        return getRestTemplateManager(clientToken, serviceType, providerKey, isFetchData);
    }

    private RestTemplateManager getRestTemplateManager(AbstractClientToken clientToken, ServiceType serviceType, String providerKey, boolean isFetchData) {
        final RestTemplateManager restTemplateManager;
        if (isFetchData) {
            restTemplateManager = new DataAwareMtlsRestTemplateManager(
                    providerConnectionProperties, applicationContext, keyService, clientToken, trustManager, yoltProxySelectorBuilder, registry, providerKey, rawDataProducer
            );
        } else {
            restTemplateManager = new AuthorizationFlowMtlsRestTemplateManager(
                    providerConnectionProperties, applicationContext, keyService, clientToken, trustManager, yoltProxySelectorBuilder, registry, providerKey, rawDataProducer
            );
        }
        log.info("Saved a new RestTemplateManager in the cache for client-token: {} and provider: {} and service type: {} and isFetchData: {}",
                clientToken.getSubject(), providerKey, serviceType, isFetchData);  // NOSHERIFF we want to see for which subject restTemplate was cached and if it was for fetch data flow.
        return restTemplateManager;
    }
}
