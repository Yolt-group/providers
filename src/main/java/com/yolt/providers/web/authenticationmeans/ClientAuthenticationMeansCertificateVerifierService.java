package com.yolt.providers.web.authenticationmeans;

import com.yolt.providers.common.domain.authenticationmeans.BasicAuthenticationMean;
import com.yolt.providers.common.domain.authenticationmeans.TypedAuthenticationMeans;
import com.yolt.providers.common.domain.authenticationmeans.types.CertificatesChainPemType;
import com.yolt.providers.common.domain.authenticationmeans.types.PemType;
import com.yolt.providers.common.providerinterface.Provider;
import com.yolt.providers.common.util.KeyUtil;
import com.yolt.providers.web.authenticationmeans.startuplogging.AuthenticationMeansLoggingProperties;
import com.yolt.providers.web.service.ProviderFactoryService;
import com.yolt.providers.web.service.configuration.VersionType;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import nl.ing.lovebird.providerdomain.ServiceType;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import org.slf4j.Marker;
import org.springframework.stereotype.Service;

import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.yolt.providers.common.rest.logging.LoggingInterceptor.REQUEST_RESPONSE_DTO_BINDING_CALL_ID;
import static com.yolt.providers.common.rest.logging.RawDataType.MRDD;
import static java.time.temporal.ChronoUnit.DAYS;
import static net.logstash.logback.marker.Markers.append;

@Service
@Slf4j
@RequiredArgsConstructor
class ClientAuthenticationMeansCertificateVerifierService {

    private final Clock clock;
    private final ProviderFactoryService providerFactoryService;
    private final Set<ClientProviderReference> checkedClientProviderReferences = ConcurrentHashMap.newKeySet();
    private final AuthenticationMeansLoggingProperties loggingProperties;

    void checkExpirationOfCertificate(
            String provider,
            ServiceType serviceType,
            AuthenticationMeansReference authenticationMeansReference,
            Map<String, BasicAuthenticationMean> decryptedAuthenticationMeans
    ) {
        ClientProviderReference clientProviderReference = new ClientProviderReference(authenticationMeansReference, serviceType, provider);
        if (!checkedClientProviderReferences.contains(clientProviderReference)) {
            logAuthMeansToRDD(authenticationMeansReference, decryptedAuthenticationMeans, provider, serviceType);
            logExpiringCertificates(decryptedAuthenticationMeans, provider, serviceType);
            checkedClientProviderReferences.add(clientProviderReference);
        }
    }

    private void logAuthMeansToRDD(final AuthenticationMeansReference authenticationMeansReference,
                                   Map<String, BasicAuthenticationMean> authenticationMeans,
                                   String provider,
                                   ServiceType serviceType) {
        if (loggingProperties.getProviderIds().contains(provider)) {
            UUID clientId = authenticationMeansReference.getClientId();
            UUID clientGroupId = authenticationMeansReference.getClientGroupId();
            UUID redirectUrlId = authenticationMeansReference.getRedirectUrlId();
            String message = "Storing authentication means for provider: " + provider + " " + serviceType.name() +
                    (clientId == null ? "" : (" clientId " + clientId)) +
                    (clientGroupId == null ? "" : (" clientGroupId " + clientGroupId)) +
                    (redirectUrlId == null ? "" : (" redirectUrlId " + redirectUrlId));
            log.info(message);
            Marker authMeanMarker = append("raw-data", "true")
                    .and(append("raw-data-type", "AUTH_MEANS"))
                    .and(append("http-call-id", UUID.randomUUID()));
            log.debug(authMeanMarker, message + " Authentication Means: " + authenticationMeans.toString());
        }
    }

    private void logExpiringCertificates(Map<String, BasicAuthenticationMean> decryptedGroupAuthenticationMeans, String provider, ServiceType serviceType) {
        Map<String, TypedAuthenticationMeans> typedAuthenticationMeans = getAuthenticationMeansUsedByStableProvider(provider, serviceType);
        for (Map.Entry<String, BasicAuthenticationMean> entry : decryptedGroupAuthenticationMeans.entrySet()) {
            if (!typedAuthenticationMeans.containsKey(entry.getKey())) {
                continue;
            }
            String authenticationMeansName = entry.getKey();
            BasicAuthenticationMean basicAuthenticationMean = entry.getValue();
            checkExpirationDatesAndLog(authenticationMeansName, basicAuthenticationMean);
        }
    }

    private void checkExpirationDatesAndLog(String key, BasicAuthenticationMean basicAuthenticationMean) {
        try {
            if (basicAuthenticationMean.getType() instanceof PemType) {
                X509Certificate certificate = KeyUtil.createCertificateFromPemFormat(basicAuthenticationMean.getValue());
                logWarnIfExpirationOccursNearby(key, certificate);
            } else if (basicAuthenticationMean.getType() instanceof CertificatesChainPemType) {
                X509Certificate[] certificatesChainFromPemFormat = KeyUtil.createCertificatesChainFromPemFormat(basicAuthenticationMean.getValue());
                logWarnIfExpirationOccursNearby(key, certificatesChainFromPemFormat);
            }
        } catch (Exception e) {
            log.info("error converting certificate (this is just for monitoring purposes, so it shouldn't break anything) authenticationMean={}", key); //NOSHERIFF this is to signal us where we do not have certificate
        }
    }

    private Map<String, TypedAuthenticationMeans> getAuthenticationMeansUsedByStableProvider(String provider, ServiceType serviceType) {
        try {
            Provider typedProvider = providerFactoryService.getProvider(provider, Provider.class, serviceType, VersionType.STABLE);
            return Optional.ofNullable(typedProvider)
                    .map(Provider::getTypedAuthenticationMeans)
                    .orElse(Collections.emptyMap());
        } catch (Exception e) {
            log.warn("Cannot fetch provider with providerName={}, errorClass={}", provider, e.getClass().getName());
        }
        return Collections.emptyMap();
    }

    private void logWarnIfExpirationOccursNearby(
            String key,
            X509Certificate... certificates) {
        Instant warnThreshold = Instant.now(clock).plus(14, DAYS); // We set the warn threshold for 14 DAYS prior the expiration
        for (X509Certificate certificate : certificates) {
            Instant notAfterDate = certificate.getNotAfter().toInstant();
            if (notAfterDate.isBefore(warnThreshold)) {
                log.warn("Detected incoming expiration date for authentication means expiryDate={}, authenticationMeanName={}", notAfterDate, key); //NOSHERIFF we want to see what is expiring
            } else {
                log.info("Detected incoming expiration date for authentication means expiryDate={}, authenticationMeanName={}", notAfterDate, key); //NOSHERIFF we want to see what is expiring
            }
        }
    }

    @Value
    private static class ClientProviderReference {

        AuthenticationMeansReference authenticationMeansReference;
        ServiceType serviceType;
        String providerKey;
    }
}
