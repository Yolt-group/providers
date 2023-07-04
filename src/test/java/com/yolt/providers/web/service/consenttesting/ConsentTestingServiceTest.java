package com.yolt.providers.web.service.consenttesting;

import com.yolt.providers.common.providerinterface.Provider;
import com.yolt.providers.web.authenticationmeans.ClientAuthenticationMeansService;
import com.yolt.providers.web.service.ProviderFactoryService;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.providerdomain.ServiceType;
import nl.ing.lovebird.providershared.api.AuthenticationMeansReference;
import org.jose4j.jwt.JwtClaims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsentTestingServiceTest {

    private static final UUID SOME_CLIENT_ID = UUID.randomUUID();
    private static final UUID SOME_CLIENT_GROUP_ID = UUID.randomUUID();
    private static final UUID SOME_CLIENT_REDIRECT_ID = UUID.randomUUID();
    private static final String SOME_REDIRECT_URL = "http://redirecturl.com";
    private static final ClientToken SOME_CLIENT_TOKEN = new ClientToken("some_serialized_token", new JwtClaims());
    private static final String FIRST_PROVIDER_NAME = "FirstProvider";
    private static final String SECOND_PROVIDER_NAME = "SecondProvider";
    private static final String BLACKLISTED_PROVIDER_NAME = "BlacklistedProvider";
    private static final String UNREGISTERED_PROVIDER_NAME = "UnregisteredProvider";
    private static final String EXPERIMENTAL_PROVIDER_NAME = "ExperimentalProvider";

    @Mock
    private ClientAuthenticationMeansService clientAuthenticationMeansService;
    @Mock
    private ProviderConsentTester providerConsentTester;

    @Mock
    private ConsentTestingProperties consentTestingProperties;

    @Mock
    private Provider firstAisProvider;
    @Mock
    private Provider secondAisProvider;
    @Mock
    private Provider blacklistedAisProvider;
    @Mock
    private Provider unregisteredAisProvider;
    @Mock
    private Provider firstPisProvider;
    @Mock
    private Provider secondPisProvider;
    @Mock
    private Provider blacklistedPisProvider;
    @Mock
    private Provider unregisteredPisProvider;
    @Mock
    private ProviderFactoryService providerFactoryService;

    @InjectMocks
    private ConsentTestingService consentTestingService;

    @Test
    void shouldInvokeConsentTestingForAllAisProvidersEligibleForTestingForClient() {
        //given
        when(consentTestingProperties.getBlacklistedProviders(ServiceType.AIS))
                .thenReturn(Collections.singletonList(BLACKLISTED_PROVIDER_NAME));
        AuthenticationMeansReference authenticationMeansReference = new AuthenticationMeansReference(SOME_CLIENT_ID, SOME_CLIENT_REDIRECT_ID);
        when(clientAuthenticationMeansService.getRegisteredProviders(SOME_CLIENT_ID, SOME_CLIENT_REDIRECT_ID, ServiceType.AIS))
                .thenReturn(new HashSet<>(Arrays.asList(FIRST_PROVIDER_NAME, SECOND_PROVIDER_NAME, BLACKLISTED_PROVIDER_NAME, EXPERIMENTAL_PROVIDER_NAME)));
        when(providerFactoryService.getAllStableProviders())
                .thenReturn(Arrays.asList(firstAisProvider, secondAisProvider, blacklistedAisProvider, unregisteredAisProvider,
                        firstPisProvider, secondPisProvider, blacklistedPisProvider, unregisteredPisProvider));

        when(firstAisProvider.getProviderIdentifier()).thenReturn(FIRST_PROVIDER_NAME);
        when(secondAisProvider.getProviderIdentifier()).thenReturn(SECOND_PROVIDER_NAME);
        when(blacklistedAisProvider.getProviderIdentifier()).thenReturn(BLACKLISTED_PROVIDER_NAME);
        when(unregisteredAisProvider.getProviderIdentifier()).thenReturn(UNREGISTERED_PROVIDER_NAME);
        when(firstAisProvider.getServiceType()).thenReturn(ServiceType.AIS);
        when(secondAisProvider.getServiceType()).thenReturn(ServiceType.AIS);
        when(blacklistedAisProvider.getServiceType()).thenReturn(ServiceType.AIS);
        when(unregisteredAisProvider.getServiceType()).thenReturn(ServiceType.AIS);

        //when
        consentTestingService.invokeConsentTesting(authenticationMeansReference, SOME_CLIENT_TOKEN, ServiceType.AIS, SOME_REDIRECT_URL);

        //then
        verify(clientAuthenticationMeansService).getRegisteredProviders(SOME_CLIENT_ID, SOME_CLIENT_REDIRECT_ID, ServiceType.AIS);
        verify(providerFactoryService).getAllStableProviders();
        verify(providerConsentTester)
                .testProviderConsent(firstAisProvider, SOME_CLIENT_TOKEN, authenticationMeansReference, SOME_REDIRECT_URL);
        verify(providerConsentTester)
                .testProviderConsent(secondAisProvider, SOME_CLIENT_TOKEN, authenticationMeansReference, SOME_REDIRECT_URL);
        verify(providerConsentTester, never())
                .testProviderConsent(blacklistedAisProvider, SOME_CLIENT_TOKEN, authenticationMeansReference, SOME_REDIRECT_URL);
        verify(providerConsentTester, never())
                .testProviderConsent(unregisteredAisProvider, SOME_CLIENT_TOKEN, authenticationMeansReference, SOME_REDIRECT_URL);
        verify(providerConsentTester, never())
                .testProviderConsent(firstPisProvider, SOME_CLIENT_TOKEN, authenticationMeansReference, SOME_REDIRECT_URL);
        verify(providerConsentTester, never())
                .testProviderConsent(secondPisProvider, SOME_CLIENT_TOKEN, authenticationMeansReference, SOME_REDIRECT_URL);
        verify(providerConsentTester, never())
                .testProviderConsent(blacklistedPisProvider, SOME_CLIENT_TOKEN, authenticationMeansReference, SOME_REDIRECT_URL);
        verify(providerConsentTester, never())
                .testProviderConsent(unregisteredPisProvider, SOME_CLIENT_TOKEN, authenticationMeansReference, SOME_REDIRECT_URL);
    }

    @Test
    void shouldInvokeConsentTestingForAllAisProvidersEligibleForTestingForClientGroup() {
        //given
        when(consentTestingProperties.getBlacklistedProviders(ServiceType.AIS))
                .thenReturn(Collections.singletonList(BLACKLISTED_PROVIDER_NAME));
        AuthenticationMeansReference authenticationMeansReference = new AuthenticationMeansReference(null, SOME_CLIENT_GROUP_ID, SOME_CLIENT_REDIRECT_ID);
        when(clientAuthenticationMeansService.getRegisteredProvidersForGroup(SOME_CLIENT_GROUP_ID, SOME_CLIENT_REDIRECT_ID, ServiceType.AIS))
                .thenReturn(new HashSet<>(Arrays.asList(FIRST_PROVIDER_NAME, SECOND_PROVIDER_NAME, BLACKLISTED_PROVIDER_NAME, EXPERIMENTAL_PROVIDER_NAME)));
        when(providerFactoryService.getAllStableProviders())
                .thenReturn(Arrays.asList(firstAisProvider, secondAisProvider, blacklistedAisProvider, unregisteredAisProvider,
                        firstPisProvider, secondPisProvider, blacklistedPisProvider, unregisteredPisProvider));

        when(firstAisProvider.getProviderIdentifier()).thenReturn(FIRST_PROVIDER_NAME);
        when(secondAisProvider.getProviderIdentifier()).thenReturn(SECOND_PROVIDER_NAME);
        when(blacklistedAisProvider.getProviderIdentifier()).thenReturn(BLACKLISTED_PROVIDER_NAME);
        when(unregisteredAisProvider.getProviderIdentifier()).thenReturn(UNREGISTERED_PROVIDER_NAME);
        when(firstAisProvider.getServiceType()).thenReturn(ServiceType.AIS);
        when(secondAisProvider.getServiceType()).thenReturn(ServiceType.AIS);
        when(blacklistedAisProvider.getServiceType()).thenReturn(ServiceType.AIS);
        when(unregisteredAisProvider.getServiceType()).thenReturn(ServiceType.AIS);

        //when
        consentTestingService.invokeConsentTesting(authenticationMeansReference, SOME_CLIENT_TOKEN, ServiceType.AIS, SOME_REDIRECT_URL);

        //then
        verify(clientAuthenticationMeansService).getRegisteredProvidersForGroup(SOME_CLIENT_GROUP_ID, SOME_CLIENT_REDIRECT_ID, ServiceType.AIS);
        verify(providerFactoryService).getAllStableProviders();
        verify(providerConsentTester)
                .testProviderConsent(firstAisProvider, SOME_CLIENT_TOKEN, authenticationMeansReference, SOME_REDIRECT_URL);
        verify(providerConsentTester)
                .testProviderConsent(secondAisProvider, SOME_CLIENT_TOKEN, authenticationMeansReference, SOME_REDIRECT_URL);
        verify(providerConsentTester, never())
                .testProviderConsent(blacklistedAisProvider, SOME_CLIENT_TOKEN, authenticationMeansReference, SOME_REDIRECT_URL);
        verify(providerConsentTester, never())
                .testProviderConsent(unregisteredAisProvider, SOME_CLIENT_TOKEN, authenticationMeansReference, SOME_REDIRECT_URL);
        verify(providerConsentTester, never())
                .testProviderConsent(firstPisProvider, SOME_CLIENT_TOKEN, authenticationMeansReference, SOME_REDIRECT_URL);
        verify(providerConsentTester, never())
                .testProviderConsent(secondPisProvider, SOME_CLIENT_TOKEN, authenticationMeansReference, SOME_REDIRECT_URL);
        verify(providerConsentTester, never())
                .testProviderConsent(blacklistedPisProvider, SOME_CLIENT_TOKEN, authenticationMeansReference, SOME_REDIRECT_URL);
        verify(providerConsentTester, never())
                .testProviderConsent(unregisteredPisProvider, SOME_CLIENT_TOKEN, authenticationMeansReference, SOME_REDIRECT_URL);
    }

    @Test
    void shouldInvokeConsentTestingForAllPisProvidersEligibleForTestingForClient() {
        //given
        when(consentTestingProperties.getBlacklistedProviders(ServiceType.PIS))
                .thenReturn(Collections.singletonList(BLACKLISTED_PROVIDER_NAME));
        AuthenticationMeansReference authenticationMeansReference = new AuthenticationMeansReference(SOME_CLIENT_ID, SOME_CLIENT_REDIRECT_ID);
        when(clientAuthenticationMeansService.getRegisteredProviders(SOME_CLIENT_ID, SOME_CLIENT_REDIRECT_ID, ServiceType.PIS))
                .thenReturn(new HashSet<>(Arrays.asList(FIRST_PROVIDER_NAME, SECOND_PROVIDER_NAME, BLACKLISTED_PROVIDER_NAME, EXPERIMENTAL_PROVIDER_NAME)));
        when(providerFactoryService.getAllStableProviders())
                .thenReturn(Arrays.asList(firstAisProvider, secondAisProvider, blacklistedAisProvider, unregisteredAisProvider,
                        firstPisProvider, secondPisProvider, blacklistedPisProvider, unregisteredPisProvider));

        when(firstPisProvider.getProviderIdentifier()).thenReturn(FIRST_PROVIDER_NAME);
        when(secondPisProvider.getProviderIdentifier()).thenReturn(SECOND_PROVIDER_NAME);
        when(blacklistedPisProvider.getProviderIdentifier()).thenReturn(BLACKLISTED_PROVIDER_NAME);
        when(unregisteredPisProvider.getProviderIdentifier()).thenReturn(UNREGISTERED_PROVIDER_NAME);
        when(firstPisProvider.getServiceType()).thenReturn(ServiceType.PIS);
        when(secondPisProvider.getServiceType()).thenReturn(ServiceType.PIS);
        when(blacklistedPisProvider.getServiceType()).thenReturn(ServiceType.PIS);
        when(unregisteredPisProvider.getServiceType()).thenReturn(ServiceType.PIS);

        //when
        consentTestingService.invokeConsentTesting(authenticationMeansReference, SOME_CLIENT_TOKEN, ServiceType.PIS, SOME_REDIRECT_URL);

        //then
        verify(clientAuthenticationMeansService).getRegisteredProviders(SOME_CLIENT_ID, SOME_CLIENT_REDIRECT_ID, ServiceType.PIS);
        verify(providerFactoryService).getAllStableProviders();
        verify(providerConsentTester)
                .testProviderConsent(firstPisProvider, SOME_CLIENT_TOKEN, authenticationMeansReference, SOME_REDIRECT_URL);
        verify(providerConsentTester)
                .testProviderConsent(secondPisProvider, SOME_CLIENT_TOKEN, authenticationMeansReference, SOME_REDIRECT_URL);
        verify(providerConsentTester, never())
                .testProviderConsent(blacklistedPisProvider, SOME_CLIENT_TOKEN, authenticationMeansReference, SOME_REDIRECT_URL);
        verify(providerConsentTester, never())
                .testProviderConsent(unregisteredPisProvider, SOME_CLIENT_TOKEN, authenticationMeansReference, SOME_REDIRECT_URL);
        verify(providerConsentTester, never())
                .testProviderConsent(firstAisProvider, SOME_CLIENT_TOKEN, authenticationMeansReference, SOME_REDIRECT_URL);
        verify(providerConsentTester, never())
                .testProviderConsent(secondAisProvider, SOME_CLIENT_TOKEN, authenticationMeansReference, SOME_REDIRECT_URL);
        verify(providerConsentTester, never())
                .testProviderConsent(blacklistedAisProvider, SOME_CLIENT_TOKEN, authenticationMeansReference, SOME_REDIRECT_URL);
        verify(providerConsentTester, never())
                .testProviderConsent(unregisteredAisProvider, SOME_CLIENT_TOKEN, authenticationMeansReference, SOME_REDIRECT_URL);
    }

    @Test
    void shouldInvokeConsentTestingForAllPisProvidersEligibleForTestingForClientGroup() {
        //given
        when(consentTestingProperties.getBlacklistedProviders(ServiceType.PIS))
                .thenReturn(Collections.singletonList(BLACKLISTED_PROVIDER_NAME));
        AuthenticationMeansReference authenticationMeansReference = new AuthenticationMeansReference(null, SOME_CLIENT_GROUP_ID, SOME_CLIENT_REDIRECT_ID);
        when(clientAuthenticationMeansService.getRegisteredProvidersForGroup(SOME_CLIENT_GROUP_ID, SOME_CLIENT_REDIRECT_ID, ServiceType.PIS))
                .thenReturn(new HashSet<>(Arrays.asList(FIRST_PROVIDER_NAME, SECOND_PROVIDER_NAME, BLACKLISTED_PROVIDER_NAME, EXPERIMENTAL_PROVIDER_NAME)));
        when(providerFactoryService.getAllStableProviders())
                .thenReturn(Arrays.asList(firstAisProvider, secondAisProvider, blacklistedAisProvider, unregisteredAisProvider,
                        firstPisProvider, secondPisProvider, blacklistedPisProvider, unregisteredPisProvider));

        when(firstPisProvider.getProviderIdentifier()).thenReturn(FIRST_PROVIDER_NAME);
        when(secondPisProvider.getProviderIdentifier()).thenReturn(SECOND_PROVIDER_NAME);
        when(blacklistedPisProvider.getProviderIdentifier()).thenReturn(BLACKLISTED_PROVIDER_NAME);
        when(unregisteredPisProvider.getProviderIdentifier()).thenReturn(UNREGISTERED_PROVIDER_NAME);
        when(firstPisProvider.getServiceType()).thenReturn(ServiceType.PIS);
        when(secondPisProvider.getServiceType()).thenReturn(ServiceType.PIS);
        when(blacklistedPisProvider.getServiceType()).thenReturn(ServiceType.PIS);
        when(unregisteredPisProvider.getServiceType()).thenReturn(ServiceType.PIS);

        //when
        consentTestingService.invokeConsentTesting(authenticationMeansReference, SOME_CLIENT_TOKEN, ServiceType.PIS, SOME_REDIRECT_URL);

        //then
        verify(clientAuthenticationMeansService).getRegisteredProvidersForGroup(SOME_CLIENT_GROUP_ID, SOME_CLIENT_REDIRECT_ID, ServiceType.PIS);
        verify(providerFactoryService).getAllStableProviders();
        verify(providerConsentTester)
                .testProviderConsent(firstPisProvider, SOME_CLIENT_TOKEN, authenticationMeansReference, SOME_REDIRECT_URL);
        verify(providerConsentTester)
                .testProviderConsent(secondPisProvider, SOME_CLIENT_TOKEN, authenticationMeansReference, SOME_REDIRECT_URL);
        verify(providerConsentTester, never())
                .testProviderConsent(blacklistedPisProvider, SOME_CLIENT_TOKEN, authenticationMeansReference, SOME_REDIRECT_URL);
        verify(providerConsentTester, never())
                .testProviderConsent(unregisteredPisProvider, SOME_CLIENT_TOKEN, authenticationMeansReference, SOME_REDIRECT_URL);
        verify(providerConsentTester, never())
                .testProviderConsent(firstAisProvider, SOME_CLIENT_TOKEN, authenticationMeansReference, SOME_REDIRECT_URL);
        verify(providerConsentTester, never())
                .testProviderConsent(secondAisProvider, SOME_CLIENT_TOKEN, authenticationMeansReference, SOME_REDIRECT_URL);
        verify(providerConsentTester, never())
                .testProviderConsent(blacklistedAisProvider, SOME_CLIENT_TOKEN, authenticationMeansReference, SOME_REDIRECT_URL);
        verify(providerConsentTester, never())
                .testProviderConsent(unregisteredAisProvider, SOME_CLIENT_TOKEN, authenticationMeansReference, SOME_REDIRECT_URL);
    }
}