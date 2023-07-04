package com.yolt.providers.web.authenticationmeans.startuplogging;

import com.yolt.providers.common.providerinterface.Provider;
import com.yolt.providers.web.authenticationmeans.ClientAuthenticationMeansService;
import com.yolt.providers.web.authenticationmeans.startuplogging.AuthenticationMeansLoggingProperties.AuthMeansPropertyReference;
import nl.ing.lovebird.providerdomain.ServiceType;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static nl.ing.lovebird.providerdomain.ServiceType.AIS;
import static nl.ing.lovebird.providerdomain.ServiceType.PIS;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthenticationMeansLoggerTest {

    private static final UUID UUID_1 = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final UUID UUID_2 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID UUID_3 = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock
    private ClientAuthenticationMeansService clientAuthenticationMeansService;

    private Map<String, List<AuthMeansPropertyReference>> providerReferences;
    private List<Provider> implemented;

    private AuthenticationMeansLogger authenticationMeansLogger;

    @BeforeEach
    public void setUp() {
        implemented = new ArrayList<>();
        providerReferences = new HashMap<>();
        AuthenticationMeansLoggingProperties properties = new AuthenticationMeansLoggingProperties(providerReferences);
        authenticationMeansLogger = new AuthenticationMeansLogger(implemented, properties, clientAuthenticationMeansService);
    }

    @Test
    public void shouldTriggerRetrievalOfProperAuthenticationMeans() {
        //given
        implementedProvider("PROVIDER1")
                .withServiceType(AIS)
                .build();
        implementedProvider("PROVIDER1")
                .withServiceType(PIS)
                .build();
        implementedProvider("PROVIDER2")
                .withServiceType(AIS)
                .build();

        propertyProvider("PROVIDER1")
                .withClientId(UUID_1)
                .withRedirectUrlId(UUID_2)
                .and()
                .withClientGroupId(UUID_3)
                .withRedirectUrlId(UUID_2)
                .build();
        propertyProvider("PROVIDER2")
                .withClientGroupId(UUID_3)
                .withRedirectUrlId(UUID_2)
                .build();

        //when
        authenticationMeansLogger.triggerLogging();

        //then
        verify(clientAuthenticationMeansService).acquireAuthenticationMeans("PROVIDER1", AIS, new AuthenticationMeansReference(UUID_1, null, UUID_2));
        verify(clientAuthenticationMeansService).acquireAuthenticationMeans("PROVIDER1", PIS, new AuthenticationMeansReference(UUID_1, null, UUID_2));
        verify(clientAuthenticationMeansService).acquireAuthenticationMeans("PROVIDER1", AIS, new AuthenticationMeansReference(null, UUID_3, UUID_2));
        verify(clientAuthenticationMeansService).acquireAuthenticationMeans("PROVIDER1", PIS, new AuthenticationMeansReference(null, UUID_3, UUID_2));
        verify(clientAuthenticationMeansService).acquireAuthenticationMeans("PROVIDER2", AIS, new AuthenticationMeansReference(null, UUID_3, UUID_2));
        verifyNoMoreInteractions(clientAuthenticationMeansService);
    }

    @Test
    public void shouldNotThrowExceptionWhenSomethingGoesWrong() {
        //given
        implementedProvider("PROVIDER1")
                .withServiceType(AIS)
                .build();

        propertyProvider("PROVIDER1")
                .withClientId(UUID_1)
                .withRedirectUrlId(UUID_2)
                .build();
        doThrow(new RuntimeException()).when(clientAuthenticationMeansService).acquireAuthenticationMeans(any(), any(), any());

        //when
        authenticationMeansLogger.triggerLogging();

        //then
        //No exception is thrown
    }

    @Test
    public void shouldNotTriggerRetrievalOfAuthenticationMeansWhenNoEntriesInProperties() {
        //given

        //when
        authenticationMeansLogger.triggerLogging();

        //then
        verifyNoInteractions(clientAuthenticationMeansService);
    }

    private ProviderBuilder implementedProvider(final String providerKey) {
        return new ProviderBuilder(providerKey);
    }

    private PropertyProviderBuilder propertyProvider(final String providerKey) {
        return new PropertyProviderBuilder(providerKey);
    }

    private class ProviderBuilder {

        private String providerKey;
        private ServiceType serviceType;

        ProviderBuilder(final String providerKey) {
            this.providerKey = providerKey;
        }

        private ProviderBuilder withServiceType(final ServiceType serviceType) {
            this.serviceType = serviceType;
            return this;
        }

        private void build() {
            Provider provider = mock(Provider.class);
            when(provider.getProviderIdentifier()).thenReturn(providerKey);
            when(provider.getServiceType()).thenReturn(serviceType);
            implemented.add(provider);
        }
    }

    private class PropertyProviderBuilder {

        PropertyProviderBuilder(final String providerKey) {
            this.providerKey = providerKey;
        }

        private String providerKey;
        private UUID clientId;
        private UUID clientGroupId;
        private UUID redirectUrlId;

        private PropertyProviderBuilder withClientId(final UUID clientId) {
            this.clientId = clientId;
            return this;
        }

        private PropertyProviderBuilder withClientGroupId(final UUID clientGroupId) {
            this.clientGroupId = clientGroupId;
            return this;
        }

        private PropertyProviderBuilder withRedirectUrlId(final UUID redirectUrlId) {
            this.redirectUrlId = redirectUrlId;
            return this;
        }

        public PropertyProviderBuilder and() {
            build();
            return propertyProvider(providerKey);
        }

        private void build() {
            providerReferences.computeIfAbsent(providerKey, key -> new ArrayList<>()).add(new AuthMeansPropertyReference(clientId, clientGroupId, redirectUrlId));
        }
    }
}