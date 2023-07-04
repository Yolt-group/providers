package com.yolt.providers.web.cryptography;

import com.amazonaws.cloudhsm.jce.provider.CloudHsmProvider;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.*;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.PSSParameterSpec;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * This class delegates the functionality of a digital signature algorithm, depending on the public/private key supplied
 * during initialization, to the BouncyCastle or Cavium implementation. Digital signatures are used for authentication
 * and integrity assurance of digital data.
 * <p/>
 * The inner classes specifying the different supported signature algorithms are instantiated through the {@link YoltSecurityProvider}.
 */
@Slf4j
public class YoltDelegatingSignature extends SignatureSpi {

    private static final Queue<SignatureSpiEventListener> listeners = new ConcurrentLinkedDeque<>();
    private final String algorithm;
    private AlgorithmParameterSpec params;
    private Signature delegate;

    private PrivateKey delegatePrivateKey;
    private PublicKey delegatePublicKey;

    private YoltDelegatingSignature(String algorithm) {
        this.algorithm = algorithm;
    }

    public static void addListener(SignatureSpiEventListener listener) {
        listeners.add(listener);
    }

    public static void removeListener(SignatureSpiEventListener listener) {
        listeners.add(listener);
    }

    private static boolean isCloudHsmKey(Key key) {
        // No better way to achieve this
        return key.getClass().getName().startsWith("com.amazonaws.cloudhsm");
    }

    @Override
    @SneakyThrows(InvalidAlgorithmParameterException.class)
    protected void engineInitVerify(PublicKey publicKey) throws InvalidKeyException {
        if (isCloudHsmKey(publicKey)) {
            this.delegate = createCloudCloudHsmDelegate();
        } else {
            this.delegate = createBouncyCastleDelegate();
        }
        if (params != null) {
            this.delegate.setParameter(params);
        }
        this.delegatePublicKey = publicKey;
        this.delegate.initVerify(publicKey);
    }

    @Override
    protected void engineInitSign(PrivateKey privateKey) throws InvalidKeyException {
        this.engineInitSign(privateKey, null);
    }

    @Override
    @SneakyThrows(InvalidAlgorithmParameterException.class)
    protected void engineInitSign(PrivateKey privateKey, SecureRandom random) throws InvalidKeyException {
        if (isCloudHsmKey(privateKey)) {
            this.delegate = createCloudCloudHsmDelegate();
        } else {
            this.delegate = createBouncyCastleDelegate();
        }
        if (params != null) {
            this.delegate.setParameter(params);
        }
        this.delegatePrivateKey = privateKey;
        this.delegate.initSign(privateKey, random);
    }

    @Override
    protected void engineUpdate(byte b) throws SignatureException {
        this.delegate.update(b);
    }

    @Override
    protected void engineUpdate(byte[] b, int off, int len) throws SignatureException {
        this.delegate.update(b, off, len);
    }

    @Override
    protected byte[] engineSign() throws SignatureException {
        listeners.forEach(listener -> listener.engineSign(delegatePrivateKey));
        return this.delegate.sign();
    }

    @Override
    protected boolean engineVerify(byte[] sigBytes) throws SignatureException {
        listeners.forEach(listener -> listener.engineVerify(delegatePublicKey));
        return this.delegate.verify(sigBytes);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void engineSetParameter(String param, Object value) {
        this.delegate.setParameter(param, value);
    }

    @Override
    protected void engineSetParameter(AlgorithmParameterSpec params) {
        // Using the Public- or PrivateKey, we determine to load CloudHSM
        // or BouncyCastle. As engineSetParameter is called before the init
        // methods, we cache the params and apply them at init.
        this.params = params;
    }

    @Override
    @SuppressWarnings("deprecation")
    protected Object engineGetParameter(String param) {
        return this.delegate.getParameter(param);
    }

    @SneakyThrows
    private Signature createBouncyCastleDelegate() {
        return Signature.getInstance(algorithm, BouncyCastleProvider.PROVIDER_NAME);
    }

    @Override
    protected AlgorithmParameters engineGetParameters() {
        if (delegate != null) {
            return delegate.getParameters();
        }
        try {
            if (algorithm.equals("RSASSA-PSS")) {
                // Construct and return fake AlgorithmParameters (because we do not and cannot yet know
                // which delegate we'll use).  This is done to support code in the constructor of:
                // sun.security.ssl.SignatureScheme.SigAlgParamSpec.SigAlgParamSpec
                // if not implemented the jvm (due to static security providers / SignatureSchemes) will likely be unable to
                // setup a TLS connection due to the missing RSA-PSS signature algorithm.
                var pssParameterSpec = requirePSSParameterSpecWithMGF1(params);
                var yoltSecurityProviderFakeAlgorithmParameters = AlgorithmParameters.getInstance(algorithm, "YoltSecurityProvider");
                yoltSecurityProviderFakeAlgorithmParameters.init(pssParameterSpec); // Useless call, do it anyway.
                return yoltSecurityProviderFakeAlgorithmParameters;
            }
        } catch (InvalidAlgorithmParameterException | InvalidParameterSpecException | NoSuchAlgorithmException | NoSuchProviderException e) {
            log.warn("Not enabling RSASSA-PSS with params={}", params);
            throw new RuntimeException(e);
        }
        return super.engineGetParameters();
    }

    @SneakyThrows
    private Signature createCloudCloudHsmDelegate() {
        if (params == null || !algorithm.equals("RSASSA-PSS")) {
            return Signature.getInstance(algorithm, CloudHsmProvider.PROVIDER_NAME);
        }

        // CloudHsm JCE has no support for the more generic Signature.RSASSA-PSS algorithm.
        // We need to look at the supplied PSSParameterSpec to get to the correct signature.
        PSSParameterSpec pssParameterSpec = requirePSSParameterSpecWithMGF1(params);
        String digestAlgorithm = pssParameterSpec.getDigestAlgorithm();
        if (digestAlgorithm == null) {
            return Signature.getInstance(algorithm, CloudHsmProvider.PROVIDER_NAME);
        }

        return switch (digestAlgorithm) {
            case "SHA", "SHA-1" -> Signature.getInstance("SHA1withRSA/PSS", CloudHsmProvider.PROVIDER_NAME);
            case "SHA-224" -> Signature.getInstance("SHA224withRSA/PSS", CloudHsmProvider.PROVIDER_NAME);
            case "SHA-256" -> Signature.getInstance("SHA256withRSA/PSS", CloudHsmProvider.PROVIDER_NAME);
            case "SHA-384" -> Signature.getInstance("SHA384withRSA/PSS", CloudHsmProvider.PROVIDER_NAME);
            case "SHA-512" -> Signature.getInstance("SHA512withRSA/PSS", CloudHsmProvider.PROVIDER_NAME);
            default -> Signature.getInstance(algorithm, CloudHsmProvider.PROVIDER_NAME);
        };
    }

    private static PSSParameterSpec requirePSSParameterSpecWithMGF1(AlgorithmParameterSpec p)
            throws InvalidAlgorithmParameterException {
        if (p == null) {
            throw new InvalidAlgorithmParameterException("Parameters cannot be null");
        }
        if (!(p instanceof PSSParameterSpec)) {
            throw new InvalidAlgorithmParameterException("parameters must be type PSSParameterSpec");
        }
        PSSParameterSpec params = (PSSParameterSpec) p;

        // now sanity check the parameter values
        if (params.getMGFAlgorithm().equalsIgnoreCase("MGF1")) {
            return params;
        }
        throw new InvalidAlgorithmParameterException("Only supports MGF1");

    }

    public static class SHA1withRSA extends YoltDelegatingSignature {
        public SHA1withRSA() {
            super("SHA1withRSA");
        }
    }

    public static class SHA224withRSA extends YoltDelegatingSignature {
        public SHA224withRSA() {
            super("SHA224withRSA");
        }
    }

    public static class SHA256withRSA extends YoltDelegatingSignature {
        public SHA256withRSA() {
            super("SHA256withRSA");
        }
    }

    public static class SHA384withRSA extends YoltDelegatingSignature {
        public SHA384withRSA() {
            super("SHA384withRSA");
        }
    }

    public static class SHA512withRSA extends YoltDelegatingSignature {
        public SHA512withRSA() {
            super("SHA512withRSA");
        }
    }

    public static class SHA1withRSAPSS extends YoltDelegatingSignature {
        public SHA1withRSAPSS() {
            super("SHA1withRSA/PSS");
        }
    }

    public static class SHA224withRSAPSS extends YoltDelegatingSignature {
        public SHA224withRSAPSS() {
            super("SHA224withRSA/PSS");
        }
    }

    public static class SHA256withRSAPSS extends YoltDelegatingSignature {
        public SHA256withRSAPSS() {
            super("SHA256withRSA/PSS");
        }
    }

    public static class SHA384withRSAPSS extends YoltDelegatingSignature {
        public SHA384withRSAPSS() {
            super("SHA384withRSA/PSS");
        }
    }

    public static class SHA512withRSAPSS extends YoltDelegatingSignature {
        public SHA512withRSAPSS() {
            super("SHA512withRSA/PSS");
        }
    }

    public static class NONEwithRSA extends YoltDelegatingSignature {
        public NONEwithRSA() {
            super("NONEwithRSA");
        }
    }

    public static class NONEwithECDSA extends YoltDelegatingSignature {
        public NONEwithECDSA() {
            super("NONEwithECDSA");
        }
    }

    public static class SHA1withECDSA extends YoltDelegatingSignature {
        public SHA1withECDSA() {
            super("SHA1withECDSA");
        }
    }

    public static class SHA224withECDSA extends YoltDelegatingSignature {
        public SHA224withECDSA() {
            super("SHA224withECDSA");
        }
    }

    public static class SHA256withECDSA extends YoltDelegatingSignature {
        public SHA256withECDSA() {
            super("SHA256withECDSA");
        }
    }

    public static class SHA384withECDSA extends YoltDelegatingSignature {
        public SHA384withECDSA() {
            super("SHA384withECDSA");
        }
    }

    public static class SHA512withECDSA extends YoltDelegatingSignature {
        public SHA512withECDSA() {
            super("SHA512withECDSA");
        }
    }

    public static class RSAPSS extends YoltDelegatingSignature {
        public RSAPSS() {
            super("RSASSA-PSS");
        }
    }

}
