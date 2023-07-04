package com.yolt.providers.web.cryptography;

import nl.ing.lovebird.clienttokens.ClientToken;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Optional;
import java.util.UUID;

import static com.yolt.providers.web.cryptography.CloudHSMKeyService.*;
import static nl.ing.lovebird.clienttokens.test.TestJwtClaims.createClientClaims;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CloudHSMKeyServiceTest {

    static final UUID CLIENT_ID = UUID.randomUUID();
    static final UUID CLIENT_GROUP_ID = UUID.randomUUID();
    private static KeyPair generatedKeyPair;

    final HSMCredentials credentials = new HSMCredentials("partition", "username", "password");
    @Mock
    AuthProvider authProvider;

    @Mock
    KeyStoreSpi keyStore;

    CloudHSMKeyService cloudHSMKeyService;

    @BeforeAll
    static void beforeAll() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(1024);
        generatedKeyPair = keyGen.generateKeyPair();
    }

    @BeforeEach
    void beforeEach() throws CertificateException, IOException, NoSuchAlgorithmException {
        KeyStore stubKeyStore = new KeyStore(this.keyStore, null, "junit") {

        };
        stubKeyStore.load(null);
        cloudHSMKeyService = new CloudHSMKeyService(
                credentials,
                authProvider,
                stubKeyStore
        );
    }

    @Test
    void login() throws LoginException {
        cloudHSMKeyService.login();
        verify(authProvider).login(null, new ApplicationCallBackHandler("username:password"));
    }

    @Test
    void logout() throws LoginException {
        cloudHSMKeyService.logout();
        verify(authProvider).logout();
    }

    @Test
    public void shouldReturnPrivateTransportKeyForInbNVForYoltAppForGetPrivateTransportKeyWhenKeyDoesNotExistInHSM() throws Exception {
        // given
        UUID kid = UUID.randomUUID();
        ClientToken clientToken = new ClientToken("stubbed-client-token", createClientClaims("junit", CLIENT_GROUP_ID, YOLT_CLIENTID));
        when(keyStore.engineGetKey(CLIENT_GROUP_ID + "_" + kid, null)).thenReturn(null);
        when(keyStore.engineGetKey(ING_NV_GROUPID + "_" + kid, null)).thenReturn(generatedKeyPair.getPrivate());

        // when
        PrivateKey privateTransportKey = cloudHSMKeyService.getPrivateTransportKey(clientToken, kid);

        // then
        assertThat(privateTransportKey).isEqualTo(generatedKeyPair.getPrivate());
    }

    @Test
    public void shouldReturnPrivateTransportKeyForYoltGroupForYTSClientForGetPrivateTransportKeyWhenKeyDoesNotExistInHSM() throws Exception {
        // given
        UUID kid = UUID.randomUUID();
        ClientToken clientToken = new ClientToken("stubbed-client-token", createClientClaims("junit", YTS_CLIENT_GROUP_ID, CLIENT_ID));
        when(keyStore.engineGetKey(YTS_CLIENT_GROUP_ID + "_" + kid, null)).thenReturn(null);
        when(keyStore.engineGetKey(YOLT_GROUP_ID + "_" + kid, null)).thenReturn(generatedKeyPair.getPrivate());

        // when
        PrivateKey privateTransportKey = cloudHSMKeyService.getPrivateTransportKey(clientToken, kid);

        // then
        assertThat(privateTransportKey).isEqualTo(generatedKeyPair.getPrivate());
    }

    @Test
    public void shouldThrowKeyNotFoundExceptionWithUnrecoverableKeyExceptionCauseForGetPrivateTransportKeyWhenKeyDoesNotExistInHSMAndCannotRetryForIngNVForYoltApp() throws Exception {
        // given
        UUID kid = UUID.randomUUID();
        ClientToken clientToken = new ClientToken("stubbed-client-token", createClientClaims("junit", CLIENT_GROUP_ID, YOLT_CLIENTID));
        when(keyStore.engineGetKey(CLIENT_GROUP_ID + "_" + kid, null)).thenReturn(null);
        when(keyStore.engineGetKey(ING_NV_GROUPID + "_" + kid, null)).thenReturn(null);

        // when
        ThrowableAssert.ThrowingCallable getPrivateTransportKeyCallable = () -> cloudHSMKeyService.getPrivateTransportKey(clientToken, kid);

        // then
        assertThatThrownBy(getPrivateTransportKeyCallable)
                .isInstanceOf(KeyNotFoundException.class);
    }

    @Test
    public void shouldReturnPrivateTransportKeyWithMatchingPrivateKeyForGetPrivateTransportKeyWithCorrectData() throws Exception {
        // given
        UUID kid = UUID.randomUUID();
        ClientToken clientToken = new ClientToken("stubbed-client-token", createClientClaims("junit", CLIENT_GROUP_ID, CLIENT_ID));
        when(keyStore.engineGetKey(CLIENT_GROUP_ID + "_" + kid, null)).thenReturn(generatedKeyPair.getPrivate());

        // when
        PrivateKey privateTransportKey = cloudHSMKeyService.getPrivateTransportKey(clientToken, kid);

        // then
        assertThat(privateTransportKey).isEqualTo(generatedKeyPair.getPrivate());
    }

    @Test
    public void shouldReturnTheSamePrivateTransportKeyInstanceForGetPrivateTransportKeyWhenSubsequentCallsWithTheSameParameters() throws Exception {
        // given
        UUID kid = UUID.randomUUID();
        ClientToken clientToken = new ClientToken("stubbed-client-token", createClientClaims("junit", CLIENT_GROUP_ID, CLIENT_ID));
        when(keyStore.engineGetKey(CLIENT_GROUP_ID + "_" + kid, null)).thenReturn(generatedKeyPair.getPrivate());

        // when
        PrivateKey privateTransportKey1 = cloudHSMKeyService.getPrivateTransportKey(clientToken, kid);
        PrivateKey privateTransportKey2 = cloudHSMKeyService.getPrivateTransportKey(clientToken, kid);

        // then
        assertThat(privateTransportKey1).isSameAs(privateTransportKey2);
        verify(keyStore, times(1)).engineGetKey(CLIENT_GROUP_ID + "_" + kid, null);
    }

    @Test
    public void shouldReturnTheSamePrivateSigningKeyInstanceForGetPrivateSigningKeyWhenSubsequentCallsWithTheSameParameters() throws Exception {
        // given
        UUID kid = UUID.randomUUID();
        ClientToken clientToken = new ClientToken("stubbed-client-token", createClientClaims("junit", CLIENT_GROUP_ID, CLIENT_ID));
        when(keyStore.engineGetKey(CLIENT_GROUP_ID + "_" + kid, null)).thenReturn(generatedKeyPair.getPrivate());

        // when
        PrivateKey privateSigningKey1 = cloudHSMKeyService.getPrivateSigningKey(clientToken, kid);
        PrivateKey privateSigningKey2 = cloudHSMKeyService.getPrivateSigningKey(clientToken, kid);

        // then
        assertThat(privateSigningKey1).isSameAs(privateSigningKey2);
        verify(keyStore, times(1)).engineGetKey(CLIENT_GROUP_ID + "_" + kid, null);
    }

    @Test
    public void shouldReturnPrivateKeyReferenceForPrivateTransportKey() throws Exception {
        // given
        UUID kid = UUID.randomUUID();
        ClientToken clientToken = new ClientToken("stubbed-client-token", createClientClaims("junit", CLIENT_GROUP_ID, CLIENT_ID));
        when(keyStore.engineGetKey(CLIENT_GROUP_ID + "_" + kid, null)).thenReturn(generatedKeyPair.getPrivate());

        // when
        PrivateKey privateTransportKey = cloudHSMKeyService.getPrivateSigningKey(clientToken, kid);
        Optional<PrivateKeyReference> privateKeyReference = cloudHSMKeyService.getPrivateKeyReference(privateTransportKey);

        // then
        assertThat(privateKeyReference).contains(new PrivateKeyReference(CLIENT_GROUP_ID, kid));
        verify(keyStore, times(1)).engineGetKey(CLIENT_GROUP_ID + "_" + kid, null);
    }

    @Test
    public void shouldReturnPrivateKeyReferenceForPrivateSigningKey() throws Exception {
        // given
        UUID kid = UUID.randomUUID();
        ClientToken clientToken = new ClientToken("stubbed-client-token", createClientClaims("junit", CLIENT_GROUP_ID, CLIENT_ID));
        when(keyStore.engineGetKey(CLIENT_GROUP_ID + "_" + kid, null)).thenReturn(generatedKeyPair.getPrivate());

        // when
        PrivateKey privateTransportKey = cloudHSMKeyService.getPrivateTransportKey(clientToken, kid);
        Optional<PrivateKeyReference> privateKeyReference = cloudHSMKeyService.getPrivateKeyReference(privateTransportKey);

        // then
        assertThat(privateKeyReference).contains(new PrivateKeyReference(CLIENT_GROUP_ID, kid));
        verify(keyStore, times(1)).engineGetKey(CLIENT_GROUP_ID + "_" + kid, null);
    }

    @Test
    public void shouldThrowKeyNotFoundExceptionForGetPrivateTransportKeyWhenKeyDoesNotExistInHSM() throws Exception {
        // given
        UUID kid = UUID.randomUUID();
        ClientToken clientToken = new ClientToken("stubbed-client-token", createClientClaims("junit", CLIENT_GROUP_ID, CLIENT_ID));
        when(keyStore.engineGetKey(CLIENT_GROUP_ID + "_" + kid, null)).thenThrow(new UnrecoverableKeyException("test"));

        // when
        ThrowableAssert.ThrowingCallable getPrivateTransportKeyCallable = () -> cloudHSMKeyService.getPrivateTransportKey(clientToken, kid);

        // then
        assertThatThrownBy(getPrivateTransportKeyCallable).isInstanceOf(KeyNotFoundException.class);
    }
}