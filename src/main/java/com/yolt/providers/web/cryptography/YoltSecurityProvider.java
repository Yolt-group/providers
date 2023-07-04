package com.yolt.providers.web.cryptography;

import java.io.IOException;
import java.security.AlgorithmParameters;
import java.security.AlgorithmParametersSpi;
import java.security.Provider;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import java.util.List;
import java.util.Map;

/**
 * To add the provider at runtime use:
 * <pre>
 * import java.security.Security;
 * import com.yolt.providers.web.cryptography.YoltSecurityProvider;
 *
 * // We need to insert the YoltSecurityProvider before SunRSA, but after the generic Sun JCE provider. See YoltSecurityProvider.
 * Security.insertProviderAt(new YoltSecurityProvider(), 2);
 * </pre>
 * <p/>
 * The YoltSecurityProvider adds a delegate signature implementation for all the supported signaturprivateKeye algorithms in
 * Cavium. To be able to delegate between BouncyCastle and Cavium, the YoltSecurityProvider needs to be registered
 * before the SunRsa implementation from the JRE (at position 2). Do not register this provider at position 1, as this
 * will break the JCE.
 */
public class YoltSecurityProvider extends Provider {

    public YoltSecurityProvider() {
        super("YoltSecurityProvider", "1.0", "A delegating provider to be able to use both Cavium and BouncyCastle RSA Signatures");

        this.put("Signature.SHA1withRSA", YoltDelegatingSignature.SHA1withRSA.class.getName());
        this.put("Signature.SHA224withRSA", YoltDelegatingSignature.SHA224withRSA.class.getName());
        this.put("Signature.SHA256withRSA", YoltDelegatingSignature.SHA256withRSA.class.getName());
        this.put("Signature.SHA384withRSA", YoltDelegatingSignature.SHA384withRSA.class.getName());
        this.put("Signature.SHA512withRSA", YoltDelegatingSignature.SHA512withRSA.class.getName());
        this.put("Signature.SHA1withRSA/PSS", YoltDelegatingSignature.SHA1withRSAPSS.class.getName());
        this.put("Signature.SHA224withRSA/PSS", YoltDelegatingSignature.SHA224withRSAPSS.class.getName());
        this.put("Signature.SHA256withRSA/PSS", YoltDelegatingSignature.SHA256withRSAPSS.class.getName());
        this.put("Signature.SHA384withRSA/PSS", YoltDelegatingSignature.SHA384withRSAPSS.class.getName());
        this.put("Signature.SHA512withRSA/PSS", YoltDelegatingSignature.SHA512withRSAPSS.class.getName());
        this.put("Signature.NONEwithRSA", YoltDelegatingSignature.NONEwithRSA.class.getName());
        this.put("Signature.NONEwithECDSA", YoltDelegatingSignature.NONEwithECDSA.class.getName());
        this.put("Signature.SHA1withECDSA", YoltDelegatingSignature.SHA1withECDSA.class.getName());
        this.put("Signature.SHA224withECDSA", YoltDelegatingSignature.SHA224withECDSA.class.getName());
        this.put("Signature.SHA256withECDSA", YoltDelegatingSignature.SHA256withECDSA.class.getName());
        this.put("Signature.SHA384withECDSA", YoltDelegatingSignature.SHA384withECDSA.class.getName());
        this.put("Signature.SHA512withECDSA", YoltDelegatingSignature.SHA512withECDSA.class.getName());
        this.put("Signature.RSASSA-PSS", YoltDelegatingSignature.RSAPSS.class.getName());
        this.put("Keystore.Yolt", YoltKeyStoreSpi.class.getName());

        this.putService(new Service(this, "AlgorithmParameters", "RSASSA-PSS", AlgorithmParameters.class.getName(), List.of(), Map.of()) {
            @Override
            public Object newInstance(Object constructorParameter) {
                return new AlgorithmParametersSpi() {
                    AlgorithmParameterSpec spec;

                    @Override
                    protected void engineInit(AlgorithmParameterSpec paramSpec) {
                        this.spec = paramSpec;
                    }

                    @Override
                    protected void engineInit(byte[] params) {
                        throw new RuntimeException(toString());
                    }

                    @Override
                    protected void engineInit(byte[] params, String format) {
                        throw new RuntimeException(toString());
                    }

                    @Override
                    protected <T extends AlgorithmParameterSpec> T engineGetParameterSpec(Class<T> paramSpec) {
                        return (T) spec;
                    }

                    @Override
                    protected byte[] engineGetEncoded() {
                        throw new RuntimeException(toString());
                    }

                    @Override
                    protected byte[] engineGetEncoded(String format) {
                        throw new RuntimeException(toString());
                    }

                    @Override
                    protected String engineToString() {
                        return "YoltSecurityProvider fake RSASSA-PSS parameters.";
                    }
                };
            }
        });
    }

}
