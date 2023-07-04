package com.yolt.providers.web.service.domain;

import lombok.Value;
import nl.ing.lovebird.providerdomain.ServiceType;

import java.util.Map;

/**
 * Contains information about a provider such as the name and what services (AIS, PIS) it supports.
 */
@Value
public class ProviderInfo {
    private String displayName;
    private Map<ServiceType, ServiceInfo> services;
}
