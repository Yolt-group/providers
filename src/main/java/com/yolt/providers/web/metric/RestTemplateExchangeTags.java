package com.yolt.providers.web.metric;

import io.micrometer.core.instrument.Tag;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Marker;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

import static net.logstash.logback.marker.Markers.append;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RestTemplateExchangeTags {

    private static final Marker RDD_MARKER = append("raw-data", "true");

    private static final String TAG_HTTP_STATUS_DESC = "http_status_desc";
    private static final String TAG_HTTP_STATUS = "http_status";
    private static final String TAG_PROVIDER = "provider";
    private static final String TAG_METHOD = "method";

    private static final Tag HTTP_STATUS_DESC_INFORMATIONAL = Tag.of(TAG_HTTP_STATUS_DESC, "INFORMATIONAL");
    private static final Tag HTTP_STATUS_DESC_CLIENT_ERROR = Tag.of(TAG_HTTP_STATUS_DESC, "CLIENT_ERROR");
    private static final Tag HTTP_STATUS_DESC_SERVER_ERROR = Tag.of(TAG_HTTP_STATUS_DESC, "SERVER_ERROR");
    private static final Tag HTTP_STATUS_DESC_REDIRECTION = Tag.of(TAG_HTTP_STATUS_DESC, "REDIRECTION");
    private static final Tag HTTP_STATUS_DESC_SUCCESS = Tag.of(TAG_HTTP_STATUS_DESC, "SUCCESS");
    private static final Tag HTTP_STATUS_DESC_UNKNOWN = Tag.of(TAG_HTTP_STATUS_DESC, "-1");
    private static final Tag HTTP_STATUS_UNKNOWN = Tag.of(TAG_HTTP_STATUS, "-1");

    public static Tag method(HttpRequest request) {
        return Tag.of(TAG_METHOD, request.getMethod().name());
    }

    public static Tag provider(String providerKey) {
        String provider = providerKey.isEmpty() ? "" : providerKey;
        return Tag.of(TAG_PROVIDER, provider);
    }

    public static Tag status(ClientHttpResponse response) {
        try {
            if (response == null) {
                return HTTP_STATUS_UNKNOWN;
            } else {
                HttpStatus.valueOf(response.getRawStatusCode());
                return Tag.of(TAG_HTTP_STATUS, response.getStatusCode().toString());
            }
        } catch (IllegalArgumentException e) {
            log.debug(RDD_MARKER, e.getMessage());
            return HTTP_STATUS_UNKNOWN;
        } catch (IOException e) {
            log.warn("Exception processing metric tag %s" + TAG_HTTP_STATUS);
            return HTTP_STATUS_UNKNOWN;
        }
    }

    public static Tag statusDesc(ClientHttpResponse response) {
        try {
            if (response == null) {
                return HTTP_STATUS_DESC_UNKNOWN;
            } else {
                HttpStatus.valueOf(response.getRawStatusCode());

                HttpStatus status = response.getStatusCode();
                if (status.is1xxInformational()) {
                    return HTTP_STATUS_DESC_INFORMATIONAL;
                } else if (status.is2xxSuccessful()) {
                    return HTTP_STATUS_DESC_SUCCESS;
                } else if (status.is3xxRedirection()) {
                    return HTTP_STATUS_DESC_REDIRECTION;
                } else {
                    return status.is4xxClientError() ? HTTP_STATUS_DESC_CLIENT_ERROR : HTTP_STATUS_DESC_SERVER_ERROR;
                }
            }
        } catch (IllegalArgumentException e) {
            log.debug(RDD_MARKER, e.getMessage());
            return HTTP_STATUS_DESC_UNKNOWN;
        } catch (IOException e) {
            log.warn("Exception processing metric tag %s" + TAG_HTTP_STATUS_DESC);
            return HTTP_STATUS_DESC_UNKNOWN;
        }
    }
}