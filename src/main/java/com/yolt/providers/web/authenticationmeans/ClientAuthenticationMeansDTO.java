package com.yolt.providers.web.authenticationmeans;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import lombok.experimental.Accessors;
import nl.ing.lovebird.providerdomain.ServiceType;
import org.springframework.lang.Nullable;

import java.util.UUID;

@Data
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
class ClientAuthenticationMeansDTO {

    private UUID clientGroupId;

    private UUID clientId;


    @NonNull
    private String provider;

    /**
     * If present, this authentication means is *restricted to* this serviceType.
     */
    @Nullable
    private ServiceType serviceType;

    /**
     * If present, this authentication means is *restricted to* this redirectUrlId.
     */
    @Nullable
    private UUID redirectUrlId;
}
