package com.yolt.providers.web;

import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class TomcatWebServerCustomizer implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        // customize the factory here
        factory.addConnectorCustomizers(connector -> {

            ProtocolHandler protocolHandler = connector.getProtocolHandler();
            if (protocolHandler instanceof AbstractHttp11Protocol) {
                log.info("disabled connection keep alive");
                ((AbstractHttp11Protocol) protocolHandler).setMaxKeepAliveRequests(1); // disable keep-alive and pipelining
            }

        });
    }
}