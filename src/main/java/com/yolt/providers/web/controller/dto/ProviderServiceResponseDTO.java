package com.yolt.providers.web.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import nl.ing.lovebird.providerdomain.ProviderAccountDTO;
import nl.ing.lovebird.providershared.ProviderServiceResponseStatusValue;

import javax.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Data
public class ProviderServiceResponseDTO {

    private final UUID providerRequestId;

    private final List<ProviderAccountDTO> accounts;

    private final ProviderServiceResponseStatusValue providerServiceResponseStatus;

    public ProviderServiceResponseDTO(@JsonProperty("accounts") final List<ProviderAccountDTO> accounts,
                                      @JsonProperty("providerServiceResponseStatus") final ProviderServiceResponseStatusValue providerServiceResponseStatus,
                                      @JsonProperty("providerRequestId") final UUID providerRequestId) {
        this.accounts = accounts;
        this.providerServiceResponseStatus = providerServiceResponseStatus;
        this.providerRequestId = providerRequestId;
    }

    public static ProviderServiceResponseDTO getEmptyInstance(@NotNull final ProviderServiceResponseStatusValue providerServiceResponseStatus,
                                                              @NotNull final UUID providerRequestId) {
        return new ProviderServiceResponseDTO(Collections.emptyList(), providerServiceResponseStatus, providerRequestId);
    }

}

