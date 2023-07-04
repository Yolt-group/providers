package com.yolt.providers.web.intercept;

import com.yolt.providers.common.rest.http.WrappedClientHttpResponse;
import lombok.RequiredArgsConstructor;
import nl.ing.lovebird.clienttokens.AbstractClientToken;
import org.springframework.core.Ordered;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Removes 'fetch-data' tag (if present) and publishes raw data to Kafka asynchronously
 */
@RequiredArgsConstructor
public class FetchDataPublishingInterceptor implements ClientHttpRequestInterceptor, Ordered {

    private static final String UNKNOWN_PLACEHOLDER = "UNKNOWN";

    /**
     * For explanation please consult README section dedicated to order of interceptors execution
     */
    public static final int ORDER = 150;

    private final String providerKey;
    private final RawDataProducer rawDataProducer;
    private final AbstractClientToken clientToken;

    @Override
    public ClientHttpResponse intercept(final HttpRequest request, final byte[] body, final ClientHttpRequestExecution execution) throws IOException {
        final WrappedClientHttpResponse response = new WrappedClientHttpResponse(execution.execute(request, body));

        final String httpMethod;
        if (request.getMethod() == null) {
            httpMethod = UNKNOWN_PLACEHOLDER;
        } else {
            httpMethod = request.getMethod().name();
        }

        if (HttpStatus.OK.equals(response.getStatusCode())) {
            rawDataProducer.sendDataAsync(RawDataSource.FETCH_DATA, new String(response.getBytes()), providerKey, clientToken, httpMethod, request.getURI().toString());
        }
        return response;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
