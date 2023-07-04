package com.yolt.providers.web.authenticationmeans;

import com.yolt.providers.common.domain.authenticationmeans.BasicAuthenticationMean;
import lombok.Data;
import nl.ing.lovebird.providerdomain.ServiceType;

import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
class ClientGroupRedirectUrlProviderClientConfiguration {

    @NotNull
    private final UUID clientGroupId;

    @NotNull
    private final UUID redirectUrlId;

    @NotNull
    private final ServiceType serviceType;

    @NotNull
    private final String provider;

    @NotNull
    private final Map<String, BasicAuthenticationMean> authenticationMeans;

    @NotNull
    private final Instant updated;
}
