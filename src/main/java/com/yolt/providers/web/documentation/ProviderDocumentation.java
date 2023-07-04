package com.yolt.providers.web.documentation;

import lombok.Value;
import nl.ing.lovebird.providerdomain.ServiceType;

/**
 * Contains information about a provider internal documentation.
 */
@Value
public class ProviderDocumentation {
    String providerIdentifier;
    ServiceType serviceType;
    String encodedContent;
}
