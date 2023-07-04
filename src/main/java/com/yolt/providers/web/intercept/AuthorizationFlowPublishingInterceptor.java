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

@RequiredArgsConstructor
public class AuthorizationFlowPublishingInterceptor implements ClientHttpRequestInterceptor, Ordered {

    private static final String UNKNOWN_PLACEHOLDER = "UNKNOWN";
    private static final String EMPTY_BODY = "{}";

    /**
     * For explanation please consult README section dedicated to order of interceptors execution
     */
    public static final int ORDER = 151;

    private final String providerKey;
    private final RawDataProducer rawDataProducer;
    private final AbstractClientToken clientToken;

    @Override
    public ClientHttpResponse intercept(final HttpRequest request, final byte[] body, final ClientHttpRequestExecution execution) throws IOException {
        final WrappedClientHttpResponse response = new WrappedClientHttpResponse(execution.execute(request, body));

        final String httpMethod = request.getMethod() == null ? UNKNOWN_PLACEHOLDER : request.getMethod().name();

        if (HttpStatus.OK.equals(response.getStatusCode())) {
            rawDataProducer.sendDataAsync(RawDataSource.AUTHORIZATION_FLOW, EMPTY_BODY, providerKey, clientToken, httpMethod, request.getURI().toString());
        }
        return response;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
