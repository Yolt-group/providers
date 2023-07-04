package com.yolt.providers.web.service;

import com.yolt.providers.web.service.configuration.VersionType;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import static com.yolt.providers.web.service.configuration.VersionType.EXPERIMENTAL;
import static com.yolt.providers.web.service.configuration.VersionType.STABLE;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
class ProviderVersioningUtil {

    private static final String EXPERIMENTAL_VERSION_MDC_KEY = "experimental_version";

    /**
     * Retrieves experimental version based on flag describing if experimental version should be used.
     * @param forceExperimentalVersion - if set to true it will allways take experimental version
     * @return experimental or stable enum
     */
    static VersionType getVersionType(final boolean forceExperimentalVersion) {
        if (forceExperimentalVersion) {
            MDC.put(EXPERIMENTAL_VERSION_MDC_KEY, "true");
            return EXPERIMENTAL;
        }
        else {
            MDC.put(EXPERIMENTAL_VERSION_MDC_KEY, "false");
            return STABLE;
        }
    }
}
