package com.yolt.providers.web.intercept;

import lombok.RequiredArgsConstructor;
import nl.ing.lovebird.clienttokens.AbstractClientToken;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.protocol.HttpContext;
import org.springframework.http.HttpStatus;

import java.io.IOException;

@RequiredArgsConstructor
public class ConsentTestingPublishingInterceptor implements HttpResponseInterceptor {

    private static final String EMPTY_BODY = "{}";
    private static final String HTTP_REQUEST_ATTRIBUTE_NAME = "http.request";

    private final String providerKey;
    private final RawDataProducer rawDataProducer;
    private final AbstractClientToken clientToken;

    @Override
    public void process(HttpResponse httpResponse, HttpContext httpContext) throws HttpException, IOException {
        if (isStatusCodeOkOrRedirect(httpResponse)) {
            HttpRequestWrapper requestWrapper = (HttpRequestWrapper) httpContext.getAttribute(HTTP_REQUEST_ATTRIBUTE_NAME);
            rawDataProducer.sendDataAsync(RawDataSource.CONSENT_TESTING, EMPTY_BODY, providerKey, clientToken, requestWrapper.getMethod(), requestWrapper.getRequestLine().getUri());
        }

    }

    private boolean isStatusCodeOkOrRedirect(HttpResponse httpResponse) {
        HttpStatus status = HttpStatus.valueOf(httpResponse.getStatusLine().getStatusCode());
        return status.is2xxSuccessful() || status.is3xxRedirection();
    }

}
