package com.yolt.providers.web.metric;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
public class MetricsClientHttpRequestInterceptor implements ClientHttpRequestInterceptor, Ordered {

    private static final String TIMER_METRIC_NAME = "restclient.providers.request.duration";
    private static final String TIMER_METRIC_DESC = "Timer of URL Providers operations";

    /**
     * For explanation please consult README section dedicated to order of interceptors execution
     */
    public static final int ORDER = 300;

    private final String providerKey;
    private final MeterRegistry meterRegistry;
    private final RestTemplateExchangeTagsProvider restTemplateExchangeTags;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        long startTime = System.nanoTime();
        ClientHttpResponse response = null;
        try {
            response = execution.execute(request, body);
            return response;
        } finally {
            getTimeBuilder(providerKey, request, response).register(meterRegistry)
                    .record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
        }
    }

    private Timer.Builder getTimeBuilder(String providerKey,
                                         HttpRequest request,
                                         ClientHttpResponse response) {
        return Timer.builder(TIMER_METRIC_NAME)
                .tags(restTemplateExchangeTags.getTags(providerKey, request, response))
                .description(TIMER_METRIC_DESC);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}