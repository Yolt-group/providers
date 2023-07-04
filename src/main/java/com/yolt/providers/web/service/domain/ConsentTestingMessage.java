package com.yolt.providers.web.service.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum ConsentTestingMessage {

    NOT_GENERATED("Failed to generate the login page url"),
    GENERATED("The login page url was successfully generated, however could not obtain HTTP-2XX response"),
    STATUS_200("Received HTTP-200 response calling the login page url"),
    VALIDITY_RULES_CHECKED("Consent validity rules were successfully verified"),
    STATUS_200_EMPTY_VALIDITY_RULES("Consent page is generated but content cannot be checked due to empty validity rules");

    private final String message;
}
