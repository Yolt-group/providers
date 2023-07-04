package com.yolt.providers.web.cryptography.transport;

import lombok.RequiredArgsConstructor;
import org.apache.http.HeaderElement;
import org.apache.http.HttpResponse;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;

/**
 * Custom {@link ConnectionKeepAliveStrategy} implementation which is almost the same as the default one
 * {@link DefaultConnectionKeepAliveStrategy} except the case where <b>Keep-Alive</b> HTTP header is not provided
 * in response - in that case this strategy returns <b>default keep-alive timeout in millis</b>
 * instead of <b>-1L</b> (timeout value <= 0 means keeping a connection alive indefinitely).
 * It is mainly used to prevent <b>Read timed out</b> or <b>Connection reset</b> errors.
 */
@RequiredArgsConstructor(staticName = "withDefaultKeepAliveTimeoutInMillis")
public class TimeLimitedConnectionKeepAliveStrategy implements ConnectionKeepAliveStrategy {

    private static final String KEEP_ALIVE_TIMEOUT_PARAMETER_NAME = "timeout";

    private final long defaultKeepAliveTimeoutInMillis;

    @Override
    public long getKeepAliveDuration(HttpResponse httpResponse, HttpContext httpContext) {
        BasicHeaderElementIterator it = new BasicHeaderElementIterator(httpResponse.headerIterator(HTTP.CONN_KEEP_ALIVE));
        while (it.hasNext()) {
            HeaderElement headerElement = it.nextElement();
            String param = headerElement.getName();
            String value = headerElement.getValue();
            if (value != null && param.equalsIgnoreCase(KEEP_ALIVE_TIMEOUT_PARAMETER_NAME)) {
                return Long.parseLong(value) * 1000;
            }
        }

        return defaultKeepAliveTimeoutInMillis;
    }
}
