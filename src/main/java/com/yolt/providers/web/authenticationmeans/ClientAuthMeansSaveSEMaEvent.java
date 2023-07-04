package com.yolt.providers.web.authenticationmeans;

import lombok.Getter;
import net.logstash.logback.marker.Markers;
import nl.ing.lovebird.logging.SemaEvent;
import nl.ing.lovebird.providerdomain.ServiceType;
import org.slf4j.Marker;

import java.util.HashMap;
import java.util.UUID;

@Getter(onMethod_ = @Override)
public class ClientAuthMeansSaveSEMaEvent implements SemaEvent {
    private final String message;
    private final Marker markers;

    public ClientAuthMeansSaveSEMaEvent(UUID clientId,
                                        UUID redirectUrlId,
                                        ServiceType serviceType,
                                        String providerKey) {
        HashMap<String, String> markersEntries = new HashMap();
        markersEntries.put("clientId", clientId.toString());
        markersEntries.put("redirectUrlId", redirectUrlId.toString());
        markersEntries.put("serviceType", serviceType.name());
        markersEntries.put("providerKey", providerKey);
        markers = Markers.appendEntries(markersEntries);

        message = "Saving client auth means without client token:\n"
                + "clientId: " + clientId + "\n"
                + "redirectUrlId: " + redirectUrlId + "\n"
                + "serviceType: " + serviceType + "\n"
                + "providerKey: " + providerKey + ".";
    }
}
