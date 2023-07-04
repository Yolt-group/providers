package com.yolt.providers.web.cryptography.signing;

import com.yolt.providers.common.cryptography.JwsSigningResult;
import lombok.RequiredArgsConstructor;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwx.CompactSerializer;

@RequiredArgsConstructor
public class JcaSigningResult implements JwsSigningResult {

    private final JsonWebSignature jws;
    private final String encodedSignature;

    public String getCompactSerialization() {
        return CompactSerializer.serialize(jws.getHeaders().getEncodedHeader(), jws.getEncodedPayload(), encodedSignature);
    }

    public String getDetachedContentCompactSerialization() {
        return CompactSerializer.serialize(jws.getHeaders().getEncodedHeader(), "", encodedSignature);
    }
}
