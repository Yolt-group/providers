package com.amazonaws.cloudhsm.stub;

import java.math.BigInteger;
import java.security.interfaces.RSAPrivateKey;

/**
 * The YoltDelegatingSignature detects which keys are for the cloudhsm based
 * on their package name. To test this we need a class in the com.amazonaws.cloudhsm
 * package.
 */
public class StubCloudHsmRsaPrivateKey implements RSAPrivateKey {
    @Override
    public BigInteger getPrivateExponent() {
        throw new UnsupportedOperationException("stub");
    }

    @Override
    public String getAlgorithm() {
        throw new UnsupportedOperationException("stub");
    }

    @Override
    public String getFormat() {
        throw new UnsupportedOperationException("stub");
    }

    @Override
    public byte[] getEncoded() {
        throw new UnsupportedOperationException("stub");
    }

    @Override
    public BigInteger getModulus() {
        throw new UnsupportedOperationException("stub");
    }
}
