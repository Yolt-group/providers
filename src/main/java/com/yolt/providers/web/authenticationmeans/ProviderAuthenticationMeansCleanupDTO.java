package com.yolt.providers.web.authenticationmeans;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import nl.ing.lovebird.providerdomain.ServiceType;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;

@Validated
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProviderAuthenticationMeansCleanupDTO {

    @NotNull
    private String provider;

    @NotEmpty
    private Set<UUID> redirectUrlIds;

    @NotEmpty
    private Set<ServiceType> serviceTypes;

    private boolean dryRun;
}
