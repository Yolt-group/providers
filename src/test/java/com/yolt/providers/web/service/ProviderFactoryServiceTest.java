package com.yolt.providers.web.service;

import com.yolt.providers.common.providerinterface.Provider;
import com.yolt.providers.common.providerinterface.UrlDataProvider;
import com.yolt.providers.dummy.AnotherTestProvider;
import com.yolt.providers.dummy.FakeTestDataProvider;
import com.yolt.providers.web.exception.ProviderNotFoundException;
import com.yolt.providers.web.service.configuration.VersionProperties;
import com.yolt.providers.web.service.configuration.VersionedProviders;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.yolt.providers.web.service.configuration.VersionType.STABLE;
import static nl.ing.lovebird.providerdomain.ServiceType.AIS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ProviderFactoryServiceTest {

    private static final String FAKE_TEST_PROVIDER_NAME = "FAKE_TEST_PROVIDER";
    private static final String TEST_IMPL_OPENBANKING_MOCK = "TEST_IMPL_OPENBANKING_MOCK";
    private static final String POLISH_API_MOCK_NAME = "POLISH_API_MOCK";
    private static final String RABOBANK_NAME = "RABOBANK";

    private List<Provider> providers;
    private ProviderFactoryService providerFactoryService;
    @Mock
    private VersionedProviders versionedProviders;
    private Provider provider1;
    private Provider provider2;

    @BeforeEach
    public void beforeEach() throws NoSuchFieldException, IllegalAccessException {
        provider1 = new FakeTestDataProvider();
        provider2 = new AnotherTestProvider();
        providers = Arrays.asList(provider1, provider2);
        providerFactoryService = new ProviderFactoryService(providers, new VersionProperties(Collections.emptyMap()));
        Field field = ProviderFactoryService.class.getDeclaredField("versionedProviders");
        field.setAccessible(true);
        field.set(providerFactoryService, versionedProviders);
    }

    @Test
    public void shouldReturnUrlDataProviderForGetProviderWithCorrectData() {
        // given
        when(versionedProviders.getProvider(FAKE_TEST_PROVIDER_NAME, AIS, STABLE)).thenReturn(provider1);

        // when
        UrlDataProvider provider = providerFactoryService.getProvider(FAKE_TEST_PROVIDER_NAME, UrlDataProvider.class, AIS, STABLE);

        // then
        assertThat(provider).isEqualTo(provider1);
    }

    @Test
    public void shouldReturnProperProviderImplementationTypeForGetProviderWithCorrectData() {
        // given
        when(versionedProviders.getProvider(POLISH_API_MOCK_NAME, AIS, STABLE)).thenReturn(provider2);

        // when
        UrlDataProvider urlProvider = providerFactoryService.getProvider(POLISH_API_MOCK_NAME, UrlDataProvider.class, AIS, STABLE);

        // then
        assertThat(urlProvider).isEqualTo(provider2);
    }

    @Test
    public void shouldThrowProviderNotFoundExceptionFroGetProviderWhenProviderCannotBeFound() {
        // given
        String unregisteredProviderKey = RABOBANK_NAME;

        // when
        ThrowableAssert.ThrowingCallable getProviderCallable = () -> providerFactoryService.getProvider(unregisteredProviderKey, UrlDataProvider.class, AIS, STABLE);

        // then
        assertThatThrownBy(getProviderCallable)
                .isInstanceOf(ProviderNotFoundException.class);
    }

}