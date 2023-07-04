package com.yolt.providers.web.service;

import com.yolt.providers.common.ais.url.UrlAutoOnboardingRequest;
import com.yolt.providers.common.cryptography.RestTemplateManager;
import com.yolt.providers.common.cryptography.Signer;
import com.yolt.providers.common.domain.ProviderMetaData;
import com.yolt.providers.common.domain.authenticationmeans.AuthenticationMeanTypeKeyDTO;
import com.yolt.providers.common.domain.authenticationmeans.TypedAuthenticationMeans;
import com.yolt.providers.common.domain.authenticationmeans.keymaterial.KeyRequirements;
import com.yolt.providers.common.providerinterface.Provider;
import com.yolt.providers.dummy.DummyTestAutoOnboardingProvider;
import com.yolt.providers.dummy.DummyTestProvider;
import com.yolt.providers.dummy.FakeTestDataProvider;
import com.yolt.providers.dummy.MockTestProvider;
import com.yolt.providers.web.service.domain.ProviderInfo;
import com.yolt.providers.web.service.domain.ServiceInfo;
import nl.ing.lovebird.providerdomain.ServiceType;
import nl.ing.lovebird.providerdomain.TokenScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.stream.Collectors;

import static com.yolt.providers.dummy.DummyTestProvider.*;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ProviderInfoServiceTest {

    private static final String FAKE_TEST_PROVIDER_NAME = "FAKE_TEST_PROVIDER";
    private static final String TEST_IMPL_OPENBANKING_MOCK = "TEST_IMPL_OPENBANKING_MOCK";
    private static final String TEST_IMPL_OPENBANKING_AUTOONBOARDING_MOCK = "TEST_IMPL_OPENBANKING_AUTOONBOARDING_MOCK";
    private static final String ING_NL_MOCK = "ING_NL_MOCK";

    @Mock
    private ProviderFactoryService providerFactoryService;

    @InjectMocks
    private ProviderInfoService service;

    @Test
    public void shouldReturnProvidersInfoMapForGetProviderInfoWithCorrectData() {
        // given
        DummyTestProvider dummyTestProvider = new DummyTestProvider();
        List<Provider> providers = Arrays.asList(dummyTestProvider, new FakeTestDataProvider(), new MockTestProvider());
        when(providerFactoryService.getAllStableProviders()).thenReturn(providers);
        List<AuthenticationMeanTypeKeyDTO> actualAuthenticationMeans = getDummyTestProviderAuthenticationMeans();
        EnumMap<ServiceType, ServiceInfo> serviceInfos = new EnumMap<>(ServiceType.class);
        serviceInfos.put(ServiceType.AIS, new ServiceInfo(dummyTestProvider.getProviderMetadata(), dummyTestProvider.getSigningKeyRequirements().get(), dummyTestProvider.getTransportKeyRequirements().get(), actualAuthenticationMeans));
        ProviderInfo expectedProvider = new ProviderInfo("Test Impl OpenBanking", serviceInfos);

        // when
        Map<String, ProviderInfo> providersInfo = service.getProvidersInfo();

        // then
        assertThat(providersInfo).hasSize(3);
        assertThat(providersInfo).containsKeys(TEST_IMPL_OPENBANKING_MOCK, FAKE_TEST_PROVIDER_NAME, ING_NL_MOCK);
        assertThat(providersInfo)
                .containsEntry(FAKE_TEST_PROVIDER_NAME, new ProviderInfo("Fake Test Provider", Map.of(ServiceType.AIS, new ServiceInfo(ProviderMetaData.builder().build(), null, null, null))))
                .containsEntry(ING_NL_MOCK, new ProviderInfo("ING NL", Map.of(ServiceType.AIS, new ServiceInfo(ProviderMetaData.builder().build(), null, null, null))));

        ProviderInfo actualProvider = providersInfo.get(TEST_IMPL_OPENBANKING_MOCK);
        assertThat(actualProvider.getServices().keySet()).isEqualTo(expectedProvider.getServices().keySet());

        ServiceInfo expectedServiceInfo = expectedProvider.getServices().get(ServiceType.AIS);
        ServiceInfo actualAISServiceInfo = actualProvider.getServices().get(ServiceType.AIS);
        assertThat(actualAISServiceInfo.getAuthenticationMeans()).containsExactlyInAnyOrderElementsOf(actualAuthenticationMeans);
        assertThat(actualAISServiceInfo.getSigning()).isEqualTo(expectedServiceInfo.getSigning());

        KeyRequirements expectedTransportKeyRequirements = expectedServiceInfo.getTransport();
        KeyRequirements actualTransportKeyRequirements = actualAISServiceInfo.getTransport();
        assertThat(actualTransportKeyRequirements.getPublicKeyAuthenticationMeanReference()).isEqualTo(expectedTransportKeyRequirements.getPublicKeyAuthenticationMeanReference());
        assertThat(actualTransportKeyRequirements.getPrivateKidAuthenticationMeanReference()).isEqualTo(expectedTransportKeyRequirements.getPrivateKidAuthenticationMeanReference());
        assertThat(actualTransportKeyRequirements.getKeyRequirements()).isEqualTo(expectedTransportKeyRequirements.getKeyRequirements());
    }

    @Test
    public void shouldReturnProviderInfoWithoutAutoConfiguredMeansForGetProviderInfoWhenAutoConfigureMeansIsNotCalledYet() {
        // given
        DummyTestAutoOnboardingProvider dummyTestProvider = new DummyTestAutoOnboardingProvider();
        List<Provider> providers = Collections.singletonList(dummyTestProvider);
        when(providerFactoryService.getAllStableProviders()).thenReturn(providers);

        // when
        Map<String, ProviderInfo> providersInfo = service.getProvidersInfo();

        // then
        assertThat(providersInfo).hasSize(1);
        assertThat(providersInfo).containsKey(TEST_IMPL_OPENBANKING_AUTOONBOARDING_MOCK);

        ProviderInfo actualProvider = providersInfo.get(TEST_IMPL_OPENBANKING_AUTOONBOARDING_MOCK);
        String firstAutoConfiguredAuthenticationMean = dummyTestProvider.getAutoConfiguredMeans().keySet().iterator().next();
        List<String> authMeans = actualProvider.getServices().get(ServiceType.AIS).getAuthenticationMeans()
                .stream()
                .map(AuthenticationMeanTypeKeyDTO::getKey)
                .collect(Collectors.toList());

        assertThat(authMeans).doesNotContain(firstAutoConfiguredAuthenticationMean);

        // and after calling autoconfigure, it is present
        UrlAutoOnboardingRequest urlAutoOnboardingRequest = new UrlAutoOnboardingRequest(
                Collections.emptyMap(), mock(RestTemplateManager.class), mock(Signer.class), "redirectUrl",
                Collections.singletonList("redirectUrl"), Collections.singleton(TokenScope.ACCOUNTS));
        List<String> autoconfiguredMeans = new ArrayList<>(dummyTestProvider.autoConfigureMeans(urlAutoOnboardingRequest)
                .keySet());
        assertThat(autoconfiguredMeans).contains(firstAutoConfiguredAuthenticationMean);
    }

    private List<AuthenticationMeanTypeKeyDTO> getDummyTestProviderAuthenticationMeans() {
        return asList(
                AuthenticationMeanTypeKeyDTO.builder()
                        .key(AUDIENCE_NAME)
                        .placeholder(TypedAuthenticationMeans.AUDIENCE_STRING.getType().getDisplayName())
                        .displayName("Audience")
                        .type(TypedAuthenticationMeans.AUDIENCE_STRING.getRendering().getValue())
                        .regex(TypedAuthenticationMeans.AUDIENCE_STRING.getType().getRegex())
                        .build(),
                AuthenticationMeanTypeKeyDTO.builder()
                        .key(CLIENT_ID_NAME)
                        .placeholder(TypedAuthenticationMeans.CLIENT_ID_UUID.getType().getDisplayName())
                        .displayName("Client ID")
                        .type(TypedAuthenticationMeans.CLIENT_ID_UUID.getRendering().getValue())
                        .regex(TypedAuthenticationMeans.CLIENT_ID_UUID.getType().getRegex())
                        .build(),
                AuthenticationMeanTypeKeyDTO.builder()
                        .key(INSTITUTION_ID_NAME)
                        .placeholder(TypedAuthenticationMeans.INSTITUTION_ID_STRING.getType().getDisplayName())
                        .displayName("Institution Id")
                        .type(TypedAuthenticationMeans.INSTITUTION_ID_STRING.getRendering().getValue())
                        .regex(TypedAuthenticationMeans.INSTITUTION_ID_STRING.getType().getRegex())
                        .build(),
                AuthenticationMeanTypeKeyDTO.builder()
                        .key(PRIVATE_KEY_NAME)
                        .placeholder(TypedAuthenticationMeans.PRIVATE_KEY_PEM.getType().getDisplayName())
                        .displayName("Signing Private Key")
                        .type(TypedAuthenticationMeans.PRIVATE_KEY_PEM.getRendering().getValue())
                        .regex(TypedAuthenticationMeans.PRIVATE_KEY_PEM.getType().getRegex())
                        .build(),
                AuthenticationMeanTypeKeyDTO.builder()
                        .key(PUBLIC_KEY_NAME)
                        .placeholder(TypedAuthenticationMeans.PUBLIC_KEY_PEM.getType().getDisplayName())
                        .displayName("Signing Public Key")
                        .type(TypedAuthenticationMeans.PUBLIC_KEY_PEM.getRendering().getValue())
                        .regex(TypedAuthenticationMeans.PUBLIC_KEY_PEM.getType().getRegex())
                        .build(),
                AuthenticationMeanTypeKeyDTO.builder()
                        .key(PRIVATE_KEY_ID_NAME)
                        .placeholder(TypedAuthenticationMeans.KEY_ID.getType().getDisplayName())
                        .displayName("Key Id")
                        .type(TypedAuthenticationMeans.KEY_ID.getRendering().getValue())
                        .regex(TypedAuthenticationMeans.KEY_ID.getType().getRegex())
                        .build(),
                AuthenticationMeanTypeKeyDTO.builder()
                        .key(CERTIFICATE_ID_NAME)
                        .placeholder(TypedAuthenticationMeans.CERTIFICATE_ID.getType().getDisplayName())
                        .displayName("Certificate Id")
                        .type(TypedAuthenticationMeans.CERTIFICATE_ID.getRendering().getValue())
                        .regex(TypedAuthenticationMeans.CERTIFICATE_ID.getType().getRegex())
                        .build(),
                AuthenticationMeanTypeKeyDTO.builder()
                        .key(TRANSPORT_PRIVATE_KEY_ID_NAME)
                        .placeholder(TypedAuthenticationMeans.KEY_ID.getType().getDisplayName())
                        .displayName("Key Id")
                        .type(TypedAuthenticationMeans.KEY_ID.getRendering().getValue())
                        .regex(TypedAuthenticationMeans.KEY_ID.getType().getRegex())
                        .build(),
                AuthenticationMeanTypeKeyDTO.builder()
                        .key(TRANSPORT_CLIENT_CERTIFICATE_NAME)
                        .placeholder(TypedAuthenticationMeans.CLIENT_TRANSPORT_CERTIFICATE_PEM.getType().getDisplayName())
                        .displayName("Client Transport Certificate")
                        .type(TypedAuthenticationMeans.CLIENT_TRANSPORT_CERTIFICATE_PEM.getRendering().getValue())
                        .regex(TypedAuthenticationMeans.CLIENT_TRANSPORT_CERTIFICATE_PEM.getType().getRegex())
                        .build());
    }
}