package com.yolt.providers.web.cryptography.transport;

import com.yolt.providers.common.rest.YoltProxySelectorBuilder;
import com.yolt.providers.web.cryptography.KeyService;
import com.yolt.providers.web.intercept.AuthorizationFlowPublishingInterceptor;
import com.yolt.providers.web.intercept.RawDataProducer;
import com.yolt.providers.web.metric.DefaultRestTemplateExchangeTagsProvider;
import com.yolt.providers.web.metric.MetricsClientHttpRequestInterceptor;
import com.yolt.providers.web.service.configuration.ProviderConnectionProperties;
import io.micrometer.core.instrument.MeterRegistry;
import nl.ing.lovebird.clienttokens.AbstractClientToken;
import org.springframework.context.ApplicationContext;
import org.springframework.http.client.ClientHttpRequestInterceptor;

import javax.net.ssl.X509TrustManager;
import java.util.Arrays;
import java.util.List;

public class AuthorizationFlowMtlsRestTemplateManager extends MutualTLSRestTemplateManager {

    private final String providerKey;
    private final RawDataProducer rawDataProducer;
    private final MeterRegistry meterRegistry;
    private final AbstractClientToken clientToken;

    public AuthorizationFlowMtlsRestTemplateManager(ProviderConnectionProperties providerConnectionProperties, ApplicationContext applicationContext, KeyService keyService, AbstractClientToken clientToken, X509TrustManager trustManager, YoltProxySelectorBuilder yoltProxySelectorBuilder, MeterRegistry meterRegistry, String providerKey, RawDataProducer rawDataProducer) {
        super(providerConnectionProperties, applicationContext, keyService, clientToken, trustManager, yoltProxySelectorBuilder, meterRegistry, providerKey);
        this.providerKey = providerKey;
        this.rawDataProducer = rawDataProducer;
        this.meterRegistry = meterRegistry;
        this.clientToken = clientToken;
    }

    @Override
    protected List<ClientHttpRequestInterceptor> getInterceptors() {
        return Arrays.asList(
                new AuthorizationFlowPublishingInterceptor(providerKey, rawDataProducer, clientToken),
                new MetricsClientHttpRequestInterceptor(providerKey, meterRegistry, new DefaultRestTemplateExchangeTagsProvider())
        );
    }
}
