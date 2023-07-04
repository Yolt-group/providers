package com.yolt.providers.web.encryption;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AesEncryptionUtilTest {

    private static final String secretKey = "a3f60fafc948035382fbe9ce7b4535c4";

    @Test
    public void shouldReturnEncryptedInputForEncryptWithCorrectParameters() {
        // given
        String input = "thisIsASecretString";

        // when
        String encryptedString = AesEncryptionUtil.encrypt(input, secretKey);

        // then
        assertThat(encryptedString).isNotEqualTo(input);
        String decryptedString = AesEncryptionUtil.decrypt(encryptedString, secretKey);
        assertThat(decryptedString).isEqualTo(input);
    }

}