package com.yolt.providers.web.service.domain;

import lombok.Value;

@Value
public class ConsentTestingResult {
    ConsentTestingMessage message;
    String consentPageUri;
}
