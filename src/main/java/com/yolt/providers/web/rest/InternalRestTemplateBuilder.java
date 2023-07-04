package com.yolt.providers.web.rest;

import nl.ing.lovebird.logging.MDCContextCreator;
import org.slf4j.MDC;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

/**
 * This class has been moved from Providers commons to make commons independent from lovebirdcommons
 */

@Component
public class InternalRestTemplateBuilder extends RestTemplateBuilder {

    public InternalRestTemplateBuilder(final RestTemplateCustomizer... customizers) {
        super(prepareRestTemplateCustomizers(customizers));
    }

    private static RestTemplateCustomizer[] prepareRestTemplateCustomizers(final RestTemplateCustomizer[] customizers) {
        RestTemplateCustomizer[] internalCustomizers = Arrays.copyOf(customizers, customizers.length + 1);
        internalCustomizers[internalCustomizers.length - 1] = restTemplate ->
                restTemplate.setInterceptors(Collections.singletonList((request, body, execution) -> {
                    attachSleuthHeaders(request.getHeaders());
                    return execution.execute(request, body);
                }));
        return internalCustomizers;
    }

    /**
     * Propagate this headers, that are usually attached by a Sleuth.
     * Due to Sleuth being default disabled in providers, we are adding these manually for now.
     * - request_trace_id
     * - cbms-profile-id
     * - service_call_id
     * - user-id
     * - client-user-id
     * - app_version
     * - site_id
     * - user_site_id
     * NOSHERRIF was used, because information added to MDC context is necessary to be logged in.
     */
    @Deprecated
    private static void attachSleuthHeaders(final HttpHeaders headers) {
        headers.set(MDCContextCreator.PROFILE_ID_HEADER_NAME, MDC.get(MDCContextCreator.PROFILE_ID_HEADER_NAME)); //NOSHERIFF
        headers.set(MDCContextCreator.APP_VERSION_HEADER_NAME_AND_MDC_KEY, MDC.get(MDCContextCreator.APP_VERSION_HEADER_NAME_AND_MDC_KEY)); //NOSHERIFF
        headers.set(MDCContextCreator.SITE_ID_MDC_KEY, MDC.get(MDCContextCreator.SITE_ID_MDC_KEY)); //NOSHERIFF
        headers.set(MDCContextCreator.USER_SITE_ID_MDC_KEY, MDC.get(MDCContextCreator.USER_SITE_ID_MDC_KEY)); //NOSHERIFF

        Optional.ofNullable(MDC.get(MDCContextCreator.USER_ID_HEADER_NAME)) //NOSHERIFF
                .or(() -> Optional.ofNullable(MDC.get(MDCContextCreator.USER_ID_MDC_KEY))) //NOSHERIFF
                .ifPresent(userId -> headers.set(MDCContextCreator.USER_ID_HEADER_NAME, userId));

        Optional.ofNullable(MDC.get(MDCContextCreator.CLIENT_USER_ID_HEADER_NAME)) //NOSHERIFF
                .or(() -> Optional.ofNullable(MDC.get(MDCContextCreator.CLIENT_USER_ID_MDC_KEY))) //NOSHERIFF
                .ifPresent(clientUserId -> headers.set(MDCContextCreator.CLIENT_USER_ID_HEADER_NAME, clientUserId));
    }
}
