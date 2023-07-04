package com.yolt.providers.web.authenticationmeans;

import com.yolt.providers.common.domain.authenticationmeans.BasicAuthenticationMean;
import com.yolt.providers.web.configuration.TestConfiguration;
import com.yolt.providers.web.service.ProviderVaultKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static com.yolt.providers.common.domain.authenticationmeans.TypedAuthenticationMeans.AUDIENCE_STRING;
import static com.yolt.providers.common.domain.authenticationmeans.TypedAuthenticationMeans.INSTITUTION_ID_STRING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AuthenticationMeansEncryptionServiceTest {

    private static final String ENCRYPTION_KEY = "3a74c4e02bc688bd4249920c1d3e0ca6cc212b4625976433b14f4338d75e4ee6";

    @Mock
    private ProviderVaultKeys vaultKeys;

    private AuthenticationMeansEncryptionService subject;

    @BeforeEach
    public void setup() {
        subject = new AuthenticationMeansEncryptionService(vaultKeys, new TestConfiguration().jacksonObjectMapper());
    }

    @Test
    public void shouldEncryptAndDecrypt() {
        //given
        when(vaultKeys.getAuthEncryptionKey()).thenReturn(ENCRYPTION_KEY);
        Map<String, BasicAuthenticationMean> authenticationMeans = new HashMap<>();
        authenticationMeans.put("audience", new BasicAuthenticationMean(AUDIENCE_STRING.getType(), "test-audience"));
        authenticationMeans.put("institutionId", new BasicAuthenticationMean(INSTITUTION_ID_STRING.getType(), "test-institutionId"));

        //when
        String encryptAuthenticationMeans = subject.encryptAuthenticationMeans(authenticationMeans);
        Map<String, BasicAuthenticationMean> decryptedAuthenticationMeans = subject.decryptAuthenticationMeans(encryptAuthenticationMeans);

        //then
        assertThat(decryptedAuthenticationMeans).isEqualTo(authenticationMeans);
    }
}
