package com.yolt.providers.web.metric;

import io.micrometer.core.instrument.Tag;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;

import java.util.Arrays;

public class DefaultRestTemplateExchangeTagsProvider implements RestTemplateExchangeTagsProvider {

    @Override
    public Iterable<Tag> getTags(final String provider,
                                 final HttpRequest request,
                                 final ClientHttpResponse response) {

        return Arrays.asList(
                RestTemplateExchangeTags.method(request),
                RestTemplateExchangeTags.status(response),
                RestTemplateExchangeTags.statusDesc(response),
                RestTemplateExchangeTags.provider(provider));
    }
}