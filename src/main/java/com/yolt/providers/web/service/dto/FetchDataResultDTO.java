package com.yolt.providers.web.service.dto;

import lombok.Value;
import nl.ing.lovebird.providershared.ProviderServiceResponseStatusValue;

import java.util.UUID;

@Value
public class FetchDataResultDTO {
    private final UUID providerRequestId;
    private final ProviderServiceResponseStatusValue providerServiceResponseStatus;
}
