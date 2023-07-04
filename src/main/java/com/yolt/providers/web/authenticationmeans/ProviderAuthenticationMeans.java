package com.yolt.providers.web.authenticationmeans;

import com.yolt.providers.common.domain.AuthenticationMeans;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;
import java.util.Set;

@Validated
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProviderAuthenticationMeans {

    @NotNull
    private String provider;

    @NotNull
    private Set<AuthenticationMeans> authenticationMeans;
}
