package com.yolt.providers.web.form.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@EqualsAndHashCode
@RequiredArgsConstructor
public class EncryptionDetailsDTO {
    private final JWEDetailsDTO jweDetails;


    /**
     * For BudgetInsight.
     */
    @Data
    public static class JWEDetailsDTO {
        final String algorithm; // RSA-OAEP
        final String encryptionMethod; // A256GCM
        final String publicJSONWebKey;
    }
}
