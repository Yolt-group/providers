package com.yolt.providers.web.authenticationmeans;

import com.yolt.providers.common.domain.authenticationmeans.BasicAuthenticationMean;
import com.yolt.providers.web.configuration.TestConfiguration;
import com.yolt.providers.web.service.ProviderVaultKeys;
import nl.ing.lovebird.providerdomain.ServiceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.yolt.providers.common.domain.authenticationmeans.TypedAuthenticationMeans.AUDIENCE_STRING;
import static com.yolt.providers.common.domain.authenticationmeans.TypedAuthenticationMeans.INSTITUTION_ID_STRING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AuthenticationMeansMapperServiceTest {

    private static final String ENCRYPTION_KEY = "3a74c4e02bc688bd4249920c1d3e0ca6cc212b4625976433b14f4338d75e4ee6";
    private static final String BUDGET_INSIGHT_NAME = "BUDGET_INSIGHT";
    private static final ServiceType SERVICE_TYPE = ServiceType.AIS;
    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID CLIENT_GROUP_ID = UUID.randomUUID();
    private static final UUID REDIRECT_URL_ID = UUID.randomUUID();

    @Mock
    private Clock clock;
    @Mock
    private ProviderVaultKeys vaultKeys;
    private AuthenticationMeansEncryptionService encryptionService;

    private AuthenticationMeansMapperService subject;

    @BeforeEach
    public void setup() {
        when(vaultKeys.getAuthEncryptionKey()).thenReturn(ENCRYPTION_KEY);
        encryptionService = new AuthenticationMeansEncryptionService(vaultKeys, new TestConfiguration().jacksonObjectMapper());
        subject = new AuthenticationMeansMapperService(clock, encryptionService);
    }

    @Test
    public void shouldMapToInternalClientRedirectUrlClientConfiguration() {
        //given
        Map<String, BasicAuthenticationMean> authenticationMeans = new HashMap<>();
        authenticationMeans.put("audience", new BasicAuthenticationMean(AUDIENCE_STRING.getType(), "test-audience"));
        authenticationMeans.put("institutionId", new BasicAuthenticationMean(INSTITUTION_ID_STRING.getType(), "test-institutionId"));
        ClientRedirectUrlProviderClientConfiguration clientRedirectUrlProviderClientConfiguration =
                new ClientRedirectUrlProviderClientConfiguration(CLIENT_ID, REDIRECT_URL_ID, SERVICE_TYPE, BUDGET_INSIGHT_NAME,
                        authenticationMeans, Instant.now());

        //when
        InternalClientRedirectUrlClientConfiguration internalClientRedirectUrlClientConfiguration = subject.mapToInternal(clientRedirectUrlProviderClientConfiguration);
        Map<String, BasicAuthenticationMean> decryptedAuthenticationMeans = encryptionService.decryptAuthenticationMeans(internalClientRedirectUrlClientConfiguration.getAuthenticationMeans());

        //then
        assertThat(internalClientRedirectUrlClientConfiguration.getClientId()).isEqualTo(CLIENT_ID);
        assertThat(internalClientRedirectUrlClientConfiguration.getRedirectUrlId()).isEqualTo(REDIRECT_URL_ID);
        assertThat(internalClientRedirectUrlClientConfiguration.getServiceType()).isEqualTo(SERVICE_TYPE);
        assertThat(internalClientRedirectUrlClientConfiguration.getProvider()).isEqualTo(BUDGET_INSIGHT_NAME);
        assertThat(decryptedAuthenticationMeans).isEqualTo(authenticationMeans);
    }

    @Test
    public void shouldMapToInternalClientAuthenticationMeans() {
        //given
        Map<String, BasicAuthenticationMean> authenticationMeans = new HashMap<>();
        authenticationMeans.put("audience", new BasicAuthenticationMean(AUDIENCE_STRING.getType(), "test-audience"));
        authenticationMeans.put("institutionId", new BasicAuthenticationMean(INSTITUTION_ID_STRING.getType(), "test-institutionId"));
        ClientProviderAuthenticationMeans clientProviderAuthenticationMeans =
                new ClientProviderAuthenticationMeans(CLIENT_ID, BUDGET_INSIGHT_NAME, authenticationMeans, Instant.now());

        //when
        InternalClientAuthenticationMeans internalClientAuthenticationMeans = subject.mapToInternal(clientProviderAuthenticationMeans);
        Map<String, BasicAuthenticationMean> decryptedAuthenticationMeans = encryptionService.decryptAuthenticationMeans(internalClientAuthenticationMeans.getAuthenticationMeans());

        //then
        assertThat(internalClientAuthenticationMeans.getClientId()).isEqualTo(CLIENT_ID);
        assertThat(internalClientAuthenticationMeans.getProvider()).isEqualTo(BUDGET_INSIGHT_NAME);
        assertThat(decryptedAuthenticationMeans).isEqualTo(authenticationMeans);
    }

    @Test
    public void shouldMapToInternalClientGroupRedirectUrlClientConfiguration() {
        //given
        Map<String, BasicAuthenticationMean> authenticationMeans = new HashMap<>();
        authenticationMeans.put("audience", new BasicAuthenticationMean(AUDIENCE_STRING.getType(), "test-audience"));
        authenticationMeans.put("institutionId", new BasicAuthenticationMean(INSTITUTION_ID_STRING.getType(), "test-institutionId"));
        ClientGroupRedirectUrlProviderClientConfiguration clientGroupRedirectUrlProviderClientConfiguration =
                new ClientGroupRedirectUrlProviderClientConfiguration(CLIENT_GROUP_ID, REDIRECT_URL_ID, SERVICE_TYPE, BUDGET_INSIGHT_NAME,
                        authenticationMeans, Instant.now());

        //when
        InternalClientGroupRedirectUrlClientConfiguration internalClientGroupRedirectUrlClientConfiguration = subject.mapToInternal(clientGroupRedirectUrlProviderClientConfiguration);
        Map<String, BasicAuthenticationMean> decryptedAuthenticationMeans = encryptionService.decryptAuthenticationMeans(internalClientGroupRedirectUrlClientConfiguration.getAuthenticationMeans());

        //then
        assertThat(internalClientGroupRedirectUrlClientConfiguration.getClientGroupId()).isEqualTo(CLIENT_GROUP_ID);
        assertThat(internalClientGroupRedirectUrlClientConfiguration.getRedirectUrlId()).isEqualTo(REDIRECT_URL_ID);
        assertThat(internalClientGroupRedirectUrlClientConfiguration.getServiceType()).isEqualTo(SERVICE_TYPE);
        assertThat(internalClientGroupRedirectUrlClientConfiguration.getProvider()).isEqualTo(BUDGET_INSIGHT_NAME);
        assertThat(decryptedAuthenticationMeans).isEqualTo(authenticationMeans);
    }

}
