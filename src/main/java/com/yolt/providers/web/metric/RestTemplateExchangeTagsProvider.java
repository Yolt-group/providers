package com.yolt.providers.web.metric;

import io.micrometer.core.instrument.Tag;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;

@FunctionalInterface
public interface RestTemplateExchangeTagsProvider {
    /**
     * Provides the tags to be associated with metrics that are recorded for the given
     * provider along with {@code request} and {@code response} exchange.
     *
     * @param provider the provider id
     * @param request  the request
     * @param response the response (may be {@code null} if the exchange failed)
     * @return the tags
     */
    Iterable<Tag> getTags(final String provider,
                          final HttpRequest request,
                          final ClientHttpResponse response);
}