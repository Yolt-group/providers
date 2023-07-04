package com.yolt.providers.web.cryptography.trust;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * This class requires the {@link Profile} "truststore" to be present, otherwise it won't load.
 * This is a hack of sorts: we have a file in `config-server` called application-truststore.yml,
 * this file contains the certificates.
 */
@Data
@Validated
@Configuration
@Profile("truststore")
@ConfigurationProperties(prefix = "yolt.truststore")
class TruststoreConfiguration {
    @Valid
    @NotNull
    private List<TruststoreConfigurationEntry> entries;
}
