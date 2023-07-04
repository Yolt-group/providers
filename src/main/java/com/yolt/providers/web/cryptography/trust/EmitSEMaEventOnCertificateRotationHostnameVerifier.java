package com.yolt.providers.web.cryptography.trust;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import net.logstash.logback.marker.Markers;
import nl.ing.lovebird.logging.SemaEventLogger;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class EmitSEMaEventOnCertificateRotationHostnameVerifier implements HostnameVerifier {

    private final HostnameVerifier delegate;

    private Map<String, Set<X509Certificate>> perHostTrustOnFirstUse = new ConcurrentHashMap<>();

    @Override
    @SneakyThrows
    public boolean verify(final String hostname, final SSLSession session) {
        // If the actual hostname verifier doesn't trust the host: all bets are off.
        if (!delegate.verify(hostname, session)) {
            return false;
        }

        // The server certificate.
        final X509Certificate offeredServerCertificate = (X509Certificate) session.getPeerCertificates()[0];
        // A previously stored certificate set (can be null if this is the first time)
        final Set<X509Certificate> storedServerCertificatesSet = perHostTrustOnFirstUse.get(hostname);

        // Subsequent use: the certificate is a certificate we've trusted before.
        if (storedServerCertificatesSet != null && storedServerCertificatesSet.contains(offeredServerCertificate)) {
            return true;
        }

        // First use: Trust the server certificate.
        if (storedServerCertificatesSet == null) {
            perHostTrustOnFirstUse.put(hostname, Collections.singleton(offeredServerCertificate));
            return true;
        }

        // This is now true:
        assert !storedServerCertificatesSet.contains(offeredServerCertificate);
        final String storedServerCertBase64 = certsSetToBase64(storedServerCertificatesSet);
        final String offeredServerCertBase64 = base64(offeredServerCertificate);
        final HashMap<String, String> markers = new HashMap<>();
        markers.put("hostname", hostname);
        markers.put("old-cert", storedServerCertBase64);
        markers.put("new-cert", offeredServerCertBase64);
        SemaEventLogger.log(new RotatedPeerCertificateSEMaEvent("One of our peers rotated their server certificate:\n"
                + "hostname: " + hostname + "\n"
                + "old cert: " + storedServerCertBase64 + "\n"
                + "new cert: " + offeredServerCertBase64 + "\n"
                + "Note: trusting \"new cert\".",
                Markers.appendEntries(markers)
        ));

        // Trust the new certificate after logging a SEMA event.
        Set<X509Certificate> x509Certificates = new HashSet<>(storedServerCertificatesSet);
        x509Certificates.add(offeredServerCertificate);
        perHostTrustOnFirstUse.put(hostname, Collections.unmodifiableSet(x509Certificates));
        return true;
    }


    private String certsSetToBase64(Set<X509Certificate> x509Certificates) {
        return x509Certificates.stream()
                .map(this::base64)
                .collect(Collectors.joining(","));
    }

    /**
     * This method is used during construction of the SEMa event.  We want the SEMa event to be logged
     * at all costs which is why we swallow an exception that might occur during base64 encoding of the
     * certificate.
     */
    private String base64(final X509Certificate certificate) {
        try {
            return Base64.getEncoder().encodeToString(certificate.getEncoded());
        } catch (Exception e) {
            return "<failed to encode>";
        }
    }
}
