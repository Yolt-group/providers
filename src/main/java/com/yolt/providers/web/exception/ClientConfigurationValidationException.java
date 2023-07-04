package com.yolt.providers.web.exception;

import nl.ing.lovebird.providerdomain.ServiceType;

import java.util.UUID;

/**
 * Exception to inform the client what was wrong.
 * Note that the message is presented to the client, and should only have information to help the client in order to get a correct request.
 */
public class ClientConfigurationValidationException extends RuntimeException {

    /**
     * This message will be presented in the response. You can notify the client about errors in the
     * response when trying to use given combination of clientId and redirectUrlId.
     *
     * @param clientId The clientId that is invalid when combined with other parameters.
     * @param redirectUrlId The redirectUrlId that is invalid when combined with other parameters.
     */
    public ClientConfigurationValidationException(UUID clientId, UUID redirectUrlId) {
        super("Could not find client redirect url with client-id: " + clientId + " and redirect-url-id: " + redirectUrlId);
    }

    /**
     * This message will be presented in the response. You can notify the client about errors in the
     * response when trying to use given combination of clientId and redirectUrlId, serviceType and provider name (key).
     *
     * @param clientId The clientId that is invalid when combined with other parameters.
     * @param redirectUrlId The redirectUrlId that is invalid when combined with other parameters.
     * @param serviceType The serviceType that is invalid when combined with other parameters.
     * @param provider The provider name (key) that is invalid when combined with other parameters.
     */
    public ClientConfigurationValidationException(UUID clientId, UUID redirectUrlId, ServiceType serviceType, String provider) {
        super("Could not find client authentication means with client-id: " + clientId + ", redirect-url-id: " + redirectUrlId +
                ", serviceType: " + serviceType + " and provider: " + provider);
    }
}
