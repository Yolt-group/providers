package com.yolt.providers.web.service;

import lombok.AllArgsConstructor;
import nl.ing.lovebird.secretspipeline.VaultKeys;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class ProviderVaultKeys {

    private static final String LB_ENCRYPTION_KEY = "lb-encryption-key";
    private static final String LB_AUTH_ENCRYPTION_KEY = "lb-auth-encryptionkey";

    private VaultKeys vaultKeys;

    public String getEncryptionKey() {
        return new String(Hex.encode(vaultKeys.getSymmetricKey(LB_ENCRYPTION_KEY).getKey().getEncoded()));
    }

    public String getAuthEncryptionKey(){
        return new String(Hex.encode(vaultKeys.getSymmetricKey(LB_AUTH_ENCRYPTION_KEY).getKey().getEncoded()));
    }
}
