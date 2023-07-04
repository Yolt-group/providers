package com.yolt.providers.web.cryptography.trust;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import nl.ing.lovebird.logging.SemaEvent;
import org.slf4j.Marker;

@Getter(onMethod_ = @Override)
@RequiredArgsConstructor
class RotatedPeerCertificateSEMaEvent implements SemaEvent {

    private final String message;
    private final Marker markers;

}
