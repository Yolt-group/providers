package com.yolt.providers.web.service.domain;

import com.yolt.providers.common.domain.ProviderMetaData;
import com.yolt.providers.common.domain.authenticationmeans.AuthenticationMeanTypeKeyDTO;
import com.yolt.providers.common.domain.authenticationmeans.keymaterial.KeyRequirements;
import lombok.Value;

import java.util.List;

/**
 * Contains information about a service (AIS, PIS) such as what authentication means are required.
 */
@Value
public class ServiceInfo {
    private final ProviderMetaData metaData;
    private final KeyRequirements signing;
    private final KeyRequirements transport;
    private final List<AuthenticationMeanTypeKeyDTO> authenticationMeans;
}
