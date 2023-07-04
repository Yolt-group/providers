package com.yolt.providers.web.service.configuration;

import com.yolt.providers.common.versioning.ProviderVersion;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Data
@AllArgsConstructor
@NoArgsConstructor
@ConfigurationProperties(prefix = "versioning")
public class VersionProperties {

    private Map<String, ProviderVersions> providers;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class ProviderVersions {

        private Version ais;
        private Version pis;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class Version {

        private ProviderVersion stable;
        private ProviderVersion experimental;
    }
}
