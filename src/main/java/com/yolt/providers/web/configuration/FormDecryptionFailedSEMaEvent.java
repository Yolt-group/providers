package com.yolt.providers.web.configuration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import nl.ing.lovebird.logging.SemaEvent;
import org.slf4j.Marker;

@Getter(onMethod_ = @Override)
@RequiredArgsConstructor
class FormDecryptionFailedSEMaEvent implements SemaEvent {

    private final String message;
    private final Marker markers;
}