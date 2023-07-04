package com.yolt.providers.web.cryptography.transport;

import lombok.experimental.UtilityClass;

@UtilityClass
public class CipherSuite {

    /**
     * Default cipher suites.
     */
    private final String[] DEFAULT_SUITE = new String[]{
            // TLSv1.2
            "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_DHE_RSA_WITH_AES_256_CBC_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384",
            "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384",
            // TLSv1.3
            "TLS_AES_256_GCM_SHA384",
            "TLS_CHACHA20_POLY1305_SHA256",
            "TLS_AES_128_GCM_SHA256"

            // These TLSv1.2 ciphers have been sanctioned by security but are not currently supported:
            // "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256",
            // "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256",
    };

    public String[] getYoltSupportedCipherSuites() {
        return DEFAULT_SUITE.clone();
    }
}
