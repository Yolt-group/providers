package com.yolt.providers.web.authenticationmeans;

import com.yolt.providers.common.domain.authenticationmeans.BasicAuthenticationMean;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import nl.ing.lovebird.providerdomain.ServiceType;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;
import java.util.Map;

@Validated
@Data
@NoArgsConstructor
@AllArgsConstructor
class ProviderTypedAuthenticationMeans {

    @NotNull
    private String provider;

    @NotNull
    private ServiceType serviceType;

    @NotNull
    private Map<String, BasicAuthenticationMean> authenticationMeans;
}
