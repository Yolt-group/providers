package com.yolt.providers.web.cryptography;

import nl.ing.lovebird.clienttokens.ClientToken;
import org.assertj.core.api.ThrowableAssert;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Security;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class VaultServiceTest {

    private static final String VAULT_SECRETS_BASE_PATH = "test/k8s/pods/test/kv/crypto";
    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID CLIENT_GROUP_ID = UUID.randomUUID();
    private static KeyPair generatedKeyPair;

    static {
        Security.addProvider(new BouncyCastleProvider());

        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", "BC");
            keyGen.initialize(1024);
            generatedKeyPair = keyGen.generateKeyPair();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Mock
    private VaultTemplate vaultTemplate;

    @Mock
    private ClientToken clientToken;

    private VaultService vaultService;

    @BeforeEach
    public void beforeEach() {
        vaultService = new VaultService(() -> vaultTemplate, "test", "test", "test", "test");

        when(clientToken.getClientIdClaim()).thenReturn(CLIENT_ID);
        when(clientToken.getClientGroupIdClaim()).thenReturn(CLIENT_GROUP_ID);
    }

    @Test
    public void shouldReturnPrivateTransportKeyForGetPrivateTransportKeyWithCorrectData() throws Exception {
        // given
        UUID kid = UUID.randomUUID();
        VaultResponse response = new VaultResponse();
        String base64EncodedPrivateKey = Base64.getEncoder().encodeToString(generatedKeyPair.getPrivate().getEncoded());
        final Map<Object, Object> secretsData = Map.of(
                "client-id", CLIENT_ID.toString(),
                "key-algorithm", "RSA",
                "private-key", base64EncodedPrivateKey);
        response.setData(Map.of("data", secretsData));
        when(vaultTemplate.read(VAULT_SECRETS_BASE_PATH + "/transport/data/" + kid)).thenReturn(response);

        // when
        PrivateKey privateTransportKey = vaultService.getPrivateTransportKey(clientToken, kid);

        // then
        assertThat(privateTransportKey).isEqualTo(generatedKeyPair.getPrivate());
    }

    @Test
    public void shouldReturnPrivateTransportKeyForGetPrivateTransportKeyWithCorrectDataForClientGroupId() throws Exception {
        // given
        UUID kid = UUID.randomUUID();
        VaultResponse response = new VaultResponse();
        String base64EncodedPrivateKey = Base64.getEncoder().encodeToString(generatedKeyPair.getPrivate().getEncoded());
        final Map<Object, Object> secretsData = Map.of(
                "client-group-id", CLIENT_GROUP_ID.toString(),
                "key-algorithm", "RSA",
                "private-key", base64EncodedPrivateKey);
        response.setData(Map.of("data", secretsData));
        when(vaultTemplate.read(VAULT_SECRETS_BASE_PATH + "/transport/data/" + kid)).thenReturn(response);

        // when
        PrivateKey privateTransportKey = vaultService.getPrivateTransportKey(clientToken, kid);

        // then
        assertThat(privateTransportKey).isEqualTo(generatedKeyPair.getPrivate());
    }

    @Test
    public void shouldThrowKeyNotFoundExceptionForGetPrivateTransportKeyWhenSecretDoesNotExistInVault() {
        // given
        UUID kid = UUID.randomUUID();
        when(vaultTemplate.read(VAULT_SECRETS_BASE_PATH + "/transport/data/" + kid)).thenReturn(null);

        // when
        ThrowableAssert.ThrowingCallable getPrivateTransportKeyCallable = () -> vaultService.getPrivateTransportKey(clientToken, kid);

        // then
        assertThatThrownBy(getPrivateTransportKeyCallable)
                .isInstanceOf(KeyNotFoundException.class);
    }

    @Test
    public void shouldThrowKeyNotFoundExceptionWhenSecretIsBoundToDifferentClientId() {
        // given
        String otherClientId = UUID.randomUUID().toString();
        UUID kid = UUID.randomUUID();
        VaultResponse response = new VaultResponse();
        String base64EncodedPrivateKey = Base64.getEncoder().encodeToString(generatedKeyPair.getPrivate().getEncoded());
        final Map<Object, Object> secretsData = Map.of(
                "client-id", otherClientId,
                "key-algorithm", "RSA",
                "private-key", base64EncodedPrivateKey);
        response.setData(Map.of("data", secretsData));
        when(vaultTemplate.read(VAULT_SECRETS_BASE_PATH + "/transport/data/" + kid)).thenReturn(response);

        // when
        ThrowableAssert.ThrowingCallable getPrivateTransportKeyCallable = () -> vaultService.getPrivateTransportKey(clientToken, kid);

        // then
        assertThatThrownBy(getPrivateTransportKeyCallable)
                .isInstanceOf(KeyNotFoundException.class);
    }

    @Test
    public void shouldThrowKeyNotFoundExceptionForGetPrivateTransportKeyWhenKeyCannotBeDeserialized() {
        // given
        UUID kid = UUID.randomUUID();
        String differentKeyAlgorithm = "DSA";
        VaultResponse response = new VaultResponse();
        String base64EncodedPrivateKey = Base64.getEncoder().encodeToString(generatedKeyPair.getPrivate().getEncoded());
        final Map<Object, Object> secretsData = Map.of(
                "client-id", CLIENT_ID.toString(),
                "key-algorithm", differentKeyAlgorithm,
                "private-key", base64EncodedPrivateKey);
        response.setData(Map.of("data", secretsData));
        when(vaultTemplate.read(VAULT_SECRETS_BASE_PATH + "/transport/data/" + kid)).thenReturn(response);

        // when
        ThrowableAssert.ThrowingCallable getPrivateTransportKeyCallable = () -> vaultService.getPrivateTransportKey(clientToken, kid);

        // then
        assertThatThrownBy(getPrivateTransportKeyCallable)
                .isInstanceOf(KeyNotFoundException.class);
    }

    @Test
    public void shouldReturnPrivateSigningKeyForGetPrivateSigningKeyForClientGroupId() throws Exception {
        // given
        UUID kid = UUID.randomUUID();
        VaultResponse response = new VaultResponse();
        String base64EncodedPrivateKey = Base64.getEncoder().encodeToString(generatedKeyPair.getPrivate().getEncoded());
        final Map<Object, Object> secretsData = Map.of(
                "client-group-id", CLIENT_GROUP_ID.toString(),
                "key-algorithm", "RSA",
                "private-key", base64EncodedPrivateKey);
        response.setData(Map.of("data", secretsData));
        when(vaultTemplate.read(VAULT_SECRETS_BASE_PATH + "/signing/data/" + kid)).thenReturn(response);

        // when
        PrivateKey privateSigningKey = vaultService.getPrivateSigningKey(clientToken, kid);

        // then
        assertThat(privateSigningKey).isEqualTo(generatedKeyPair.getPrivate());
    }
}