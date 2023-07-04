package com.yolt.providers.web.intercept;

import nl.ing.lovebird.clienttokens.AbstractClientToken;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.RequestLine;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ConsentTestingPublishingInterceptorTest {

    private static final String PROVIDER = "CAJA_RURAL";

    private RawDataProducer rawDataProducer = mock(RawDataProducer.class);

    private AbstractClientToken clientToken = mock(AbstractClientToken.class);

    private ConsentTestingPublishingInterceptor interceptor = new ConsentTestingPublishingInterceptor(PROVIDER, rawDataProducer, clientToken);

    @Test
    void shouldSendDataToKafkaWhenReceivedResponseWithOkOrRedirectHttpStatus() throws IOException, HttpException {
        //given
        String requestedUrl = "https://hubpsd2.redsys.es/api-oauth-xs2a/services/rest/ruralbank/authorize";
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(301);
        HttpResponse httpResponse = mock(HttpResponse.class);
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        RequestLine requestLine = mock(RequestLine.class);
        when(requestLine.getUri()).thenReturn(requestedUrl);
        HttpRequestWrapper requestWrapper = mock(HttpRequestWrapper.class);
        when(requestWrapper.getRequestLine()).thenReturn(requestLine);
        when(requestWrapper.getMethod()).thenReturn("GET");
        HttpContext context = mock(HttpContext.class);
        when(context.getAttribute("http.request")).thenReturn(requestWrapper);
        doNothing().when(rawDataProducer).sendDataAsync(RawDataSource.CONSENT_TESTING, "{}", PROVIDER, clientToken, "GET", requestedUrl);

        //when
        interceptor.process(httpResponse, context);

        //then
        verify(rawDataProducer).sendDataAsync(RawDataSource.CONSENT_TESTING, "{}", PROVIDER, clientToken, "GET", requestedUrl);
        verifyNoMoreInteractions(rawDataProducer);
    }

    @Test
    void shouldNotSendDataToKafkaWhenReceivedResponseWIthBadRequestHttpStatus() throws IOException, HttpException {
        //give
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(400);
        HttpResponse httpResponse = mock(HttpResponse.class);
        when(httpResponse.getStatusLine()).thenReturn(statusLine);

        //when
        interceptor.process(httpResponse, new BasicHttpContext());

        //then
        verifyNoInteractions(rawDataProducer);
    }
}
