package com.yolt.providers.web.intercept;

import nl.ing.lovebird.clienttokens.AbstractClientToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FetchDataPublishingInterceptorTest {

    private static final URI DEFAULT_URI = URI.create("http://localhost/accounts");
    private static final String PROVIDER = "ABN_AMRO";
    private static final String BODY = "raw-data";

    private FetchDataPublishingInterceptor interceptor;

    @Mock
    private RawDataProducer producerMock;

    @Spy
    private ClientHttpResponse responseSpy;

    @Mock
    private ClientHttpRequestExecution executionMock;

    @Captor
    private ArgumentCaptor<HttpRequest> httpRequestCaptor;

    @Captor
    private ArgumentCaptor<byte[]> bodyCaptor;

    @Mock
    private AbstractClientToken clientToken;

    @BeforeEach
    public void beforeEach() throws IOException {
        when(executionMock.execute(httpRequestCaptor.capture(), bodyCaptor.capture())).thenReturn(responseSpy);
        interceptor = new FetchDataPublishingInterceptor(PROVIDER, producerMock, clientToken);
    }

    @Test
    public void shouldNotPublishDataForNonFetchDataRequest() throws IOException {
        // given
        HttpRequest requestSpy = spy(HttpRequest.class);
        when(requestSpy.getMethod()).thenReturn(null);

        // when
        interceptor.intercept(requestSpy, null, executionMock);

        // then
        verifyNoMoreInteractions(producerMock);
    }

    @Test
    public void shouldPassProviderAndMethodOverWhenTheyArePresent() throws IOException {
        // given
        when(responseSpy.getStatusCode()).thenReturn(HttpStatus.OK);
        doNothing().when(producerMock).sendDataAsync(any(RawDataSource.class), anyString(), anyString(), any(AbstractClientToken.class), anyString(), anyString());
        when(responseSpy.getBody()).thenReturn(new ByteArrayInputStream(BODY.getBytes()));
        HttpRequest requestSpy = spy(HttpRequest.class);
        when(requestSpy.getMethod()).thenReturn(HttpMethod.GET);
        when(requestSpy.getURI()).thenReturn(DEFAULT_URI);

        // when
        interceptor.intercept(requestSpy, BODY.getBytes(), executionMock);

        // then
        assertThat(new String(bodyCaptor.getValue())).isEqualTo(BODY);
        verify(producerMock).sendDataAsync(RawDataSource.FETCH_DATA, BODY, PROVIDER, clientToken, "GET", DEFAULT_URI.toString());
        verifyNoMoreInteractions(producerMock);
    }

    @Test
    public void shouldPassUnknownOverWhenProviderAndMethodAreAbsent() throws IOException {
        // given
        when(responseSpy.getStatusCode()).thenReturn(HttpStatus.OK);
        doNothing().when(producerMock).sendDataAsync(any(RawDataSource.class), anyString(), anyString(), any(AbstractClientToken.class), anyString(), anyString());
        when(responseSpy.getBody()).thenReturn(new ByteArrayInputStream(BODY.getBytes()));
        HttpRequest requestSpy = spy(HttpRequest.class);
        when(requestSpy.getMethod()).thenReturn(null);
        when(requestSpy.getURI()).thenReturn(DEFAULT_URI);

        // when
        interceptor.intercept(requestSpy, BODY.getBytes(), executionMock);

        // then
        assertThat(new String(bodyCaptor.getValue())).isEqualTo(BODY);
        verify(producerMock).sendDataAsync(RawDataSource.FETCH_DATA, BODY, PROVIDER, clientToken, "UNKNOWN", DEFAULT_URI.toString());
        verifyNoMoreInteractions(producerMock);
    }

    @Test
    public void shouldNotPublishDataWhenResposneCodeIsNotOk() throws IOException {
        // given
        when(responseSpy.getStatusCode()).thenReturn(HttpStatus.NO_CONTENT);
        HttpRequest requestSpy = spy(HttpRequest.class);
        when(requestSpy.getMethod()).thenReturn(HttpMethod.GET);

        // when
        interceptor.intercept(requestSpy, BODY.getBytes(), executionMock);

        // then
        assertThat(new String(bodyCaptor.getValue())).isEqualTo(BODY);
        verifyNoMoreInteractions(producerMock);
    }
}
