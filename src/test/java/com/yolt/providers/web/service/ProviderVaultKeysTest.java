package com.yolt.providers.web.service;

import com.yolt.securityutils.crypto.SecretKey;
import nl.ing.lovebird.secretspipeline.VaultKeys;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ProviderVaultKeysTest {

    @Mock
    private VaultKeys vaultKeys;

    private ProviderVaultKeys providerVaultKeys;

    @BeforeEach
    public void setup(){
        providerVaultKeys = new ProviderVaultKeys(vaultKeys);
    }

    @Test
    public void getEncryptionKey() {
        //given
        SecretKey secretKey = SecretKey.from("test".getBytes());
        when(vaultKeys.getSymmetricKey("lb-encryption-key")).thenReturn(secretKey);

        //when
        String result = providerVaultKeys.getEncryptionKey();

        //then
        assertThat(result).isEqualTo(new String(Hex.encodeHex("test".getBytes())));
    }

    @Test
    public void getAuthEncryptionKey() {
        //given
        SecretKey secretKey = SecretKey.from("test".getBytes());
        when(vaultKeys.getSymmetricKey("lb-auth-encryptionkey")).thenReturn(secretKey);

        //when
        String result = providerVaultKeys.getAuthEncryptionKey();

        //then
        assertThat(result).isEqualTo(new String(Hex.encodeHex("test".getBytes())));
    }
}
