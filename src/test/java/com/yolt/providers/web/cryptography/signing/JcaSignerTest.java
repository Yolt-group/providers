package com.yolt.providers.web.cryptography.signing;

import com.yolt.providers.web.cryptography.KeyService;
import com.yolt.securityutils.signing.SignatureAlgorithm;
import nl.ing.lovebird.clienttokens.ClientToken;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwx.HeaderParameterNames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class JcaSignerTest {

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID CLIENT_GROUP_ID = UUID.randomUUID();

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private JcaSigner cryptoSigner;

    @Mock
    private KeyService keyService;
    @Mock
    private ClientToken clientToken;

    @BeforeEach
    public void beforeEach() {
        cryptoSigner = new JcaSigner(clientToken, keyService);
    }

    @Test
    public void shouldReturnSignatureForSignWithCorrectData() throws Exception {
        // given
        when(clientToken.getClientIdClaim()).thenReturn(CLIENT_ID);
        when(clientToken.getClientGroupIdClaim()).thenReturn(CLIENT_GROUP_ID);
        KeyPair keypair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        UUID kid = UUID.randomUUID();
        when(keyService.getPrivateSigningKey(clientToken, kid)).thenReturn(keypair.getPrivate());
        JsonWebSignature jws = new JsonWebSignature();
        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
        jws.setPayload("payload");

        // when
        String compact = cryptoSigner.sign(jws, kid, SignatureAlgorithm.SHA256_WITH_RSA).getCompactSerialization();

        //then
        JsonWebSignature result = (JsonWebSignature) JsonWebSignature.fromCompactSerialization(compact);
        result.setKey(keypair.getPublic());
        assertThat(result.verifySignature()).isTrue();
    }

    @Test
    public void shouldReturnSignatureForSignWithJwsWithPSS() throws Exception {
        // given
        when(clientToken.getClientIdClaim()).thenReturn(CLIENT_ID);
        when(clientToken.getClientGroupIdClaim()).thenReturn(CLIENT_GROUP_ID);
        SignatureAlgorithm sha256WithRsaPss = SignatureAlgorithm.SHA256_WITH_RSA_PSS;
        KeyPair keypair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        UUID kid = UUID.randomUUID();
        when(keyService.getPrivateSigningKey(clientToken, kid)).thenReturn(keypair.getPrivate());
        JsonWebSignature jws = new JsonWebSignature();
        jws.setAlgorithmHeaderValue(sha256WithRsaPss.getJsonSignatureAlgorithm());
        jws.setPayload("payload");

        // when
        String compact = cryptoSigner.sign(jws, kid, sha256WithRsaPss).getCompactSerialization();

        // then
        JsonWebSignature result = (JsonWebSignature) JsonWebSignature.fromCompactSerialization(compact);
        result.setKey(keypair.getPublic());
        assertThat(result.verifySignature()).isTrue();
    }

    @Test
    public void shouldReturnSignatureForSignWithJwsUnencoded() throws Exception {
        // given
        when(clientToken.getClientIdClaim()).thenReturn(CLIENT_ID);
        when(clientToken.getClientGroupIdClaim()).thenReturn(CLIENT_GROUP_ID);
        KeyPair keypair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        UUID kid = UUID.randomUUID();
        when(keyService.getPrivateSigningKey(clientToken, kid)).thenReturn(keypair.getPrivate());
        JsonWebSignature jws = new JsonWebSignature();
        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
        jws.setPayload("payload");

        // when
        String compact = cryptoSigner.sign(jws, kid, SignatureAlgorithm.SHA256_WITH_RSA).getCompactSerialization();

        // then
        JsonWebSignature result = (JsonWebSignature) JsonWebSignature.fromCompactSerialization(compact);
        result.setKey(keypair.getPublic());
        assertThat(result.verifySignature()).isTrue();
    }
}
