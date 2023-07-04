package com.yolt.providers.web.authenticationmeans;

import com.yolt.providers.common.domain.authenticationmeans.BasicAuthenticationMean;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
class ClientProviderAuthenticationMeans {

    @NotNull
    private final UUID clientId;

    @NotNull
    private final String provider;

    @NotNull
    private final Map<String, BasicAuthenticationMean> authenticationMeans;

    @NotNull
    private final Instant updated;
}
