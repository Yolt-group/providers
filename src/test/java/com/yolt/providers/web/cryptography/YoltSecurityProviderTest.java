package com.yolt.providers.web.cryptography;

import com.amazonaws.cloudhsm.jce.provider.CloudHsmProvider;
import com.amazonaws.cloudhsm.stub.StubCloudHsmRsaPrivateKey;
import com.yolt.securityutils.signing.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.ThrowableAssert;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.*;
import java.security.spec.RSAKeyGenParameterSpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Slf4j
@ExtendWith(MockitoExtension.class)
class YoltSecurityProviderTest {

    static {
        // We need to insert the YoltSecurityProvider before SunRSA, but after the generic Sun JCE provider.
        Security.insertProviderAt(new YoltSecurityProvider(), 2);
        // TODO: This will add BouncyCastle as the last provider, is this correct?
        Security.addProvider(new BouncyCastleProvider());
        // Test Provider
        Security.addProvider(new Provider(CloudHsmProvider.PROVIDER_NAME, "0.1", "junit test") {
        });
    }

    private KeyPair bcRsaKeyPair;

    @BeforeEach
    void beforeEach() throws Exception {
        KeyPairGenerator keyPair = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME);
        keyPair.initialize(new RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4));
        bcRsaKeyPair = keyPair.generateKeyPair();
    }

    @Test
    void canUseYoltKeystore() {
        Assertions.assertDoesNotThrow(() -> KeyStore.getInstance("Yolt"));
    }

    @Test
    void shouldDelegateToBcForRsaPrivateKeyForInitSignWithCorrectPrivateKey() throws Exception {
        // given
        Signature signature = Signature.getInstance(SignatureAlgorithm.SHA512_WITH_RSA.getJvmAlgorithm());
        String secret = "Is this the real life, is this just fanta sea";

        // when
        signature.initSign(bcRsaKeyPair.getPrivate());

        // then
        signature.update(secret.getBytes());
        byte[] sign = signature.sign();
        signature.initVerify(bcRsaKeyPair.getPublic());
        signature.update(secret.getBytes());
        boolean valid = signature.verify(sign);

        assertThat(signature.getProvider()).isInstanceOf(YoltSecurityProvider.class);
        assertThat(valid).isTrue();
    }

    /*
     * We would like to test a successful delegation to the CaviumProvider/CloudHSM, but the HSM is not reachable
     * outside of the kubernetes cluster and the native library is only added in the docker container. Therefor testing
     * if the {@link NoSuchAlgorithmException} is thrown, verifies that our testing security provider (added in the static
     * initialization block) is called for the CloudHsmKey (which is stubbed).
     */
    @Test
    void shouldDelegateToCaviumForHsmKey() throws Exception {
        // given
        Signature signature = Signature.getInstance(SignatureAlgorithm.SHA512_WITH_RSA.getJvmAlgorithm());
        PrivateKey privateKey = new StubCloudHsmRsaPrivateKey();

        // given
        ThrowableAssert.ThrowingCallable initSignCallable = () -> signature.initSign(privateKey);

        // then
        assertThat(signature.getProvider()).isInstanceOf(YoltSecurityProvider.class);
        assertThatThrownBy(initSignCallable)
                .isInstanceOf(NoSuchAlgorithmException.class);
    }

    @Test
    void shouldDelegateDuringMTLSHandshake() throws Exception {
        // given
        Signature signature = Signature.getInstance("RSASSA-PSS");
        PrivateKey privateKey = new StubCloudHsmRsaPrivateKey();

        // given
        ThrowableAssert.ThrowingCallable initSignCallable = () -> signature.initSign(privateKey);

        // then
        assertThat(signature.getProvider()).isInstanceOf(YoltSecurityProvider.class);
        assertThatThrownBy(initSignCallable)
                .isInstanceOf(NoSuchAlgorithmException.class);
    }

}