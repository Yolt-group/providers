package com.yolt.providers.web.service.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Data
@ConfigurationProperties(prefix = "yolt.providers.connection")
public class ProviderConnectionProperties {

    int maxTotal;
    int maxPerRoute;
    int validateAfterInactivityInMillis;
    int requestTimeoutInMillis;
    int connectTimeoutInMillis;
    int socketTimeoutInMillis;
}
