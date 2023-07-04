package com.yolt.providers.web.cryptography.trust;

import lombok.Lombok;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Set;

import static com.yolt.providers.common.util.KeyUtil.createCertificateFromPemFormat;
import static java.util.stream.Collectors.*;

/**
 * Provides a {@link X509TrustManager} that is configured to trust all hosts that are present in {@link TruststoreConfiguration}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrustManagerSupplier {

    private final TruststoreConfiguration truststoreConfiguration;

    private X509TrustManager trustManager;

    @SneakyThrows
    public synchronized X509TrustManager getTrustManager() {
        if (trustManager == null) {
            final KeyStore trustStore = buildTruststoreFromTruststoreConfiguration(truststoreConfiguration);

            // Create a trust manager.
            final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
            trustManager = (X509TrustManager) trustManagerFactory.getTrustManagers()[0];
        }
        return trustManager;
    }

    /**
     * The store returned by this method contains certificates that are present in the
     * configuration properties provided by {@link TruststoreConfiguration}
     */
    @SneakyThrows
    static KeyStore buildTruststoreFromTruststoreConfiguration(final TruststoreConfiguration truststoreConfiguration) {
        final KeyStore trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(null, null);

        // It can be the case that different hosts share an intermediate certificate.  In this case we want to
        // include the certificate only once in the keystore which is why we have this groupingBy operation.
        final Map<String, Set<String>> groupedByCert = truststoreConfiguration.getEntries().stream()
                .collect(groupingBy(TruststoreConfigurationEntry::getCert, mapping(TruststoreConfigurationEntry::getHostname, toSet())));

        for (Map.Entry<String, Set<String>> certEntry : groupedByCert.entrySet()) {
            try {
                final String base64encodedDER = certEntry.getKey();
                final Set<String> hosts = certEntry.getValue();

                final String hostNames = String.join(",", hosts);
                final X509Certificate x509cert = createCertificateFromPemFormat(base64encodedDER);
                trustStore.setCertificateEntry(hostNames, x509cert);
            } catch (Exception e) {
                Lombok.sneakyThrow(e);
            }
        }

        return trustStore;
    }
}
