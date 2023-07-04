package com.yolt.providers.web.cryptography;

import java.security.PrivateKey;
import java.security.PublicKey;

public interface SignatureSpiEventListener {

    void engineSign(PrivateKey privateKey);

    void engineVerify(PublicKey publicKey);
}
