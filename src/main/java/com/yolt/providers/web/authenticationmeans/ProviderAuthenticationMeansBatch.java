package com.yolt.providers.web.authenticationmeans;

import com.yolt.providers.common.domain.AuthenticationMeans;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import nl.ing.lovebird.providerdomain.ServiceType;
import nl.ing.lovebird.providerdomain.TokenScope;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;

/**
 * This object will be used when one set of authentication means will be registered for multiple redirectUrlIds and/or serviceTypes in once.
 */
@Validated
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProviderAuthenticationMeansBatch {

    @NotNull
    private String provider;

    @NotEmpty
    private Set<UUID> redirectUrlIds;

    @NotEmpty
    private Set<ServiceType> serviceTypes;

    @NotNull
    private Set<AuthenticationMeans> authenticationMeans;

    private Set<TokenScope> scopes;

    private boolean ignoreAutoOnboarding;
}
