package com.yolt.providers.web.service.consenttesting;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import nl.ing.lovebird.providerdomain.ServiceType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "consent-testing")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConsentTestingProperties {

    private List<String> blacklistedAisProviders;
    private List<String> blacklistedPisProviders;

    public List<String> getBlacklistedProviders(ServiceType serviceType) {
        return serviceType == ServiceType.AIS ? blacklistedAisProviders : blacklistedPisProviders;
    }
}
